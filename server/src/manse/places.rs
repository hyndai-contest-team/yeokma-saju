//! 태어난 곳 → 경도. 목록은 레포의 `data/places.tsv`(앱과 같이 씀).
//! 말한 문장에서 지명을 앞에서부터 가장 긴 이름으로 찾고, 상위 지역이 같이 나오면 그 아래로 좁힌다.
//! 앱은 고른 지역의 정식 이름(예: "경기도 광주시")을 보낸다. 그래도 여러 곳이면 목록의 첫 곳을 쓴다.

use std::collections::HashMap;
use std::sync::OnceLock;

const TSV: &str = include_str!("../../../data/places.tsv");

pub const SEOUL: f64 = 126.978;

pub struct Place {
    pub full: String,
    pub lon: f64,
    parent: Option<usize>,
}

struct Book {
    places: Vec<Place>,
    keys: HashMap<String, Vec<usize>>,
    longest: usize,
}

fn book() -> &'static Book {
    static B: OnceLock<Book> = OnceLock::new();
    B.get_or_init(|| {
        let mut places: Vec<Place> = Vec::new();
        let mut by_full: HashMap<String, usize> = HashMap::new();
        let mut keys: HashMap<String, Vec<usize>> = HashMap::new();
        for line in TSV.lines().filter(|l| !l.starts_with('#') && !l.trim().is_empty()) {
            let c: Vec<&str> = line.split('\t').collect();
            let (parent, name, lon) = (c[0], c[1], c[2].parse::<f64>().unwrap_or(SEOUL));
            let aliases = c.get(3).copied().unwrap_or("");
            let full = if parent.is_empty() { name.to_string() } else { format!("{parent} {name}") };
            let id = places.len();
            places.push(Place { full: full.clone(), lon, parent: by_full.get(parent).copied() });
            by_full.insert(full, id);
            let mut ks: Vec<String> = vec![name.to_string()];
            if !parent.is_empty() {
                let chars: Vec<char> = name.chars().collect();
                if chars.len() >= 3 && matches!(chars[chars.len() - 1], '시' | '군' | '구') {
                    ks.push(chars[..chars.len() - 1].iter().collect());
                }
            }
            ks.extend(aliases.split(',').map(str::trim).filter(|a| !a.is_empty()).map(String::from));
            for k in ks {
                let v = keys.entry(k).or_default();
                if !v.contains(&id) { v.push(id) }
            }
        }
        let longest = keys.keys().map(|k| k.chars().count()).max().unwrap_or(1);
        Book { places, keys, longest }
    })
}

fn is_ancestor(b: &Book, a: usize, mut x: usize) -> bool {
    while let Some(p) = b.places[x].parent {
        if p == a { return true }
        x = p;
    }
    false
}

/// 말한 문장에 들어 있는 지역 후보(정식 이름 순서는 목록 순). 하나면 확정, 여럿이면 물어볼 대상
pub fn candidates(text: &str) -> Vec<&'static Place> {
    let b = book();
    let chars: Vec<char> = text.chars().filter(|c| !c.is_whitespace()).collect();
    let mut hit: Vec<usize> = Vec::new();
    let mut i = 0;
    while i < chars.len() {
        let mut found = 0;
        for len in (1..=b.longest.min(chars.len() - i)).rev() {
            let w: String = chars[i..i + len].iter().collect();
            if let Some(ids) = b.keys.get(&w) {
                for &id in ids { if !hit.contains(&id) { hit.push(id) } }
                found = len;
                break;
            }
        }
        i += found.max(1);
    }
    // 상위 지역이 같이 나왔으면 그 아래 것만, 그다음 더 깊은 것이 있으면 상위는 뺀다
    let under: Vec<usize> = hit.iter().copied().filter(|&x| hit.iter().any(|&a| is_ancestor(b, a, x))).collect();
    let pool = if under.is_empty() { hit } else { under };
    let mut out: Vec<usize> = pool.iter().copied().filter(|&a| !pool.iter().any(|&x| is_ancestor(b, a, x))).collect();
    out.sort();
    out.into_iter().map(|id| &b.places[id]).collect()
}

/// (경도, 쓴 지명). 못 찾으면 None
pub fn longitude(place: &str) -> Option<(f64, &'static str)> {
    candidates(place).first().map(|p| (p.lon, p.full.as_str()))
}

#[cfg(test)]
mod tests {
    use super::*;
    fn names(t: &str) -> Vec<&'static str> { candidates(t).iter().map(|p| p.full.as_str()).collect() }

    #[test]
    fn resolves() {
        assert_eq!(names("광주"), vec!["광주광역시", "경기도 광주시"]);
        assert_eq!(names("경기도 광주"), vec!["경기도 광주시"]);
        assert_eq!(names("경기도 광주시"), vec!["경기도 광주시"]);
        assert_eq!(names("광주에서 태어났어"), vec!["광주광역시", "경기도 광주시"]);
        assert_eq!(names("광주광역시"), vec!["광주광역시"]);
        assert_eq!(names("광주 북구"), vec!["광주광역시 북구"]);
        assert_eq!(names("서울 강남구"), vec!["서울특별시 강남구"]);
        assert_eq!(names("강남"), vec!["서울특별시 강남구"]);
        assert_eq!(names("분당"), vec!["경기도 성남시 분당구"]);
        assert_eq!(names("경기도 성남시 분당구"), vec!["경기도 성남시 분당구"]);
        assert_eq!(names("제주"), vec!["제주특별자치도 제주시"]);
        assert_eq!(names("고성").len(), 2);
        assert_eq!(names("중구").len(), 6);
        assert_eq!(names("부산 중구"), vec!["부산광역시 중구"]);
        assert_eq!(names("남양주"), vec!["경기도 남양주시"]);
        assert_eq!(names("마산"), vec!["경상남도 창원시 마산합포구"]);
        assert_eq!(names("부산 해운대"), vec!["부산광역시 해운대구"]);
        assert!(names("뉴욕").is_empty());
    }
}
