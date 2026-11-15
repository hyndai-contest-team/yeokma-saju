//! 만세력: 출생 정보 → 사주 명식, 대운, 십신, 지장간, 12운성, 공망, 합충

pub mod astro;
pub mod ganji;
pub mod kst;
pub mod lunar;
pub mod places;

use astro::{from_jd, jd, norm360, sun_longitude, sun_longitude_time, equation_of_time};
use ganji::*;
use serde::{Deserialize, Serialize};

#[derive(Deserialize, Clone, Debug)]
pub struct BirthInput {
    pub name: Option<String>,
    /// "여자" | "남자" (예전 값 "여성" | "남성"도 같음. '남'이 들어 있으면 남자)
    pub gender: String,
    /// "yyyy.MM.dd"
    pub birth: String,
    #[serde(default)]
    pub lunar: bool,
    #[serde(default)]
    pub leap: bool,
    /// "HH:mm" | "모름"
    pub time: String,
    pub place: String,
    /// 23시 이후: "next_day"(그날 자시를 다음 날로, 기본) | "same_day"(야자시)
    #[serde(default)]
    pub zasi: Option<String>,
}

#[derive(Serialize, Debug, Clone)]
pub struct Pillar {
    pub label: &'static str,
    pub stem: String,
    pub branch: String,
    pub hanja: String,
    pub stem_element: &'static str,
    pub branch_element: &'static str,
    pub stem_god: &'static str,
    pub branch_god: &'static str,
    pub hidden: Vec<String>,
    pub stage: &'static str,
}

#[derive(Serialize, Debug, Clone)]
pub struct DaeunItem { pub age: u32, pub ganji: String, pub hanja: String }

