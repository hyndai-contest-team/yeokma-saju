# 역마사주

<img src="assets/brand/app/app-icon-512.png" width="112" height="112" alt="흰 배경의 驛馬四柱 도장 로고">

**면접, 미팅, 소개팅 앞에서 차에 남는 몇 분, 그 고민을 사주로 풀어 주는 차량용 사주 앱**

면접, 중요한 미팅, 소개팅. 떨리는 자리에 도착하면 들어가기 전 차 안에 몇 분이 남습니다. 혼자 있을 수 있는 거의 유일한 공간이지만, 마음은 가장 불안한 때입니다. 역마사주는 그 몇 분을 위한 앱입니다.

앱을 열면 숲길을 달려온 차의 문이 열리고 역마아씨가 기다립니다. "탑승하기"를 누르는 순간 내 차의 앰비언트 라이트가 무드등 색으로 바뀌고 실내등이 켜지며, 송풍이 잔잔하게 흘러 영상 속 장면이 그대로 내 차 안으로 이어집니다. 지금의 고민을 말로 털어놓으면 역마아씨가 내 사주로 풀어 주고, 풀이를 기다리는 동안 무드등은 오행의 색으로 천천히 바뀝니다. 차는 화면이 달린 공간이 아니라, 중요한 자리에 들어가기 전 마음을 다잡게 해 주는 몰입 장치가 됩니다.

Pleos Connect(안드로이드 오토모티브) 차량용 앱입니다.

## 시연 영상

[![역마사주 시연 영상: 풀이를 기다리는 동안 앰비언트가 오행 색으로 바뀌는 장면](docs/demo/demo-ambient.jpg)](docs/demo/yeokma-demo.mp4)

**[시연 영상 보기 (2분 4초, mp4)](docs/demo/yeokma-demo.mp4)**: 처음 설치한 상태에서 "임지민(2000.01.01 12시, 서울, 여자)"을 말로 입력하고, "지금 면접장에 도착했는데 긴장되네. 결과는 어떨까? 나 잘할 수 있을까?"라는 고민에 대한 풀이를 받는 흐름입니다.

