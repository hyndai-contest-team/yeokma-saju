-- 포인트 지갑과 원장. 기기 번호(앱이 처음 실행될 때 만든 무작위 값)로 구분한다
CREATE TABLE IF NOT EXISTS wallets (
  device TEXT PRIMARY KEY,
  balance INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0)
);
CREATE TABLE IF NOT EXISTS ledger (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device TEXT NOT NULL,
  delta INTEGER NOT NULL,
  reason TEXT NOT NULL,
  at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ledger_device ON ledger (device);