#[derive(Serialize, Debug, Clone)]
pub struct Manse {
    pub solar_date: String,
    pub clock_time: Option<String>,
    pub utc_offset: String,
    pub dst: bool,
    pub place_used: String,
    pub longitude: f64,
    pub true_solar_time: Option<String>,
    pub correction_minutes: Option<i32>,
    pub pillars: Vec<Pillar>,
    pub day_master: String,
    pub elements: [u32; 5],
    pub element_names: [&'static str; 5],
    pub gongmang: Vec<String>,
    pub relations: Vec<String>,
    pub daeun_forward: bool,
    pub daeun_start_age: u32,
    pub daeun: Vec<DaeunItem>,
    pub notes: Vec<String>,
}

fn parse_date(s: &str) -> Option<(i32, u32, u32)> {
    let v: Vec<&str> = s.split(|c: char| !c.is_ascii_digit()).filter(|x| !x.is_empty()).collect();
    if v.len() != 3 { return None }
    Some((v[0].parse().ok()?, v[1].parse().ok()?, v[2].parse().ok()?))
}

fn parse_time(s: &str) -> Option<(u32, u32)> {
    let v: Vec<&str> = s.split(':').collect();
    if v.len() != 2 { return None }
    let (h, m) = (v[0].trim().parse().ok()?, v[1].trim().parse().ok()?);
    if h < 24 && m < 60 { Some((h, m)) } else { None }
}

fn fmt_dt(jd_local: f64) -> (String, String) {
    let (y, m, d) = from_jd(jd_local);
    let day = d.floor();
    let mins = ((d - day) * 1440.0).round() as i32;
    let (mut h, mut mi) = (mins / 60, mins % 60);
    if h == 24 { h = 23; mi = 59 }
    (format!("{:04}.{:02}.{:02}", y, m, day as u32), format!("{:02}:{:02}", h, mi))
}

fn pillar(label: &'static str, idx: usize, day_stem: usize, is_day: bool) -> Pillar {
    let (s, b) = split(idx);
    Pillar {
        label,
        stem: STEM_K[s].into(), branch: BRANCH_K[b].into(),
        hanja: format!("{}{}", STEM_H[s], BRANCH_H[b]),
        stem_element: ELEMENT[stem_element(s)], branch_element: ELEMENT[branch_element(b)],
        stem_god: if is_day { "일간" } else { ten_god(day_stem, s) },
        branch_god: ten_god(day_stem, branch_main_stem(b)),
        hidden: hidden(b).iter().map(|&h| format!("{}{}", STEM_K[h], STEM_H[h])).collect(),
        stage: twelve_stage(day_stem, b),
    }
}

pub fn compute(inp: &BirthInput) -> Result<Manse, String> {
    let mut notes = Vec::new();
    let (by, bm, bd) = parse_date(&inp.birth).ok_or("생년월일 형식이 맞지 않아요 (yyyy.MM.dd)")?;
    if !(1900..=2100).contains(&by) { return Err("1900~2100년만 계산해요".into()) }

    // 1) 양력 날짜
    let (y, m, d) = if inp.lunar {
        let day = lunar::to_solar_day(by, bm, bd, inp.leap).ok_or("없는 음력 날짜예요")?;
        let (y, m, d) = from_jd(day as f64);
        notes.push(format!("음력 {}{}.{:02}.{:02} → 양력 {:04}.{:02}.{:02}", by, if inp.leap { " 윤" } else { "" }, bm, bd, y, m, d.floor() as u32));
        (y, m, d.floor() as u32)
    } else { (by, bm, bd) };

    // 2) 시각: 모르면 정오로 두고 시주는 비운다
    let time = parse_time(&inp.time);
    if time.is_none() { notes.push("태어난 시간을 몰라 시주는 비우고 정오 기준으로 계산했어요".into()) }
    let (h, mi) = time.unwrap_or((12, 0));
    let ymd = y as u32 * 10000 + m * 100 + d;
    let off = kst::offset(ymd);
    if off.dst { notes.push("서머타임 기간이라 1시간을 뺐어요".into()) }
    if off.minutes % 60 == 30 || off.minutes == 570 { notes.push(format!("당시 표준시는 {}였어요", off.label)) }
    let ut = jd(y, m, d as f64 + (h as f64 * 60.0 + mi as f64 - off.minutes as f64) / 1440.0);

    // 3) 경도, 진태양시
    let (lon, place_used) = match places::longitude(&inp.place) {
        Some((lon, n)) => (lon, n.to_string()),
        None => { notes.push(format!("'{}'의 경도를 몰라 서울 경도로 계산했어요", inp.place)); (places::SEOUL, "서울(기본)".into()) }
    };
    let eot = equation_of_time(ut);
    let app = ut + (lon * 4.0 + eot) / 1440.0; // 지방 진태양시(JD 형태)
    let correction = ((lon * 4.0 + eot) - off.minutes as f64).round() as i32;

    // 4) 연주·월주: 태양 황경 (입춘 315°, 절마다 30°)
    let lam = sun_longitude(ut);
    let ipchun = sun_longitude_time(315.0, jd(y, 2, 4.5));
    let sy = if ut >= ipchun { y } else { y - 1 };
    let year_idx = ((sy - 4).rem_euclid(60)) as usize;
    let m0 = (norm360(lam - 315.0) / 30.0).floor() as usize; // 0 = 인월
    let ys = year_idx % 10;
    let month_idx = join(((ys % 5) * 2 + 2 + m0) % 10, (2 + m0) % 12);

    // 절입 가까이면 알림 (계산 오차 약 ±15분)
    let next_jeol = norm360(lam - 315.0) % 30.0;
    let to_jeol_days = (30.0 - next_jeol).min(next_jeol) / 0.9856;
    if to_jeol_days < 1.0 / 24.0 { notes.push("절입 시각과 1시간 안쪽이라 월주가 바뀔 수 있어요".into()) }

    // 5) 일주·시주: 지방 진태양시 기준. 23시 이후 처리는 zasi 옵션
    let (ad, at) = fmt_dt(app);
    let app_day = (app + 0.5).floor() as i64; // JDN
    let app_hour = ((app + 0.5).fract() * 24.0) as u32;
    let late = time.is_some() && app_hour >= 23;
    let next_day_rule = inp.zasi.as_deref() != Some("same_day");
    let day_jdn = if late && next_day_rule { app_day + 1 } else { app_day };
    let day_idx = ((day_jdn + 49).rem_euclid(60)) as usize;
    let ds = day_idx % 10;
    let mut pillars = vec![pillar("연주", year_idx, ds, false), pillar("월주", month_idx, ds, false), pillar("일주", day_idx, ds, true)];
    if time.is_some() {
        let hb = (((app_hour + 1) / 2) % 12) as usize;
        let stem_base = if late && !next_day_rule { (ds + 1) % 10 } else { ds };
        let hs = ((stem_base % 5) * 2 + hb) % 10;
        pillars.push(pillar("시주", join(hs, hb), ds, false));
        if late { notes.push(if next_day_rule { "23시 이후라 다음 날의 자시로 봤어요".into() } else { "23시 이후라 야자시(그날 일주)로 봤어요".into() }) }
    }
    // 시주, 일주, 월주, 연주 순(오른쪽에서 왼쪽 읽기 관례)으로 돌려준다
    pillars.reverse();

    // 6) 오행 개수
    let mut el = [0u32; 5];
    for p in &pillars {
        let s = STEM_K.iter().position(|x| *x == p.stem).unwrap();
        let b = BRANCH_K.iter().position(|x| *x == p.branch).unwrap();
        el[stem_element(s)] += 1;
        el[branch_element(b)] += 1;
    }

    // 7) 공망, 합충
    let gm = gongmang(day_idx);
    let mut rel = Vec::new();
    let idxs: Vec<(usize, usize, &str)> = pillars.iter().map(|p| {
        (STEM_K.iter().position(|x| *x == p.stem).unwrap(), BRANCH_K.iter().position(|x| *x == p.branch).unwrap(), p.label)
    }).collect();
    for i in 0..idxs.len() { for j in i + 1..idxs.len() {
        if let Some(r) = stem_relation(idxs[i].0, idxs[j].0) { rel.push(format!("{}·{} 천간 {}", idxs[i].2, idxs[j].2, r)) }
        if let Some(r) = branch_relation(idxs[i].1, idxs[j].1) { rel.push(format!("{}·{} 지지 {}", idxs[i].2, idxs[j].2, r)) }
    }}

    // 8) 대운: 양남음녀 순행, 음남양녀 역행. 다음(이전) 절까지 일수 / 3
    let male = inp.gender.contains('남');
    let forward = stem_yang(ys) == male;
    let k = (norm360(lam - 315.0) / 30.0).floor();
    let target = if forward { norm360(315.0 + (k + 1.0) * 30.0) } else { norm360(315.0 + k * 30.0) };
    let guess = if forward { ut + 15.0 } else { ut - 15.0 };
    let t = sun_longitude_time(target, guess);
    let days = (t - ut).abs();
    let start_age = (days / 3.0).round().max(1.0) as u32;
    let daeun = (1..=10).map(|i| {
        let idx = if forward { (month_idx + i) % 60 } else { (month_idx + 60 - i) % 60 };
        let (s, b) = split(idx);
        DaeunItem { age: start_age + (i as u32 - 1) * 10, ganji: format!("{}{}", STEM_K[s], BRANCH_K[b]), hanja: format!("{}{}", STEM_H[s], BRANCH_H[b]) }
    }).collect();

    Ok(Manse {
        solar_date: format!("{:04}.{:02}.{:02}", y, m, d),
        clock_time: time.map(|(h, m)| format!("{:02}:{:02}", h, m)),
        utc_offset: off.label.into(), dst: off.dst,
        place_used, longitude: lon,
        true_solar_time: time.map(|_| format!("{} {}", ad, at)),
        correction_minutes: time.map(|_| correction),
        day_master: format!("{}{}({})", STEM_K[ds], STEM_H[ds], ELEMENT[stem_element(ds)]),
        pillars, elements: el, element_names: ELEMENT,
        gongmang: gm.iter().map(|&b| format!("{}{}", BRANCH_K[b], BRANCH_H[b])).collect(),
        relations: rel, daeun_forward: forward, daeun_start_age: start_age, daeun, notes,
    })
}

#[cfg(test)]
mod tests;
