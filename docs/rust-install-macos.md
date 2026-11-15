# Rust 설치 (macOS)

역마사주 서버(`server/`)를 켤 때만 필요합니다. 앱만 쓸 때는 설치하지 않아도 됩니다.

Apple Silicon과 Intel Mac 모두 같습니다. 터미널에서 순서대로 실행합니다.

```bash
# 1) Xcode 명령줄 도구 (C 링커가 필요합니다. 이미 있으면 "already installed"가 나옵니다)
xcode-select --install

# 2) rustup으로 Rust 설치 (공식 설치 스크립트, 기본값으로 설치)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y

# 3) 지금 열린 터미널에 경로 적용 (새 터미널은 자동으로 적용됩니다)
source "$HOME/.cargo/env"

# 4) 설치 확인
rustc --version   # rustc 1.100.0 이상
cargo --version

# 5) 서버 빌드에 필요한 것
rustup target add wasm32-unknown-unknown
cargo install worker-build --version 0.9.0 --locked
```

- Homebrew를 쓴다면 `brew install rustup` 뒤에 `rustup-init -y`로 설치해도 됩니다. Homebrew의 `rust` 패키지는 `rustup target add`를 쓸 수 없으니 `rustup`으로 설치하세요.
- Node.js가 없으면 `brew install node`로 설치합니다(`npx wrangler`에 필요).
- 나중에 Rust를 최신으로 올릴 때: `rustup update`

설치가 끝나면 [README의 2. 서버 켜기](../README.md#2-서버-켜기-선택)로 돌아갑니다.
