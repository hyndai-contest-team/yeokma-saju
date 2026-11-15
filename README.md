<p align="center">
  <img src="assets/brand/app/app-icon-512.png" width="140" height="140" alt="흰 배경의 驛馬四柱 도장 로고">
</p>

<h1 align="center">역마사주 (Yeokma Saju)</h1>

<p align="center">팀 tridrive</p>

## 앱 이름 및 한 줄 소개

**역마사주 (Yeokma Saju)**: 중요한 일정을 앞두고, 출발 전이나 도착 후 차 안에서 보내는 몇 분. 그 시간의 고민을 사주로 풀어 주는 차량용 사주 앱

https://github.com/user-attachments/assets/76b15628-df0b-4d45-a509-7a91ebe3bafa

자세한 기능, 차량 특화 요소, 사업성은 [상세 README](docs/상세_README.md)와 [기획서](docs/기획서.md)에 있습니다.

## 실행 방법

### 폴더

| 폴더 | 내용 |
|---|---|
| `app/` | 차량 앱. Kotlin, Jetpack Compose, Media3, Pleos Vehicle SDK 2.3.2, Pleos SpeechToText SDK 3.3.1 |
| `server/` | 서버. Rust. 이 컴퓨터에서 로컬로 실행하고, 포인트는 로컬 DB에 저장합니다 |
| `data/` | 태어난 곳 목록(전국 특별시·광역시와 시·군). 앱과 서버가 같이 씁니다 |

### 준비물

| 항목 | 버전 | 필요한 때 |
|---|---|---|
| Android Studio | 2026.2 이상 (명령줄 빌드에는 Android Studio에 들어 있는 JDK를 씁니다) | 앱 |
| Pleos 에뮬레이터 | Android SDK의 `pleos_car_emulator` 이미지 v2.1.0, 가상 기기 2560×1440 | 앱 |
| Rust | 1.100 이상 (`rustup`) | 서버 |
| Node.js | 24 이상 (`npx wrangler`) | 서버 |

### 1. 앱 설치와 실행

**서버를 켜지 않아도 앱은 끝까지 동작합니다.** 서버에 연결되지 않으면 앱 안의 목업 풀이(태어난 달의 계절 기운으로 쓴 풀이)가 나오고, 복채(포인트) 화면은 건너뜁니다. 정밀 만세력, 포인트, LLM 풀이를 보려면 2번의 서버를 켭니다.

#### 바로 설치 (빌드된 APK)

