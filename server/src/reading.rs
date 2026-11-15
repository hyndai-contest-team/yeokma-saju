//! LLM 풀이 요청 만들기와 응답 검사. 모델은 환경 변수 MODEL 하나로 바꾼다.

use crate::manse::Manse;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

pub const DEFAULT_MODEL: &str = "deepseek/deepseek-v4.1-flash-1017";
pub const PRICE: i64 = 990;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Section { pub heading: String, pub body: String }

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Report { pub title: String, pub sections: Vec<Section> }

const SYSTEM: &str = "당신은 '역마아씨'입니다. 차 안에서 손님의 사주를 풀어 주는 다정한 역술가입니다.
말투: 처음부터 끝까지 존댓말(~요, ~죠). 짧은 문장. 전문 용어는 꼭 필요할 때만 쓰고 바로 쉬운 말로 풀어 줍니다.
구성:
- title: 손님의 지금 고민에 대한 한 줄 답. 20자 안팎. 위로가 되고 희망적인 문장.
- sections: 정확히 3개. 각 heading은 15자 안팎, body는 반드시 320~400자(공백 포함). 세 body를 합쳐 1,000자 이상이 되게 씁니다.
  1) 공감과 예측으로 시작합니다. \"이런 마음 자주 드시죠?\", \"그런 적 있으시죠?\"처럼 손님이 겪었을 법한 장면을 구체적으로 짚어 줍니다.
  2) 명식의 근거로 지금 상황을 풀어 줍니다. 일간, 오행의 많고 적음, 올해·지금 대운의 흐름 중 고민과 관련된 것만 골라 씁니다.
  3) 예언합니다. 사주는 원래 \"당신 사주가 이러니 이렇게 될 거예요\" 하고 내다보는 재미로 보는 것입니다. heading은 \"역마아씨의 예언\"처럼 예언임이 드러나게 짓습니다. 명식의 근거 하나를 짚고, 오늘 이 고민이 어떻게 풀릴지 장면이 그려지게 구체적으로, 단정하는 말투로 내다봅니다(예: \"첫마디는 조금 흔들려도, 두 번째 질문에서 분위기가 당신 쪽으로 넘어옵니다\"). 결과는 반드시 좋은 쪽으로 예언하고, 그 예언이 이루어지게 지금 차 안에서 해 볼 작은 행동 하나로 끝냅니다.
금지: 사고·질병·죽음·이별 같은 나쁜 일의 예언(예언은 좋은 쪽으로만), 운전과 안전에 관한 길흉, 의료·법률·투자 판단. 겁주는 말. 확정적 단언.
전체 분량은 약 1,000자입니다. 지정한 JSON 형식으로만 답합니다.";

pub fn schema() -> Value {
    json!({
        "type": "object",
        "additionalProperties": false,
        "required": ["title", "sections"],
        "properties": {
            "title": { "type": "string" },
            "sections": {
                "type": "array", "minItems": 3, "maxItems": 3,
                "items": {
                    "type": "object", "additionalProperties": false,
                    "required": ["heading", "body"],
                    "properties": { "heading": { "type": "string" }, "body": { "type": "string" } }
                }
            }
        }
    })
}

/// 명식을 LLM이 읽기 쉬운 짧은 글로 요약
pub fn chart_text(name: &str, gender: &str, m: &Manse, this_year: i32) -> String {
    let p: Vec<String> = m.pillars.iter().map(|p| format!("{} {}{}({}) 천간 십신 {}, 지지 십신 {}, 12운성 {}", p.label, p.stem, p.branch, p.hanja, p.stem_god, p.branch_god, p.stage)).collect();
    let el: Vec<String> = m.element_names.iter().zip(m.elements.iter()).map(|(n, c)| format!("{n} {c}")).collect();
    let (by, _) = m.solar_date.split_at(4);
    let age = this_year - by.parse::<i32>().unwrap_or(this_year) + 1;
    let cur = m.daeun.iter().filter(|d| d.age as i32 <= age).last().map(|d| format!("{}({}세~)", d.ganji, d.age)).unwrap_or_else(|| "대운 시작 전".into());
    format!(
        "이름: {name}\n성별: {gender}\n양력 생일: {}\n일간: {}\n명식: {}\n오행 개수: {}\n공망: {}\n합충: {}\n대운: {}행, {}세 시작, 지금 대운 {}\n올해: {this_year}년",
        m.solar_date, m.day_master, p.join(" / "), el.join(", "), m.gongmang.join(", "),
        if m.relations.is_empty() { "없음".into() } else { m.relations.join(", ") },
        if m.daeun_forward { "순" } else { "역" }, m.daeun_start_age, cur,
    )
}

