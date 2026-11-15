use super::*;
use super::astro::*;

fn kst(jd_ut: f64) -> (String, String) { super::fmt_dt(jd_ut + 9.0 / 24.0) }
fn solar(day: i64) -> String { let (y, m, d) = from_jd(day as f64); format!("{:04}.{:02}.{:02}", y, m, d.floor() as u32) }

#[test]
fn ipchun_2024() {
    // 한국천문연구원: 2024 입춘 2월 4일 17:27
    let t = sun_longitude_time(315.0, jd(2024, 2, 4.5));
    let (d, h) = kst(t);
    println!("입춘 2024 = {d} {h}");
    assert_eq!(d, "2024.02.04");
    let mins = |s: &str| { let v: Vec<i32> = s.split(':').map(|x| x.parse().unwrap()).collect(); v[0] * 60 + v[1] };
    assert!((mins(&h) - mins("17:27")).abs() <= 20, "{h}");
}

#[test]
fn lunar_dates() {
    assert_eq!(solar(lunar::to_solar_day(2024, 1, 1, false).unwrap()), "2024.02.10");
    assert_eq!(solar(lunar::to_solar_day(1994, 1, 1, false).unwrap()), "1994.02.10");
    assert_eq!(solar(lunar::to_solar_day(2025, 8, 15, false).unwrap()), "2025.10.06");
    assert_eq!(solar(lunar::to_solar_day(2023, 2, 1, true).unwrap()), "2023.03.22");   // 윤2월
    assert_eq!(solar(lunar::to_solar_day(2020, 4, 1, true).unwrap()), "2020.05.23");   // 윤4월
    assert_eq!(solar(lunar::to_solar_day(2017, 5, 1, true).unwrap()), "2017.06.24");   // 윤5월
    assert!(lunar::to_solar_day(2024, 2, 1, true).is_none()); // 2024년엔 윤2월 없음
}

fn inp(birth: &str, time: &str, gender: &str) -> BirthInput {
    BirthInput { name: None, gender: gender.into(), birth: birth.into(), lunar: false, leap: false, time: time.into(), place: "서울".into(), zasi: None }
}

#[test]
fn day_pillar_2000() {
    let m = compute(&inp("2000.01.01", "12:00", "남성")).unwrap();
    let day = m.pillars.iter().find(|p| p.label == "일주").unwrap();
    assert_eq!(day.hanja, "戊午");
}

#[test]
fn year_changes_at_ipchun() {
    let before = compute(&inp("2024.02.04", "16:00", "남성")).unwrap();
    let after = compute(&inp("2024.02.04", "18:30", "남성")).unwrap();
    let y = |m: &Manse| m.pillars.iter().find(|p| p.label == "연주").unwrap().hanja.clone();
    assert_eq!(y(&before), "癸卯");
    assert_eq!(y(&after), "甲辰");
    let mo = |m: &Manse| m.pillars.iter().find(|p| p.label == "월주").unwrap().hanja.clone();
    assert_eq!(mo(&after), "丙寅");
}

#[test]
fn sample_1994() {
    let mut i = inp("1994.03.07", "07:30", "여성");
    i.place = "부산 해운대".into();
    let m = compute(&i).unwrap();
    println!("{}", serde_json::to_string_pretty(&m).unwrap());
    let get = |l: &str| m.pillars.iter().find(|p| p.label == l).unwrap().hanja.clone();
    assert_eq!(get("연주"), "甲戌");
    assert_eq!(get("월주"), "丁卯");
}

#[test]
fn mock_report_shape() {
    let m = compute(&inp("1994.03.07", "07:30", "여성")).unwrap();
    let r = crate::reading::mock_report("김하늘", &m, "면접 가는 길인데 떨려요");
    assert_eq!(r.sections.len(), 3);
    let total: usize = r.sections.iter().map(|s| s.body.chars().count()).sum();
    println!("{}\n{total}자\n{}", r.title, r.sections[2].body);
    assert!(total > 700);
}

#[test]
fn demo_reading_chart() {
    // 시연용 풀이(reading::demo_report)가 말하는 명식과 실제 계산이 같은지
    let m = compute(&inp("2000.01.01", "12:00", "여자")).unwrap();
    let h: Vec<&str> = m.pillars.iter().map(|p| p.hanja.as_str()).collect();
    assert_eq!(h, vec!["戊午", "戊午", "丙子", "己卯"]);
    assert_eq!(m.elements, [1, 3, 3, 0, 1]);
    let r = crate::reading::demo_report("임지민", "2000.01.01", "12:00").unwrap();
    assert_eq!(r.sections.len(), 3);
    let total: usize = r.sections.iter().map(|s| s.body.chars().count()).sum();
    assert!(total >= 1000, "{total}");
}
