//! 천간·지지와 그 관계 표

pub const STEM_H: [&str; 10] = ["甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"];
pub const STEM_K: [&str; 10] = ["갑", "을", "병", "정", "무", "기", "경", "신", "임", "계"];
pub const BRANCH_H: [&str; 12] = ["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"];
pub const BRANCH_K: [&str; 12] = ["자", "축", "인", "묘", "진", "사", "오", "미", "신", "유", "술", "해"];
pub const ELEMENT: [&str; 5] = ["목", "화", "토", "금", "수"];

/// 천간의 오행(0목 1화 2토 3금 4수)
pub fn stem_element(s: usize) -> usize { s / 2 }
/// 천간 음양: 짝수=양
pub fn stem_yang(s: usize) -> bool { s % 2 == 0 }

/// 지장간 (여기, 중기, 정기) 순. 마지막이 정기(본기)
pub fn hidden(b: usize) -> &'static [usize] {
    const H: [&[usize]; 12] = [
        &[8, 9], &[9, 7, 5], &[4, 2, 0], &[0, 1], &[1, 9, 4], &[4, 6, 2],
        &[2, 5, 3], &[3, 1, 5], &[4, 8, 6], &[6, 7], &[7, 3, 4], &[4, 0, 8],
    ];
    H[b]
}
pub fn branch_main_stem(b: usize) -> usize { *hidden(b).last().unwrap() }
pub fn branch_element(b: usize) -> usize { stem_element(branch_main_stem(b)) }

/// 십신: 일간 기준 다른 천간의 관계
pub fn ten_god(day: usize, other: usize) -> &'static str {
    let (de, oe) = (stem_element(day), stem_element(other));
    let same = stem_yang(day) == stem_yang(other);
    match (oe + 5 - de) % 5 {
        0 => if same { "비견" } else { "겁재" },
        1 => if same { "식신" } else { "상관" },
        2 => if same { "편재" } else { "정재" },
        3 => if same { "편관" } else { "정관" },
        _ => if same { "편인" } else { "정인" },
    }
}

/// 12운성
pub fn twelve_stage(day: usize, b: usize) -> &'static str {
    const NAMES: [&str; 12] = ["장생", "목욕", "관대", "건록", "제왕", "쇠", "병", "사", "묘", "절", "태", "양"];
    const BIRTH: [usize; 10] = [11, 6, 2, 9, 2, 9, 5, 0, 8, 3]; // 각 천간의 장생 지지
    let start = BIRTH[day];
    let i = if stem_yang(day) { (b + 12 - start) % 12 } else { (start + 12 - b) % 12 };
    NAMES[i]
}

/// 60갑자 번호 → (천간, 지지)
pub fn split(idx: usize) -> (usize, usize) { (idx % 10, idx % 12) }
/// (천간, 지지) → 60갑자 번호
pub fn join(s: usize, b: usize) -> usize { ((6 * s as i64 - 5 * b as i64).rem_euclid(60)) as usize }

/// 일주의 공망 두 지지
pub fn gongmang(day_idx: usize) -> [usize; 2] {
    let start = (day_idx - day_idx % 10) % 12;
    [(start + 10) % 12, (start + 11) % 12]
}

/// 두 지지의 관계 (육합, 충, 삼합 일부는 생략)
pub fn branch_relation(a: usize, b: usize) -> Option<&'static str> {
    if (a + 12 - b) % 12 == 6 { return Some("충") }
    const HAP: [(usize, usize); 6] = [(0, 1), (2, 11), (3, 10), (4, 9), (5, 8), (6, 7)];
    if HAP.iter().any(|&(x, y)| (x == a && y == b) || (x == b && y == a)) { return Some("합") }
    None
}

/// 두 천간의 합(갑기, 을경, 병신, 정임, 무계) / 충(갑경, 을신, 병임, 정계)
pub fn stem_relation(a: usize, b: usize) -> Option<&'static str> {
    let (x, y) = (a.min(b), a.max(b));
    if y - x == 5 { return Some("합") }
    if y - x == 6 && x <= 3 { return Some("충") }
    None
}