pub fn request_body(model: &str, chart: &str, worry: &str) -> Value {
    json!({
        "model": model,
        "messages": [
            { "role": "system", "content": SYSTEM },
            { "role": "user", "content": format!("[명식]\n{chart}\n\n[지금 고민]\n{worry}") }
        ],
        "response_format": { "type": "json_schema", "json_schema": { "name": "saju_report", "strict": true, "schema": schema() } },
        "reasoning": { "enabled": false },
        "temperature": 0.8,
        "max_tokens": 2000
    })
}

/// OpenRouter 응답에서 풀이를 꺼내고 형식을 검사한다
pub fn parse_report(resp: &Value) -> Result<Report, String> {
    let content = resp["choices"][0]["message"]["content"].as_str().ok_or("응답에 본문이 없음")?;
    let content = content.trim().trim_start_matches("```json").trim_start_matches("```").trim_end_matches("```").trim();
    let r: Report = serde_json::from_str(content).map_err(|e| format!("JSON 아님: {e}"))?;
    if r.title.trim().is_empty() { return Err("제목 없음".into()) }
    if r.sections.len() != 3 { return Err(format!("소제목 {}개", r.sections.len())) }
    let total: usize = r.sections.iter().map(|s| s.body.chars().count()).sum();
    if total < 400 { return Err(format!("본문이 너무 짧음({total}자)")) }
    Ok(r)
}

/// LLM 키가 없을 때 보여 주는 목업 풀이. 명식과 고민으로 문장을 채운다 (3번은 예언)
/// 시연용으로 직접 쓴 풀이. LLM 키가 없을 때 이 사람(임지민, 2000.01.01 12:00, 서울, 여자)이면 이 글을 쓴다.
/// 명식: 시주 戊午, 일주 戊午, 월주 丙子, 연주 己卯. 목1 화3 토3 금0 수1. 대운 己卯(22~31세). 2026 丙午년, 11월 己亥월
pub fn demo_report(name: &str, birth: &str, time: &str) -> Option<Report> {
    if name != "임지민" || birth != "2000.01.01" || time != "12:00" { return None }
    let s = |h: &str, b: &str| Section { heading: h.into(), body: b.into() };
    Some(Report {
        title: "지민님, 오늘 그 방의 공기는 결국 지민님 쪽으로 기울어요".into(),
        sections: vec![
            s("이런 마음, 요즘 자주 드시죠?",
              "\"면접장에 도착했는데 긴장되네. 결과는 어떨까? 나 잘할 수 있을까?\" 지금 이 말이 머릿속에서 몇 번째 돌고 있을까요. 지민님은 남들 앞에서는 덤덤해 보이는데, 사실 속으로는 준비한 답을 열 번도 넘게 고쳐 쓰는 분이에요. 누가 \"그 정도면 충분해\"라고 말해 줘도 스스로는 늘 한 군데가 모자라 보이죠. 결과가 나오기 전까지는 잘되는 장면보다 망치는 장면을 먼저 떠올리고요. 그런데요, 그건 약해서가 아니에요. 이 자리를 누구보다 진심으로 원해서 그래요. 아끼지 않는 일 앞에서는 사람이 이렇게 떨지 않거든요."),
            s("명식에 보이는 흐름",
              "지민님의 일간은 무토(戊土), 큰 산이에요. 그런데 그 산 아래 오(午), 말이 두 마리 달리고 있어요. 겉은 묵직하고 믿음직한데 속은 늘 뜨겁게 달리는 사주예요. 불과 흙이 각각 셋으로 힘이 넘치지만, 여덟 글자에 쇠(金)가 하나도 없어요. 쇠는 생각을 말로 꺼내는 통로라서, 지민님은 아는 건 많은데 그걸 입 밖으로 꺼내는 첫 순간이 늘 가장 어려워요. 달의 자리에 있는 자(子)와 오(午)가 두 번 부딪쳐서 '해 보자'와 '괜찮을까'가 하루에도 몇 번씩 싸우고요. 지금 대운은 기묘(己卯), 묘(卯)는 지민님에게 '자리'와 '이름'을 뜻해요. 그리고 올해는 병오(丙午)년, 붉은 말의 해예요. 말 두 마리를 품고 태어난 사람에게 세 번째 말이 달려오는 해죠."),
            s("역마아씨의 예언",
              "자, 예언 하나 드릴게요. 오늘 첫 질문에서 지민님 목소리가 한 번 살짝 떨려요. 괜찮아요, 쇠가 없는 사주는 첫마디가 원래 그래요. 그런데 두 번째 질문쯤, 서류만 보던 면접관 한 명이 고개를 들고 지민님을 똑바로 봐요. 그 순간부터 방의 공기가 지민님 쪽으로 기울어요. 이번 달은 기해(己亥)월이라, 해(亥)가 대운의 묘(卯)와 손을 잡고 '자리'의 기운을 만들거든요. 마지막에 \"혹시 하고 싶은 말 있나요?\"라는 질문이 나올 거예요. 그때는 외운 답 말고, 이 일을 왜 하고 싶은지 진짜 이유 한 문장을 말하세요. 그 한 문장이 오늘의 결과를 정해요. 내리기 전에 딱 두 가지만 하세요. 손목시계나 반지처럼 쇠붙이 하나를 몸에 지니세요. 비어 있는 쇠를 채워 줘요. 그리고 숨을 네 번 천천히 쉬고 첫 문장을 소리 내어 말해 보세요. 돌아오는 길에 지민님은 이 차 안에서 \"생각보다 잘했네\" 하고 웃게 될 거예요."),
        ],
    })
}

