//! 천문 계산 (Jean Meeus, Astronomical Algorithms 2판).
//! - 태양 겉보기 황경: 25장 저정밀 식(오차 약 0.01°, 절입 시각으로 약 ±15분)
//! - 균시차: 28장 Smart 식
//! - 합삭(新月): 49장 (오차 수십 초)
//! 시각은 모두 율리우스일(JD, UT 기준)로 다룬다.

use std::f64::consts::PI;

fn rad(d: f64) -> f64 { d * PI / 180.0 }
pub fn norm360(x: f64) -> f64 { let r = x % 360.0; if r < 0.0 { r + 360.0 } else { r } }

/// 그레고리력 날짜(UT) → JD
pub fn jd(y: i32, m: u32, d: f64) -> f64 {
    let (mut y, mut m) = (y as f64, m as f64);
    if m <= 2.0 { y -= 1.0; m += 12.0; }
    let a = (y / 100.0).floor();
    let b = 2.0 - a + (a / 4.0).floor();
    (365.25 * (y + 4716.0)).floor() + (30.6001 * (m + 1.0)).floor() + d + b - 1524.5
}

/// JD → (년, 월, 일(소수))
pub fn from_jd(jd: f64) -> (i32, u32, f64) {
    let z = (jd + 0.5).floor();
    let f = jd + 0.5 - z;
    let a = if z < 2299161.0 { z } else {
        let al = ((z - 1867216.25) / 36524.25).floor();
        z + 1.0 + al - (al / 4.0).floor()
    };
    let b = a + 1524.0;
    let c = ((b - 122.1) / 365.25).floor();
    let d = (365.25 * c).floor();
    let e = ((b - d) / 30.6001).floor();
    let day = b - d - (30.6001 * e).floor() + f;
    let m = if e < 14.0 { e - 1.0 } else { e - 13.0 };
    let y = if m > 2.0 { c - 4716.0 } else { c - 4715.0 };
    (y as i32, m as u32, day)
}

/// ΔT(초) = TT - UT. Espenak·Meeus 다항식(1900~2150)
pub fn delta_t(year: f64) -> f64 {
    let y = year;
    if y < 1920.0 { let t = y - 1900.0; -2.79 + 1.494119 * t - 0.0598939 * t * t + 0.0061966 * t.powi(3) - 0.000197 * t.powi(4) }
    else if y < 1941.0 { let t = y - 1920.0; 21.20 + 0.84493 * t - 0.076100 * t * t + 0.0020936 * t.powi(3) }
    else if y < 1961.0 { let t = y - 1950.0; 29.07 + 0.407 * t - t * t / 233.0 + t.powi(3) / 2547.0 }
    else if y < 1986.0 { let t = y - 1975.0; 45.45 + 1.067 * t - t * t / 260.0 - t.powi(3) / 718.0 }
    else if y < 2005.0 { let t = y - 2000.0; 63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t.powi(3) + 0.000651814 * t.powi(4) + 0.00002373599 * t.powi(5) }
    else if y < 2050.0 { let t = y - 2000.0; 62.92 + 0.32217 * t + 0.005589 * t * t }
    else { let u = (y - 1820.0) / 100.0; -20.0 + 32.0 * u * u - 0.5628 * (2150.0 - y) }
}

fn jde_from_ut(jd_ut: f64) -> f64 { let (y, _, _) = from_jd(jd_ut); jd_ut + delta_t(y as f64) / 86400.0 }

/// 태양 겉보기 황경(도). 입력은 UT 기준 JD
pub fn sun_longitude(jd_ut: f64) -> f64 {
    let t = (jde_from_ut(jd_ut) - 2451545.0) / 36525.0;
    let l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
    let m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t;
    let c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * rad(m).sin()
        + (0.019993 - 0.000101 * t) * rad(2.0 * m).sin()
        + 0.000289 * rad(3.0 * m).sin();
    let omega = 125.04 - 1934.136 * t;
    norm360(l0 + c - 0.00569 - 0.00478 * rad(omega).sin())
}

/// 태양 황경이 target(도)이 되는 순간(UT JD). guess 근처에서 찾는다
pub fn sun_longitude_time(target: f64, guess: f64) -> f64 {
    let mut j = guess;
    for _ in 0..30 {
        let mut diff = target - sun_longitude(j);
        if diff > 180.0 { diff -= 360.0 }
        if diff < -180.0 { diff += 360.0 }
        j += diff / 360.0 * 365.2422;
        if diff.abs() < 1e-7 { break }
    }
    j
}

