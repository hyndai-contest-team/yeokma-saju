//! 두 사람의 궁합. 점수는 명식으로 직접 계산하고(일간 오행, 천간합, 일지·띠의 합충, 서로 비어 있는 오행을 채워 주는지),
//! 풀이 글은 LLM이 쓰거나 키가 없으면 목업으로 채운다.

use crate::manse::Manse;
use crate::reading::{Report, Section};
use serde::Serialize;
use serde_json::{json, Value};

#[derive(Serialize, Debug, Clone)]
pub struct Score { pub score: u32, pub kind: &'static str, pub reasons: Vec<String> }

const GEN: [(&str, &str); 5] = [("목", "화"), ("화", "토"), ("토", "금"), ("금", "수"), ("수", "목")];
const CLASH_EL: [(&str, &str); 5] = [("목", "토"), ("토", "수"), ("수", "화"), ("화", "금"), ("금", "목")];
const STEM_HAP: [(&str, &str); 5] = [("갑", "기"), ("을", "경"), ("병", "신"), ("정", "임"), ("무", "계")];
const YUKHAP: [(&str, &str); 6] = [("자", "축"), ("인", "해"), ("묘", "술"), ("진", "유"), ("사", "신"), ("오", "미")];
const CHUNG: [(&str, &str); 6] = [("자", "오"), ("축", "미"), ("인", "신"), ("묘", "유"), ("진", "술"), ("사", "해")];
const SAMHAP: [[&str; 3]; 4] = [["신", "자", "진"], ["해", "묘", "미"], ["인", "오", "술"], ["사", "유", "축"]];

fn pair(t: &[(&str, &str)], a: &str, b: &str) -> bool { t.iter().any(|(x, y)| (*x == a && *y == b) || (*x == b && *y == a)) }
fn half(a: &str, b: &str) -> bool { a != b && SAMHAP.iter().any(|g| g.contains(&a) && g.contains(&b)) }

fn pillar<'a>(m: &'a Manse, label: &str) -> Option<&'a crate::manse::Pillar> { m.pillars.iter().find(|p| p.label == label) }

/// 점수 38~97과 유형(귀인·단짝·동반자·자극제·숙제), 근거 문장
pub fn score(a: &Manse, b: &Manse) -> Score {
    let mut s: i32 = 62;
    let mut why = Vec::new();
    let (da, db) = (pillar(a, "일주").unwrap(), pillar(b, "일주").unwrap());
    let (ea, eb) = (da.stem_element, db.stem_element);
    if ea == eb { s += 6; why.push(format!("두 분 다 {ea}의 일간이라 생각하는 방식이 닮았어요")) }
    else if pair(&GEN, ea, eb) { s += 12; why.push(format!("{ea}과 {eb}, 한쪽이 다른 쪽을 살려 주는 상생 관계예요")) }
    else if pair(&CLASH_EL, ea, eb) { s -= 8; why.push(format!("{ea}과 {eb}, 서로를 다듬는 상극 관계라 부딪히며 자라요")) }
    if pair(&STEM_HAP, &da.stem, &db.stem) { s += 10; why.push(format!("일간 {}와 {}가 천간합을 이뤄 끌림이 강해요", da.stem, db.stem)) }
    let (ba, bb) = (da.branch.as_str(), db.branch.as_str());
    if pair(&YUKHAP, ba, bb) { s += 12; why.push(format!("일지 {ba}와 {bb}가 육합이라 같이 있으면 마음이 편해요")) }
    else if half(ba, bb) { s += 7; why.push(format!("일지 {ba}와 {bb}가 삼합으로 이어져 한 방향을 봐요")) }
    else if pair(&CHUNG, ba, bb) { s -= 10; why.push(format!("일지 {ba}와 {bb}가 충이라 가까울수록 자주 부딪혀요")) }
    else if ba == bb { s += 3 }
    if let (Some(ya), Some(yb)) = (pillar(a, "연주"), pillar(b, "연주")) {
        let (ya, yb) = (ya.branch.as_str(), yb.branch.as_str());
        if pair(&YUKHAP, ya, yb) { s += 5; why.push(format!("띠({ya}·{yb})도 육합이에요")) }
        else if half(ya, yb) { s += 4; why.push(format!("띠({ya}·{yb})가 삼합으로 이어져요")) }
        else if pair(&CHUNG, ya, yb) { s -= 5; why.push(format!("띠({ya}·{yb})는 충이라 처음엔 낯설 수 있어요")) }
    }
    let mut fill = 0;
    for (i, n) in a.element_names.iter().enumerate() {
        if a.elements[i] == 0 && b.elements[i] >= 2 { fill += 5; why.push(format!("첫 분에게 없는 {n}을 두 번째 분이 넉넉히 가졌어요")) }
        if b.elements[i] == 0 && a.elements[i] >= 2 { fill += 5; why.push(format!("두 번째 분에게 없는 {n}을 첫 분이 넉넉히 가졌어요")) }
    }
    s += fill.min(10);
    let s = s.clamp(38, 97) as u32;
    let kind = match s { 86.. => "귀인", 76..=85 => "단짝", 66..=75 => "동반자", 56..=65 => "자극제", _ => "숙제" };
    Score { score: s, kind, reasons: why }
}

