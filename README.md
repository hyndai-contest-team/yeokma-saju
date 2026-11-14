# 역마사주

차를 세우면 역마아씨가 차 안을 풀이의 무대로 바꾸고, 지금 가는 길의 고민을 내 사주로 풀어 주는 **정차(P단) 전용 사주 앱**입니다. Pleos Connect(안드로이드 오토모티브) 차량용 앱입니다.

- 인트로 영상 → "탑승하기" → 저장된 사람 고르기 또는 새로 입력(이름, 성별, 생년월일, 태어난 시간, 태어난 곳, 이메일) → 확인 → 지금 고민 → 복채(포인트) → 풀이 대기 → 리포트
- 차 연출: 대기 화면에서 앰비언트 라이트를 무드등 색으로, 실내등 켜기, 송풍. 탑승 영상 5초에 실내등 끄기, 6초에 붉은 앰비언트. 풀이를 기다리는 동안 무드등이 오행 색으로 돈다. 앱을 떠나면 원래 값으로 되돌린다
- 만세력은 서버에서 직접 계산합니다(절기, 음력·윤달, 진태양시, 한국 표준시·서머타임 이력, 대운, 십신, 지장간, 12운성, 공망, 합충)
- 풀이는 **포인트를 먼저 뺀 뒤에만** LLM을 부릅니다. 실패하면 포인트를 돌려줍니다

## 폴더

| 폴더 | 내용 |
|---|---|
| `app/` | 차량 앱. Kotlin, Jetpack Compose, Media3, Pleos Vehicle SDK 2.3.2 |
| `server/` | 서버. Rust, Cloudflare Workers(`worker` 0.9), D1(포인트) |

## 준비물

| 항목 | 버전 |
|---|---|
| Android Studio | 2026.2 이상 (명령줄 빌드에는 Android Studio에 들어 있는 JDK를 씁니다) |
| Pleos 에뮬레이터 | Android SDK의 `pleos_car_emulator` 이미지 v2.1.0, 가상 기기 2560×1440 |
| Rust | 1.100 이상 (`rustup`) |
| Node.js | 24 이상 (`npx wrangler`) |

## 1. 서버 켜기

```bash
cd server

# 처음 한 번
rustup target add wasm32-unknown-unknown
cargo install worker-build --version 0.9.0 --locked
npx wrangler@4.162.0 d1 migrations apply yeokma-saju --local   # 로컬 포인트 DB 만들기

# (선택) LLM 키. 넣지 않으면 목업 풀이가 나옵니다
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

## 2. 앱 설치와 실행

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

- 앱은 서버를 `http://10.0.2.2:8787`(에뮬레이터에서 본 이 컴퓨터)로 찾습니다. 서버를 배포했다면 `app/app/build.gradle.kts`의 `API_BASE`를 배포 주소로 바꿉니다.
- 앱은 기어가 P일 때 쓰는 앱입니다. 에뮬레이터는 기본으로 P입니다.
- 음성 입력은 기기에 음성 인식기가 있을 때만 켜집니다. Pleos 에뮬레이터에는 없어서 키보드로 입력합니다.
- 공조 풍량·풍향 제어는 에뮬레이터가 지원하지 않아 실차에서만 동작합니다.

## 3. (선택) 서버 배포

```bash
cd server
npx wrangler@4.162.0 d1 create yeokma-saju          # 나온 database_id를 wrangler.toml에 넣는다
npx wrangler@4.162.0 d1 migrations apply yeokma-saju --remote
npx wrangler@4.162.0 secret put OPENROUTER_API_KEY   # 키는 secret으로만
npx wrangler@4.162.0 deploy
```

Workers 배포 권한이 있는 Cloudflare 계정이 필요합니다.

## 안내

사주 풀이는 재미로 보는 이야기입니다. 사고·건강·안전에 관한 길흉은 말하지 않습니다.
