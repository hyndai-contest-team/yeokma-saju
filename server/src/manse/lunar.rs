//! 음력 → 양력. 합삭과 중기(中氣)로 직접 계산한다 (한국 시각 UTC+9 기준 날짜).
//! 동지가 든 달을 11월로 하고, 동지에서 다음 동지 사이에 달이 13개면 중기가 없는 첫 달을 윤달로 둔다.

use super::astro::{jd, new_moon_before, sun_longitude, sun_longitude_time};

const KST: f64 = 9.0 / 24.0;
fn civil_day(jd_ut: f64) -> i64 { (jd_ut + KST + 0.5).floor() as i64 }

/// 그 해(양력)의 동지 순간(UT JD)
fn winter_solstice(year: i32) -> f64 { sun_longitude_time(270.0, jd(year, 12, 21.5)) }

/// 날짜(KST 일 번호)가 속한 달의 첫날(합삭일)
fn month_start_day(day: i64) -> f64 {
    // day의 끝(다음날 0시 KST) 직전까지 일어난 합삭
    let end_ut = day as f64 + 0.5 - KST; // 그날 24시(KST)의 UT JD
    new_moon_before(end_ut - 1e-6)
}

/// 동지가 든 달의 시작 합삭 순간
fn solstice_month(year: i32) -> f64 { month_start_day(civil_day(winter_solstice(year))) }

/// 달 [start, next) 안에 중기(황경 30°의 배수)가 드는가 (KST 날짜 기준)
fn has_zhongqi(start: f64, next: f64) -> bool {
    let (d0, d1) = (civil_day(start), civil_day(next));
    let lon = sun_longitude(start);
    let target = ((lon / 30.0).floor() + 1.0) * 30.0;
    let t = sun_longitude_time(target % 360.0, start + 15.0);
    let dt = civil_day(t);
    dt >= d0 && dt < d1
}

pub struct LunarMonth { pub year: i32, pub month: u32, pub leap: bool, pub start_day: i64, pub days: u32 }

/// 동지월(year-1) ~ 동지월(year) 사이의 달에 번호를 붙인다. 첫 달이 11월.
fn label_sui(year: i32) -> Vec<LunarMonth> {
    let a = solstice_month(year - 1);
    let b = solstice_month(year);
    let mut starts = vec![a];
    let mut cur = a;
    while civil_day(cur) < civil_day(b) {
        cur = new_moon_before(cur + 30.0);
        starts.push(cur);
    }
    let n = starts.len() - 1; // a부터 b 전까지의 달 개수 (12 또는 13)
    let leap_year = n == 13;
    let mut out = Vec::new();
    let mut num = 11u32;
    let mut ly = year - 1; // 11, 12월은 앞 해. 1월이 되면 year
    let mut leap_used = false;
    for i in 0..n {
        let (s, e) = (starts[i], starts[i + 1]);
        let leap = leap_year && !leap_used && i > 0 && !has_zhongqi(s, e);
        if leap { leap_used = true } else if i > 0 {
            num = if num == 12 { 1 } else { num + 1 };
            if num == 1 { ly = year }
        }
        out.push(LunarMonth { year: ly, month: num, leap, start_day: civil_day(s), days: (civil_day(e) - civil_day(s)) as u32 });
    }
    out
}

/// 음력 (년, 월, 일, 윤달) → 양력 KST 일 번호(JD 정수일). 없는 날짜면 None
pub fn to_solar_day(year: i32, month: u32, day: u32, leap: bool) -> Option<i64> {
    for y in [year, year + 1] {
        for m in label_sui(y) {
            if m.year == year && m.month == month && m.leap == leap {
                return if day >= 1 && day <= m.days { Some(m.start_day + day as i64 - 1) } else { None };
            }
        }
    }
    None
}