/// 균시차(분). 진태양시 = 평균태양시 + 균시차
pub fn equation_of_time(jd_ut: f64) -> f64 {
    let t = (jde_from_ut(jd_ut) - 2451545.0) / 36525.0;
    let l0 = rad(norm360(280.46646 + 36000.76983 * t + 0.0003032 * t * t));
    let m = rad(357.52911 + 35999.05029 * t - 0.0001537 * t * t);
    let e = 0.016708634 - 0.000042037 * t;
    let eps = rad(23.4392911 - 0.0130042 * t);
    let y = (eps / 2.0).tan().powi(2);
    let v = y * (2.0 * l0).sin() - 2.0 * e * m.sin() + 4.0 * e * y * m.sin() * (2.0 * l0).cos()
        - 0.5 * y * y * (4.0 * l0).sin() - 1.25 * e * e * (2.0 * m).sin();
    v * 4.0 * 180.0 / PI
}

/// k번째 합삭(2000년 1월 기준)의 순간(UT JD)
pub fn new_moon(k: f64) -> f64 {
    let t = k / 1236.85;
    let mut jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t * t - 0.000000150 * t.powi(3) + 0.00000000073 * t.powi(4);
    let e = 1.0 - 0.002516 * t - 0.0000074 * t * t;
    let m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t * t - 0.00000011 * t.powi(3));
    let mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t * t + 0.00001238 * t.powi(3) - 0.000000058 * t.powi(4));
    let f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t * t - 0.00000227 * t.powi(3) + 0.000000011 * t.powi(4));
    let om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t * t + 0.00000215 * t.powi(3));
    jde += -0.40720 * mp.sin() + 0.17241 * e * m.sin() + 0.01608 * (2.0 * mp).sin() + 0.01039 * (2.0 * f).sin()
        + 0.00739 * e * (mp - m).sin() - 0.00514 * e * (mp + m).sin() + 0.00208 * e * e * (2.0 * m).sin()
        - 0.00111 * (mp - 2.0 * f).sin() - 0.00057 * (mp + 2.0 * f).sin() + 0.00056 * e * (2.0 * mp + m).sin()
        - 0.00042 * (3.0 * mp).sin() + 0.00042 * e * (m + 2.0 * f).sin() + 0.00038 * e * (m - 2.0 * f).sin()
        - 0.00024 * e * (2.0 * mp - m).sin() - 0.00017 * om.sin() - 0.00007 * (mp + 2.0 * m).sin()
        + 0.00004 * (2.0 * mp - 2.0 * f).sin() + 0.00004 * (3.0 * m).sin() + 0.00003 * (mp + m - 2.0 * f).sin()
        + 0.00003 * (2.0 * mp + 2.0 * f).sin() - 0.00003 * (mp + m + 2.0 * f).sin() + 0.00003 * (mp - m + 2.0 * f).sin()
        - 0.00002 * (mp - m - 2.0 * f).sin() - 0.00002 * (3.0 * mp + m).sin() + 0.00002 * (4.0 * mp).sin();
    let a = [
        (299.77 + 0.107408 * k - 0.009173 * t * t, 0.000325), (251.88 + 0.016321 * k, 0.000165),
        (251.83 + 26.651886 * k, 0.000164), (349.42 + 36.412478 * k, 0.000126), (84.66 + 18.206239 * k, 0.000110),
        (141.74 + 53.303771 * k, 0.000062), (207.14 + 2.453732 * k, 0.000060), (154.84 + 7.306860 * k, 0.000056),
        (34.52 + 27.261239 * k, 0.000047), (207.19 + 0.121824 * k, 0.000042), (291.34 + 1.844379 * k, 0.000040),
        (161.72 + 24.198154 * k, 0.000037), (239.56 + 25.513099 * k, 0.000035), (331.55 + 3.592518 * k, 0.000023),
    ];
    for (arg, c) in a { jde += c * rad(arg).sin(); }
    let (y, _, _) = from_jd(jde);
    jde - delta_t(y as f64) / 86400.0
}

/// jd_ut 이전(또는 같은)의 가장 가까운 합삭
pub fn new_moon_before(jd_ut: f64) -> f64 {
    let mut k = ((jd_ut - 2451550.09766) / 29.530588861).floor();
    while new_moon(k + 1.0) <= jd_ut { k += 1.0 }
    while new_moon(k) > jd_ut { k -= 1.0 }
    new_moon(k)
}