- 가운데는 앱 화면을 그대로 녹화한 것이고, 둘레는 같은 시각에 앱이 차에 보낸 명령(앰비언트 색, 실내등, 송풍)을 차 실내 도식으로 그린 것입니다.
- 운전자의 목소리는 오픈소스 TTS [MeloTTS](https://github.com/myshell-ai/MeloTTS)로 만들어 에뮬레이터 마이크에 넣었습니다.
- 앱 화면을 전체 화면으로 찍기 위해 1920×1080 안드로이드 에뮬레이터에서 녹화했습니다. 그래서 이 영상의 음성 인식은 안드로이드 기본 인식기이고, Pleos 차량에서는 Pleos SpeechToText가 같은 자리를 맡습니다. 동승 궁합 화면은 좌석 센서가 있는 Pleos 에뮬레이터에서 찍었습니다.
- 풀이는 LLM 키 없이 나오는 시연용 목업 풀이입니다.

## 주요 화면

| | | |
|---|---|---|
| ![인트로](docs/screenshots/01-intro.jpg)<br>인트로 대기 | ![오셨군요](docs/screenshots/02-profiles.jpg)<br>오셨군요 | ![말로 입력](docs/screenshots/03-voice-input.jpg)<br>말로 입력 |
| ![확인과 명식](docs/screenshots/04-confirm-chart.jpg)<br>확인과 명식 | ![지금 고민](docs/screenshots/05-worry.jpg)<br>지금 고민 | ![복채](docs/screenshots/06-pay.jpg)<br>복채(포인트) |
| ![풀이 대기](docs/screenshots/07-loading.jpg)<br>풀이 대기 | ![리포트](docs/screenshots/08-report.jpg)<br>리포트(명식과 역마아씨의 예언) | ![혼자가 아니시군요](docs/screenshots/09-together.jpg)<br>동승자 감지(좌석 센서) |
| ![궁합 고르기](docs/screenshots/10-match-pick.jpg)<br>궁합: 두 사람 고르기 | ![궁합 결과](docs/screenshots/11-match-report.jpg)<br>궁합 결과 | |

## 기능

- 인트로 영상 → "탑승하기" → 저장된 사람 고르기 또는 새로 입력(이름, 성별, 생년월일, 태어난 시간, 태어난 곳, 이메일) → 확인과 명식 → 지금 고민 → 복채(포인트) → 풀이 대기 → 리포트
- 동승 궁합: 좌석 센서로 운전석 말고 앉은 사람이 있으면 "혼자가 아니시군요?"를 묻고 사주 보기와 궁합 보기로 나뉩니다. 궁합은 두 사람의 명식으로 점수(38~97)와 유형(귀인, 단짝, 동반자, 자극제, 숙제)을 계산합니다
- 차 연출: 대기 화면에서 앰비언트 라이트를 무드등 색으로, 실내등 켜기, 송풍. 탑승 영상 5초에 실내등 끄기, 6초에 붉은 앰비언트. 풀이를 기다리는 동안 무드등이 오행 색으로 돈다. 앱을 떠나면 원래 값으로 되돌린다
- 입력은 말로 합니다. Pleos SpeechToText SDK로 이름, 생년월일, "오후 1시 반" 같은 시각, 태어난 곳(전국 시·군)을 알아듣습니다
- 만세력은 서버에서 직접 계산합니다(절기, 음력·윤달, 진태양시, 한국 표준시·서머타임 이력, 대운, 십신, 지장간, 12운성, 공망, 합충)

## 폴더

| 폴더 | 내용 |
|---|---|
| `app/` | 차량 앱. Kotlin, Jetpack Compose, Media3, Pleos Vehicle SDK 2.3.2, Pleos SpeechToText SDK 3.3.1 |
| `server/` | 서버. Rust. 이 컴퓨터에서 로컬로 실행하고, 포인트는 로컬 DB에 저장합니다 |
| `data/` | 태어난 곳 목록(전국 특별시·광역시와 시·군). 앱과 서버가 같이 씁니다 |

## 준비물

| 항목 | 버전 | 필요한 때 |
|---|---|---|
| Android Studio | 2026.2 이상 (명령줄 빌드에는 Android Studio에 들어 있는 JDK를 씁니다) | 앱 |
| Pleos 에뮬레이터 | Android SDK의 `pleos_car_emulator` 이미지 v2.1.0, 가상 기기 2560×1440 | 앱 |
| Rust | 1.100 이상 (`rustup`) | 서버 |
| Node.js | 24 이상 (`npx wrangler`) | 서버 |

## 1. 앱 설치와 실행

**서버를 켜지 않아도 앱은 끝까지 동작합니다.** 서버에 연결되지 않으면 앱 안의 목업 풀이(태어난 달의 계절 기운으로 쓴 풀이)가 나오고, 복채(포인트) 화면은 건너뜁니다. 정밀 만세력, 포인트, LLM 풀이를 보려면 2번의 서버를 켭니다.

### 바로 설치 (빌드된 APK)

[Releases](https://github.com/hyndai-contest-team/yeokma-saju/releases/latest)에서 `yeokma-saju.apk`를 받아 설치합니다. 빌드 없이 바로 됩니다.

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"

# APK 받기 (비공개 레포라 gh 로그인이 필요합니다. 브라우저로 받아도 됩니다)
gh release download -R hyndai-contest-team/yeokma-saju -p yeokma-saju.apk

# 에뮬레이터를 켠 뒤 설치하고 실행합니다
$ANDROID_HOME/platform-tools/adb install -r --user 10 yeokma-saju.apk
$ANDROID_HOME/platform-tools/adb shell am start --user 10 -n com.yeokma.saju/.MainActivity
```

### 직접 빌드해서 설치

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
- 앱은 기어가 P일 때 쓰는 앱입니다. 에뮬레이터는 기본으로 P입니다.
- 음성 입력은 Pleos SpeechToText SDK(3.3.1)의 `ON_DEVICE` 모드를 씁니다. 차 안의 인식 엔진이라 앱 등록 키가 필요 없습니다. 인식 엔진이 없는 기기에서는 키보드로 입력합니다.
- 공조 풍량·풍향 제어는 에뮬레이터가 지원하지 않아 실차에서만 동작합니다.

## 2. 서버 켜기 (선택)

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

### API

| 경로 | 하는 일 |
|---|---|
| `POST /api/manse` | 만세력 계산. 무료이고 LLM을 부르지 않습니다 |
| `GET /api/wallet?device=` | 포인트 잔액 |
| `POST /api/charge` | 포인트 충전. 시연용이라 결제 인증 없이 바로 충전됩니다 (990원 1회 / 9,900원 11회 / 99,000원 115회) |
| `POST /api/reading` | 990P를 먼저 빼고 풀이를 만듭니다. 포인트가 모자라면 402를 돌려주고 LLM을 부르지 않습니다 |
| `POST /api/match` | 두 사람의 궁합. 같은 방식으로 990P를 먼저 빼고 점수와 풀이를 만듭니다 |