const MATCH_SYSTEM: &str = "당신은 '역마아씨'입니다. 같은 차에 탄 두 사람의 사주 궁합을 풀어 주는 다정한 역술가입니다.
말투: 처음부터 끝까지 존댓말(~요, ~죠). 짧은 문장. 전문 용어는 꼭 필요할 때만 쓰고 바로 쉬운 말로 풀어 줍니다.
구성:
- title: 두 사람 관계에 대한 한 줄. 25자 안팎. 따뜻하고 희망적인 문장.
- sections: 정확히 3개. 각 heading은 15자 안팎, body는 300~380자.
  1) 두 사람이 서로에게 어떤 사람인지 공감 가게 짚습니다(\"이런 순간 있으시죠?\").
  2) 명식 근거(주어진 점수 근거 문장)를 쉬운 말로 풀어 잘 맞는 점과 부딪히는 점을 말합니다.
  3) heading은 \"역마아씨의 예언\". 두 사람에게 곧 올 장면을 단정하는 말투로 내다보고, 그 예언이 이루어지게 지금 차 안에서 함께 해 볼 작은 행동 하나로 끝냅니다. 결과는 반드시 좋은 쪽으로.
금지: 건강·사고·죽음·이별 단정, 불안을 키우는 말.";

pub fn request_body(model: &str, chart_a: &str, chart_b: &str, sc: &Score) -> Value {
    json!({
        "model": model,
        "messages": [
            { "role": "system", "content": MATCH_SYSTEM },
            { "role": "user", "content": format!("[첫째 사람]\n{chart_a}\n\n[둘째 사람]\n{chart_b}\n\n[궁합 점수] {}점, 유형 {}\n[근거]\n{}", sc.score, sc.kind, sc.reasons.join("\n")) }
        ],
        "response_format": { "type": "json_schema", "json_schema": { "name": "saju_match", "strict": true, "schema": crate::reading::schema() } },
        "reasoning": { "enabled": false },
        "temperature": 0.8,
        "max_tokens": 2000
    })
}

/// LLM 키가 없을 때의 궁합 목업
pub fn mock_report(a: &str, b: &str, sc: &Score) -> Report {
    let good = sc.reasons.iter().find(|r| !r.contains("부딪") && !r.contains("낯설") && !r.contains("상극")).cloned()
        .unwrap_or_else(|| "두 분의 기운이 서로 다른 쪽을 채워 줘요".into());
    let rough = sc.reasons.iter().find(|r| r.contains("부딪") || r.contains("낯설") || r.contains("상극")).cloned()
        .unwrap_or_else(|| "속도가 조금 달라서 한 분이 기다려 줘야 할 때가 있어요".into());
    let kind_line = match sc.kind {
        "귀인" => "서로의 귀인", "단짝" => "말 안 해도 통하는 단짝", "동반자" => "같은 길을 오래 걷는 동반자",
        "자극제" => "서로를 자라게 하는 자극제", _ => "함께 풀어 갈 숙제가 있는 사이",
    };
    Report {
        title: format!("{a}님과 {b}님, {kind_line}예요"),
        sections: vec![
            Section { heading: "이런 순간 있으시죠?".into(), body: format!("같이 있으면 별말 안 해도 편한데, 막상 중요한 이야기는 서로 미루게 되는 순간이 있으시죠? {a}님은 상대가 먼저 꺼내 주길 기다리고, {b}님은 괜히 분위기를 깰까 봐 웃으며 넘기고요. 그런데 그건 마음이 없어서가 아니에요. 서로를 그만큼 아끼고 있어서 조심하는 거예요. 오늘 같은 차에 나란히 앉은 이 시간이, 미뤄 둔 이야기를 꺼내기에 딱 좋은 때예요.") },
            Section { heading: "잘 맞는 점, 부딪히는 점".into(), body: format!("두 분의 궁합은 {}점, '{}'예요. {good}. 그래서 한쪽이 지칠 때 다른 쪽이 자연스럽게 기운을 채워 줘요. 대신 {rough}. 이건 나쁜 신호가 아니라, 서로의 모난 곳을 다듬어 주는 과정이에요. 부딪힐 때는 누가 옳은지보다 '지금 무엇이 필요한지'를 먼저 물어보세요.", sc.score, sc.kind) },
            Section { heading: "역마아씨의 예언".into(), body: format!("자, 예언 하나 드릴게요. 두 분은 올해가 가기 전에 함께 큰 결정을 하나 내리게 돼요. 처음엔 의견이 갈리는데, 마지막에는 {b}님이 꺼낸 한마디로 {a}님 마음이 정해져요. 그리고 그 결정은 두 분 모두에게 좋은 쪽으로 흘러갑니다. 이 예언이 그대로 맞게 하려면 딱 하나만 하세요. 차에서 내리기 전에 서로에게 '오늘 고마웠던 한 가지'를 말해 주는 거예요. 그 한 문장이 두 분 사이의 기운을 한 번 더 묶어 줍니다.") },
        ],
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::manse::{compute, BirthInput};
    fn m(birth: &str, time: &str, g: &str) -> Manse {
        compute(&BirthInput { name: None, gender: g.into(), birth: birth.into(), lunar: false, leap: false, time: time.into(), place: "서울".into(), zasi: None }).unwrap()
    }
    #[test]
    fn scores_in_range() {
        let a = m("2000.01.01", "12:00", "여자");
        let b = m("1994.03.07", "07:30", "남자");
        let s = score(&a, &b);
        assert!((38..=97).contains(&s.score));
        assert!(!s.reasons.is_empty());
        let r = mock_report("지민", "하늘", &s);
        assert_eq!(r.sections.len(), 3);
        // 같은 사람끼리는 같은 점수
        assert_eq!(score(&a, &b).score, score(&a, &b).score);
    }
}
