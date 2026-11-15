//! HTTP API (로컬 wrangler dev로 실행)
//!  POST /api/manse    만세력 (무료, LLM 호출 없음)
//!  GET  /api/wallet   ?device=  포인트 잔액
//!  POST /api/charge   {device, pack}  충전 (결제 연동 전 목업)
//!  POST /api/reading  {device, profile, worry}  990P를 먼저 빼고 나서야 LLM을 부른다. 실패하면 돌려준다.

use crate::manse::{compute, BirthInput};
use crate::reading::{self, PRICE};
use serde::Deserialize;
use serde_json::json;
use worker::*;

/// 충전 단위: (가격, 지급 포인트). 9,900원은 1회 더, 99,000원은 15회 더
const PACKS: [(i64, i64); 3] = [(990, 990), (9_900, 9_900 + 990), (99_000, 99_000 + 15 * 990)];

fn cors(mut r: Response) -> Result<Response> {
    let h = r.headers_mut();
    h.set("Access-Control-Allow-Origin", "*")?;
    h.set("Access-Control-Allow-Headers", "content-type")?;
    h.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS")?;
    Ok(r)
}
fn err(msg: &str, status: u16) -> Result<Response> { cors(Response::from_json(&json!({ "error": msg }))?.with_status(status)) }

fn valid_device(d: &str) -> bool { (8..=64).contains(&d.len()) && d.chars().all(|c| c.is_ascii_alphanumeric() || c == '-') }

async fn balance(db: &D1Database, device: &str) -> Result<i64> {
    #[derive(Deserialize)] struct Row { balance: i64 }
    let r: Option<Row> = db.prepare("SELECT balance FROM wallets WHERE device = ?1").bind(&[device.into()])?.first(None).await?;
    Ok(r.map(|r| r.balance).unwrap_or(0))
}

async fn ledger(db: &D1Database, device: &str, delta: i64, reason: &str) -> Result<()> {
    db.prepare("INSERT INTO ledger (device, delta, reason, at) VALUES (?1, ?2, ?3, ?4)")
        .bind(&[device.into(), (delta as f64).into(), reason.into(), Date::now().as_millis().to_string().into()])?.run().await?;
    Ok(())
}

async fn add_points(db: &D1Database, device: &str, delta: i64, reason: &str) -> Result<()> {
    db.prepare("INSERT INTO wallets (device, balance) VALUES (?1, ?2) ON CONFLICT(device) DO UPDATE SET balance = balance + ?2")
        .bind(&[device.into(), (delta as f64).into()])?.run().await?;
    ledger(db, device, delta, reason).await
}

/// 잔액이 충분할 때만 빼고 true. 동시에 두 번 눌러도 한 번만 빠진다
async fn take_points(db: &D1Database, device: &str, amount: i64) -> Result<bool> {
    let r = db.prepare("UPDATE wallets SET balance = balance - ?2 WHERE device = ?1 AND balance >= ?2")
        .bind(&[device.into(), (amount as f64).into()])?.run().await?;
    let changed = r.meta()?.and_then(|m| m.changes).unwrap_or(0) > 0;
    if changed { ledger(db, device, -amount, "reading").await?; }
    Ok(changed)
}

#[derive(Deserialize)]
struct ChargeReq { device: String, pack: usize }

#[derive(Deserialize)]
struct ReadingReq { device: String, profile: BirthInput, worry: String }

async fn call_llm(env: &Env, body: serde_json::Value) -> std::result::Result<reading::Report, String> {
    let key = env.secret("OPENROUTER_API_KEY").map_err(|_| "API 키 없음".to_string())?.to_string();
    let mut init = RequestInit::new();
    init.with_method(Method::Post);
    let headers = Headers::new();
    headers.set("Authorization", &format!("Bearer {key}")).map_err(|e| e.to_string())?;
    headers.set("Content-Type", "application/json").map_err(|e| e.to_string())?;
    headers.set("X-Title", "yeokma-saju").map_err(|e| e.to_string())?;
    init.with_headers(headers);
    init.with_body(Some(body.to_string().into()));
    let req = Request::new_with_init("https://openrouter.ai/api/v1/chat/completions", &init).map_err(|e| e.to_string())?;
    let mut res = Fetch::Request(req).send().await.map_err(|e| e.to_string())?;
    let status = res.status_code();
    let v: serde_json::Value = res.json().await.map_err(|e| e.to_string())?;
    if status >= 400 { return Err(format!("LLM {status}: {}", v["error"]["message"].as_str().unwrap_or(""))) }
    reading::parse_report(&v)
}