저장소의 [`apk/tridrive_yeokmasaju_release.apk`](apk/tridrive_yeokmasaju_release.apk)를 설치합니다. [Releases](https://github.com/hyndai-contest-team/yeokma-saju/releases/latest)에도 같은 파일이 있습니다. 빌드 없이 바로 됩니다.

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"

# 저장소 루트에서, 에뮬레이터를 켠 뒤 설치하고 실행합니다
$ANDROID_HOME/platform-tools/adb install -r --user 10 apk/tridrive_yeokmasaju_release.apk
$ANDROID_HOME/platform-tools/adb shell am start --user 10 -n com.yeokma.saju/.MainActivity
```

#### 직접 빌드해서 설치

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

# 에뮬레이터 켜기
$ANDROID_HOME/emulator/emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load &
$ANDROID_HOME/platform-tools/adb wait-for-device

# 빌드
cd app
./gradlew :app:assembleDebug

# 설치와 실행: Pleos 차량 화면은 사용자 10번이라 --user 10이 필요합니다
$ANDROID_HOME/platform-tools/adb install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
$ANDROID_HOME/platform-tools/adb shell am start --user 10 -n com.yeokma.saju/.MainActivity
```

Android Studio에서는 `app/` 폴더를 열고 실행해도 됩니다.

- 앱은 서버를 `http://10.0.2.2:8787`(에뮬레이터에서 본 이 컴퓨터)로 찾습니다.
- 음성 입력은 Pleos SpeechToText SDK(3.3.1)의 `ON_DEVICE` 모드를 씁니다. 차 안의 인식 엔진이라 앱 등록 키가 필요 없습니다. Pleos 인식 엔진이 없는 기기에서는 안드로이드 기본 음성 인식기로, 그것도 없으면 키보드로 입력합니다.
- 공조 풍량·풍향 제어는 에뮬레이터가 지원하지 않아 실차에서만 동작합니다.

### 2. 서버 켜기 (선택)

정밀 만세력, 포인트, LLM 풀이를 보려면 이 컴퓨터에서 서버를 켭니다. Rust가 설치되어 있지 않다면 먼저 [Rust 설치 방법(macOS)](docs/rust-install-macos.md)을 따라 설치합니다.

```bash
cd server

# 처음 한 번
npx wrangler@4.162.0 d1 migrations apply yeokma-saju --local   # 로컬 포인트 DB 만들기

# (선택) LLM 키. 넣지 않으면 서버의 목업 풀이(실제 만세력으로 쓴 풀이)가 나옵니다
echo 'OPENROUTER_API_KEY="sk-or-..."' > .dev.vars

# 서버 실행 (에뮬레이터에서 붙을 수 있게 0.0.0.0)
npx wrangler@4.162.0 dev --ip 0.0.0.0 --port 8787
```

`http://127.0.0.1:8787/` 에서 `yeokma-saju api`가 보이면 켜진 것입니다.

- `.dev.vars`는 깃에 올라가지 않습니다. 키를 레포나 앱에 넣지 마세요.
- LLM 모델은 `wrangler.toml`의 `MODEL` 값 하나로 바꿉니다. 기본값은 `deepseek/deepseek-v4.1-flash-1017`(OpenRouter)입니다.
- 만세력 테스트: `cargo test --lib`

#### API

| 경로 | 하는 일 |
|---|---|
| `POST /api/manse` | 만세력 계산 |
| `GET /api/wallet?device=` | 포인트 잔액 |
| `POST /api/charge` | 포인트 충전. 시연용이라 결제 인증 없이 바로 충전됩니다 (990원 1회 / 9,900원 11회 / 99,000원 115회) |
| `POST /api/reading` | 990P를 먼저 빼고 풀이를 만듭니다. 포인트가 모자라면 402를 돌려주고 LLM을 부르지 않습니다 |
| `POST /api/match` | 두 사람의 궁합. 같은 방식으로 990P를 먼저 빼고 점수와 풀이를 만듭니다 |

## 테스트 환경 및 필요한 설정

| 설정 | 방법 |
|---|---|
| 에뮬레이터 실행 | Cold boot로 켠다(`-no-snapshot-load`). Quick boot로 켜면 차량 신호를 쓸 수 없다 |
| 설치·실행 사용자 | Pleos 차량 화면은 사용자 10번이다. `adb install --user 10`, `adb shell am start --user 10`처럼 `--user 10`을 붙인다 |
| 권한 | 차량 권한(`CAR_LIGHTS`, `CONTROL_CAR_LIGHTS`, `CAR_CLIMATE`, `CONTROL_CAR_CLIMATE`, `CAR_SEATS`, `CAR_DRIVE`, `STT_SERVICE`)은 설치하면 허용된다. 마이크 권한은 첫 음성 입력 때 묻는다 |
| 음성 입력 | Mac 마이크가 에뮬레이터에 들어가야 한다. 기본으로 켜져 있고, 안 되면 에뮬레이터 Extended controls → Microphone에서 "Virtual microphone uses host audio input"을 켠다. 키보드로도 입력할 수 있다 |
| 서버 | 없어도 앱은 끝까지 동작한다(앱 안 목업 풀이). 명식, 포인트, 궁합, LLM 풀이를 보려면 아래 "서버 켜기"대로 로컬 서버를 켠다. 앱은 `http://10.0.2.2:8787`로 찾는다 |
| 동승 궁합 | Virtual Console에서 조수석이나 뒷좌석을 "Occupied"로 바꾼 뒤 앱을 연다. `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/` → Emulation Controls → Seat Occupancy |
| 앰비언트·실내등 확인 | Virtual Console의 Ambient 화면에서 앱이 바꾼 색과 실내등 상태를 볼 수 있다 |
| 차량 연출 끄기 | 인트로 대기 화면 오른쪽 위 톱니바퀴 → 앰비언트 라이트, 실내등, 송풍을 각각 켜고 끔. 끄면 앱이 그 장치를 바꾸지 않고, 이미 바꾼 값은 바로 원래대로 돌린다. 설정은 기기에 저장된다 |
| 처음 상태로 되돌리기 | `adb shell pm clear --user 10 com.yeokma.saju` (저장된 사람과 기기 번호가 지워진다) |
| 주행 중 잠금 | Virtual Console의 Test Console에서 기어를 D로 바꾸면 앱이 잠기고, P로 돌리면 보던 화면과 그 화면의 조명·공조가 이어진다. 기어 신호가 없는 일반 안드로이드 에뮬레이터에서는 잠그지 않는다 |
| 알려진 한계 | 공조 풍량·풍향은 에뮬레이터가 지원하지 않아 실차에서만 동작한다 |

테스트용 입력 예: 이름 "임지민", 성별 "여자", 생년월일 "2000년 1월 1일", 태어난 시간 "12시", 태어난 곳 "서울", 고민 "지금 면접장에 도착했는데 긴장되네. 나 잘할 수 있을까?". LLM 키 없이 서버를 켜면 이 사람에게는 시연용 풀이가 나온다.

## 개발에 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator **v2.1.0** (Android Studio 시스템 이미지 `pleos_car_emulator`, arm64-v8a) |
| 안드로이드 | Android 14 (API 34) |
| 가상 기기(AVD) | `Pleos_Connect_v2.1.0`, Automotive, RAM 4GB, Cold boot |
| 화면 | **2560×1440**, 160dpi, 가로 |
| 앱이 그려지는 영역 | 차량 화면 오른쪽 칸, 약 1682×1206px (사용자 10번 화면) |
| 앱 설정 | `minSdk 34`, `targetSdk 34`, `compileSdk 37`, 가로 고정. 차량 화면에서 잘 보이게 UI를 1.45배로 키워 그린다 |
| 개발 PC | macOS, Apple Silicon (Pleos 에뮬레이터 이미지가 arm64라 Apple Silicon Mac에서 실행) |
| 시연 영상 녹화 | 앱을 전체 화면으로 담으려고 1920×1080 일반 안드로이드 에뮬레이터(Android API 37)에서 녹화. 동승 궁합 화면은 Pleos 에뮬레이터에서 촬영 |

## APK 파일 위치 및 GitHub Release 링크

| 항목 | 내용 |
|---|---|
| 저장소 안 위치 | [`apk/tridrive_yeokmasaju_release.apk`](apk/tridrive_yeokmasaju_release.apk) |
| GitHub Release | [최신 릴리스](https://github.com/hyndai-contest-team/yeokma-saju/releases/latest) |
| 파일 정보 | 패키지 `com.yeokma.saju`, 버전 0.1.0, 릴리스 빌드(코드 축소), 시연용 디버그 키 서명, 약 67MB |
| 직접 빌드한 경우 | `./gradlew :app:assembleRelease` → `app/app/build/outputs/apk/release/app-release.apk` |