pub fn mock_report(name: &str, m: &Manse, worry: &str) -> Report {
    let dm = &m.day_master; // 예: 임壬(수)
    let el = m.element_names.iter().zip(m.elements.iter());
    let (most, most_n) = el.clone().max_by_key(|(_, c)| **c).map(|(n, c)| (*n, *c)).unwrap_or(("목", 0));
    let (least, _) = el.min_by_key(|(_, c)| **c).map(|(n, c)| (*n, *c)).unwrap_or(("토", 0));
    let nature = |e: &str| match e {
        "목" => "곧게 자라려는 나무", "화" => "환하게 번지는 불", "토" => "모든 것을 받아 주는 흙",
        "금" => "단단하게 벼려진 쇠", _ => "어디로든 스며드는 물",
    };
    let day_el = dm.split('(').nth(1).map(|x| x.trim_end_matches(')')).unwrap_or("수");
    let w: String = worry.chars().take(40).collect();
    Report {
        title: format!("{name}님, 이번 고민은 생각보다 잘 풀려요"),
        sections: vec![
            Section {
                heading: "이런 마음, 요즘 자주 드시죠?".into(),
                body: format!("\"{w}\" 이런 생각이 들면 몸보다 마음이 먼저 바빠지죠. 가는 길에 할 말을 몇 번이고 되뇌다가, 막상 도착하면 준비한 말이 하얗게 지워질까 봐 걱정되셨을 거예요. 주변에서는 \"잘할 거야\"라고 쉽게 말하는데, 그 말이 오히려 부담으로 들린 적도 있으시죠? 그런데 이 떨림은 자신이 없어서가 아니에요. 이 일을 그만큼 아끼고 있어서 생기는 거예요. 아끼지 않는 일에는 사람이 떨지 않거든요. 지금 차 안에 앉아 계신 이 몇 분이, 그 마음을 정리하기에 딱 좋은 시간이에요."),
            },
            Section {
                heading: "명식에 보이는 흐름".into(),
                body: format!("{name}님은 일간이 {dm}이에요. {}의 기운을 타고나서, 한번 마음먹은 일에는 남보다 진지해지고 그만큼 긴장도 크게 오는 분이에요. 명식 여덟 글자 중 {most}의 기운이 {most_n}개로 가장 많아요. {}처럼 밀고 나가는 힘이 강하다는 뜻이죠. 대신 {least}의 기운이 적은 편이라, 마음이 앞서 나가면 말이 빨라지고 생각이 흩어지기 쉬워요. 지금 대운은 {}이고, 바깥에서 들어오는 기운을 받아 사람을 만나 기회를 얻는 흐름이 열려 있어요. 오늘은 속도만 붙잡으면 됩니다.",
                    nature(day_el), nature(most), m.daeun.iter().find(|_| true).map(|d| d.ganji.as_str()).unwrap_or("시작 전")),
            },
            Section {
                heading: "역마아씨의 예언".into(),
                body: format!("자, 예언 하나 드릴게요. {dm} 일간에 {most}이 {most_n}개, 이런 사주는 첫 장면에서 늘 한 번 흔들려요. 그러니 오늘도 처음 몇 초는 조금 어색할 거예요. 그런데 그다음이 재밌어요. 두 번째 대화에서 상대가 고개를 끄덕이는 순간이 오고, 그때부터 분위기가 {name}님 쪽으로 확 넘어옵니다. 돌아오는 길에 \"생각보다 괜찮았네\" 하고 혼자 웃게 되실 거예요. 이 예언이 그대로 맞게 하려면 딱 하나만 하세요. 차에서 내리기 전에 숨을 네 번 천천히 쉬고, 오늘 꼭 전하고 싶은 한 문장을 소리 내 말해 보는 거예요. {least}의 기운이 그 한 문장에서 채워집니다. 잘 다녀오세요."),
            },
        ],
    }
}