#[event(fetch)]
async fn fetch(req: Request, env: Env, _ctx: Context) -> Result<Response> {
    if req.method() == Method::Options { return cors(Response::empty()?) }
    Router::new()
        .get("/", |_, _| cors(Response::ok("yeokma-saju api")?))
        .post_async("/api/manse", |mut req, _| async move {
            let inp: BirthInput = match req.json().await { Ok(v) => v, Err(_) => return err("입력 형식이 맞지 않아요", 400) };
            match compute(&inp) { Ok(m) => cors(Response::from_json(&m)?), Err(e) => err(&e, 422) }
        })
        .get_async("/api/wallet", |req, ctx| async move {
            let url = req.url()?;
            let device = url.query_pairs().find(|(k, _)| k == "device").map(|(_, v)| v.to_string()).unwrap_or_default();
            if !valid_device(&device) { return err("기기 번호가 맞지 않아요", 400) }
            let db = ctx.env.d1("DB")?;
            cors(Response::from_json(&json!({ "balance": balance(&db, &device).await?, "price": PRICE }))?)
        })
        .post_async("/api/charge", |mut req, ctx| async move {
            let c: ChargeReq = match req.json().await { Ok(v) => v, Err(_) => return err("입력 형식이 맞지 않아요", 400) };
            if !valid_device(&c.device) || c.pack >= PACKS.len() { return err("충전 요청이 맞지 않아요", 400) }
            let db = ctx.env.d1("DB")?;
            let (price, points) = PACKS[c.pack];
            // 시연용: 결제 인증 없이 바로 충전한다
            add_points(&db, &c.device, points, &format!("charge:{price}:mock")).await?;
            cors(Response::from_json(&json!({ "balance": balance(&db, &c.device).await?, "charged": points, "mock": true }))?)
        })
        .post_async("/api/reading", |mut req, ctx| async move {
            let r: ReadingReq = match req.json().await { Ok(v) => v, Err(_) => return err("입력 형식이 맞지 않아요", 400) };
            if !valid_device(&r.device) { return err("기기 번호가 맞지 않아요", 400) }
            let worry = r.worry.trim().chars().take(300).collect::<String>();
            if worry.chars().count() < 2 { return err("고민을 알려 주세요", 400) }
            let m = match compute(&r.profile) { Ok(m) => m, Err(e) => return err(&e, 422) };
            let db = ctx.env.d1("DB")?;
            // 결제(포인트 차감)가 먼저다. 모자라면 LLM을 부르지 않는다
            if !take_points(&db, &r.device, PRICE).await? {
                return cors(Response::from_json(&json!({ "error": "포인트가 부족해요", "need": PRICE, "balance": balance(&db, &r.device).await? }))?.with_status(402));
            }
            let model = ctx.env.var("MODEL").map(|v| v.to_string()).unwrap_or_else(|_| reading::DEFAULT_MODEL.into());
            let this_year = js_sys_year();
            let chart = reading::chart_text(r.profile.name.as_deref().unwrap_or("손님"), &r.profile.gender, &m, this_year);
            let body = reading::request_body(&model, &chart, &worry);
            // LLM 키가 등록되지 않았으면 목업 풀이를 보여 준다
            let has_key = ctx.env.secret("OPENROUTER_API_KEY").map(|k| !k.to_string().trim().is_empty()).unwrap_or(false);
            if !has_key {
                let rep = reading::mock_report(r.profile.name.as_deref().unwrap_or("손님"), &m, &worry);
                return cors(Response::from_json(&json!({ "report": rep, "manse": m, "balance": balance(&db, &r.device).await?, "mock": true }))?);
            }
            // 형식 검사를 두 번까지 시도하고, 그래도 안 되면 포인트를 돌려준다
            let mut last = String::new();
            for _ in 0..2 {
                match call_llm(&ctx.env, body.clone()).await {
                    Ok(rep) => return cors(Response::from_json(&json!({ "report": rep, "manse": m, "balance": balance(&db, &r.device).await? }))?),
                    Err(e) => { console_log!("reading failed: {e}"); last = e; }
                }
            }
            add_points(&db, &r.device, PRICE, "refund").await?;
            cors(Response::from_json(&json!({ "error": "풀이를 만들지 못했어요. 포인트는 돌려드렸어요", "detail": last, "refunded": PRICE, "balance": balance(&db, &r.device).await? }))?.with_status(502))
        })
        .run(req, env)
        .await
}

fn js_sys_year() -> i32 {
    let ms = Date::now().as_millis() as f64;
    let jd = ms / 86_400_000.0 + 2440587.5;
    crate::manse::astro::from_jd(jd).0
}
