# Email Auth And Local Room Data Domains Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build passwordless email login with Resend-backed verification, strict backend token auth, and Android Room-backed local schedule/todo data isolated by logged-in user.

**Architecture:** Keep identity, email delivery, token auth, local persistence, and UI state as separate units. Backend routes call `AuthService`, which calls `AuthStore` and `EmailSender`; agent/asr routes only receive authenticated `UserContext`. Android `MainActivity` wires dependencies, while UI receives repositories/callbacks and never talks directly to SQLite or token internals.

**Tech Stack:** FastAPI, Python stdlib SQLite, Resend HTTP API, Redis job queue, Android Compose, Room, Kotlin coroutines/test infrastructure already present in Gradle plus Room/KSP additions.

---

## File Structure And Boundaries

Backend:

- Create `backend/fucktheddl_agent/auth_store.py`: SQLite schema, migrations, users, verification codes, token hashes.
- Create `backend/fucktheddl_agent/email_sender.py`: `EmailSender` protocol, `ResendEmailSender`, `FakeEmailSender`.
- Rewrite `backend/fucktheddl_agent/auth.py`: `AuthService`, code request/verify/logout/authenticate.
- Modify `backend/fucktheddl_agent/schemas.py`: add `AuthCodeRequest`, `AuthCodeVerifyRequest`, `AuthCodeVerifyResponse`, `LogoutResponse`.
- Modify `backend/fucktheddl_agent/config.py`: add Resend and auth DB configuration.
- Modify `backend/fucktheddl_agent/api.py`: expose new auth routes and enforce auth on protected routes.
- Test `backend/tests/test_auth_flow.py` and extend `backend/tests/test_agent_api.py`.

Android:

- Modify `settings.gradle.kts` and `app/build.gradle.kts`: add Room and KSP.
- Create `app/src/main/java/com/zpc/fucktheddl/auth/AuthModels.kt`: login/session models.
- Create `app/src/main/java/com/zpc/fucktheddl/auth/AuthSessionStore.kt`: SharedPreferences for email/user/token.
- Create `app/src/main/java/com/zpc/fucktheddl/auth/AuthRepository.kt`: request code, verify code, logout.
- Create `app/src/main/java/com/zpc/fucktheddl/commitments/CommitmentRepository.kt`: interface used by app/UI.
- Create `app/src/main/java/com/zpc/fucktheddl/commitments/room/CommitmentDatabase.kt`: Room database/entities/DAO.
- Create `app/src/main/java/com/zpc/fucktheddl/commitments/room/RoomCommitmentRepository.kt`: current-user-scoped local data source.
- Keep `app/src/main/java/com/zpc/fucktheddl/agent/LocalCommitmentStore.kt` only until Room replacement compiles, then delete it in the final cleanup task.
- Modify `app/src/main/java/com/zpc/fucktheddl/agent/AgentApiClient.kt`: new auth methods and remove old device register method.
- Modify `app/src/main/java/com/zpc/fucktheddl/MainActivity.kt`: login gate and dependency wiring.
- Modify `app/src/main/java/com/zpc/fucktheddl/ui/FuckTheDdlApp.kt`: remove registration settings flow and accept logged-in session/logout callbacks.
- Create tests under `app/src/test/java/com/zpc/fucktheddl/auth/` and `app/src/test/java/com/zpc/fucktheddl/commitments/`.

Decoupling rule: no UI file may import Room, SQLite, Resend, token hash logic, or backend auth store classes. No backend agent workflow file may import SQLite auth store or Resend directly.

---

## Task 1: Backend Auth SQLite Store

**Files:**
- Create: `backend/fucktheddl_agent/auth_store.py`
- Test: `backend/tests/test_auth_store.py`

- [ ] **Step 1: Write failing auth store tests**

Create `backend/tests/test_auth_store.py`:

```python
from datetime import datetime, timedelta, timezone

from fucktheddl_agent.auth_store import AuthStore


def test_user_is_created_once_per_email(tmp_path):
    store = AuthStore(tmp_path / "auth.sqlite3")

    first = store.get_or_create_user("USER@example.com")
    second = store.get_or_create_user("user@example.com")

    assert first.user_id == second.user_id
    assert first.email == "user@example.com"
    assert second.email == "user@example.com"


def test_verification_code_hash_is_stored_without_plaintext(tmp_path):
    store = AuthStore(tmp_path / "auth.sqlite3")
    expires_at = datetime.now(timezone.utc) + timedelta(minutes=5)

    code_id = store.create_verification_code(
        email="user@example.com",
        code_hash="hash-123",
        expires_at=expires_at,
    )
    record = store.latest_verification_code("user@example.com")

    assert code_id
    assert record is not None
    assert record.code_hash == "hash-123"
    assert record.code_hash != "123456"
    assert record.attempts == 0
    assert record.consumed_at is None


def test_access_token_hash_authenticates_user(tmp_path):
    store = AuthStore(tmp_path / "auth.sqlite3")
    user = store.get_or_create_user("user@example.com")
    store.create_access_token(user.user_id, token_hash="token-hash")

    assert store.user_id_for_token_hash("token-hash") == user.user_id
    assert store.user_id_for_token_hash("wrong") is None
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
.venv/bin/pytest backend/tests/test_auth_store.py -q
```

Expected: fails with `ModuleNotFoundError: No module named 'fucktheddl_agent.auth_store'`.

- [ ] **Step 3: Implement `AuthStore`**

Create `backend/fucktheddl_agent/auth_store.py` with:

```python
from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4


@dataclass(frozen=True)
class StoredUser:
    user_id: str
    email: str


@dataclass(frozen=True)
class StoredVerificationCode:
    id: str
    email: str
    code_hash: str
    expires_at: datetime
    attempts: int
    locked_until: datetime | None
    consumed_at: datetime | None
    created_at: datetime


class AuthStore:
    def __init__(self, path: Path):
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._migrate()

    def get_or_create_user(self, email: str) -> StoredUser:
        normalized = email.strip().lower()
        now = _now()
        with self._connect() as db:
            row = db.execute("SELECT id, email FROM users WHERE email = ?", (normalized,)).fetchone()
            if row is not None:
                return StoredUser(user_id=row["id"], email=row["email"])
            user_id = f"usr_{uuid4().hex}"
            db.execute(
                "INSERT INTO users (id, email, created_at, updated_at) VALUES (?, ?, ?, ?)",
                (user_id, normalized, _iso(now), _iso(now)),
            )
            return StoredUser(user_id=user_id, email=normalized)

    def create_verification_code(self, email: str, code_hash: str, expires_at: datetime) -> str:
        normalized = email.strip().lower()
        code_id = f"vc_{uuid4().hex}"
        now = _now()
        with self._connect() as db:
            db.execute(
                """
                INSERT INTO email_verification_codes
                (id, email, code_hash, expires_at, attempts, locked_until, consumed_at, created_at)
                VALUES (?, ?, ?, ?, 0, NULL, NULL, ?)
                """,
                (code_id, normalized, code_hash, _iso(expires_at), _iso(now)),
            )
        return code_id

    def latest_verification_code(self, email: str) -> StoredVerificationCode | None:
        normalized = email.strip().lower()
        with self._connect() as db:
            row = db.execute(
                """
                SELECT id, email, code_hash, expires_at, attempts, locked_until, consumed_at, created_at
                FROM email_verification_codes
                WHERE email = ?
                ORDER BY created_at DESC
                LIMIT 1
                """,
                (normalized,),
            ).fetchone()
        return _verification_from_row(row) if row is not None else None

    def increment_attempts(self, code_id: str, locked_until: datetime | None) -> None:
        with self._connect() as db:
            db.execute(
                """
                UPDATE email_verification_codes
                SET attempts = attempts + 1, locked_until = ?
                WHERE id = ?
                """,
                (_iso(locked_until) if locked_until else None, code_id),
            )

    def consume_code(self, code_id: str) -> None:
        with self._connect() as db:
            db.execute(
                "UPDATE email_verification_codes SET consumed_at = ? WHERE id = ?",
                (_iso(_now()), code_id),
            )

    def create_access_token(self, user_id: str, token_hash: str) -> str:
        token_id = f"tok_{uuid4().hex}"
        now = _now()
        with self._connect() as db:
            db.execute(
                """
                INSERT INTO access_tokens (id, user_id, token_hash, created_at, revoked_at, last_used_at)
                VALUES (?, ?, ?, ?, NULL, NULL)
                """,
                (token_id, user_id, token_hash, _iso(now)),
            )
        return token_id

    def user_id_for_token_hash(self, token_hash: str) -> str | None:
        now = _now()
        with self._connect() as db:
            row = db.execute(
                """
                SELECT user_id
                FROM access_tokens
                WHERE token_hash = ? AND revoked_at IS NULL
                """,
                (token_hash,),
            ).fetchone()
            if row is None:
                return None
            db.execute(
                "UPDATE access_tokens SET last_used_at = ? WHERE token_hash = ?",
                (_iso(now), token_hash),
            )
            return row["user_id"]

    def revoke_token_hash(self, token_hash: str) -> bool:
        with self._connect() as db:
            cursor = db.execute(
                "UPDATE access_tokens SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL",
                (_iso(_now()), token_hash),
            )
            return cursor.rowcount > 0

    def _connect(self) -> sqlite3.Connection:
        db = sqlite3.connect(self.path)
        db.row_factory = sqlite3.Row
        return db

    def _migrate(self) -> None:
        with self._connect() as db:
            db.executescript(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    email TEXT UNIQUE NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS email_verification_codes (
                    id TEXT PRIMARY KEY,
                    email TEXT NOT NULL,
                    code_hash TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    locked_until TEXT,
                    consumed_at TEXT,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_email_codes_email_created
                    ON email_verification_codes(email, created_at);
                CREATE TABLE IF NOT EXISTS access_tokens (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(id),
                    token_hash TEXT UNIQUE NOT NULL,
                    created_at TEXT NOT NULL,
                    revoked_at TEXT,
                    last_used_at TEXT
                );
                CREATE INDEX IF NOT EXISTS idx_access_tokens_hash
                    ON access_tokens(token_hash);
                """
            )


def _verification_from_row(row: sqlite3.Row) -> StoredVerificationCode:
    return StoredVerificationCode(
        id=row["id"],
        email=row["email"],
        code_hash=row["code_hash"],
        expires_at=_parse(row["expires_at"]),
        attempts=row["attempts"],
        locked_until=_parse_optional(row["locked_until"]),
        consumed_at=_parse_optional(row["consumed_at"]),
        created_at=_parse(row["created_at"]),
    )


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat()


def _parse(value: str) -> datetime:
    return datetime.fromisoformat(value)


def _parse_optional(value: str | None) -> datetime | None:
    return datetime.fromisoformat(value) if value else None
```

- [ ] **Step 4: Run auth store tests**

Run:

```bash
.venv/bin/pytest backend/tests/test_auth_store.py -q
```

Expected: `3 passed`.

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/fucktheddl_agent/auth_store.py backend/tests/test_auth_store.py
git commit -m "Persist email identity auth state in SQLite" \
  -m "Auth needs a durable store before email verification and token enforcement can be split from the agent routes. The store owns schema creation, hashed verification codes, users, and token hashes without importing FastAPI or email delivery." \
  -m "Constraint: Backend deployment remains single-server SQLite for this phase." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: .venv/bin/pytest backend/tests/test_auth_store.py -q"
```

---

## Task 2: Backend Resend Email Sender Boundary

**Files:**
- Create: `backend/fucktheddl_agent/email_sender.py`
- Test: `backend/tests/test_email_sender.py`

- [ ] **Step 1: Write failing email sender tests**

Create `backend/tests/test_email_sender.py`:

```python
from fucktheddl_agent.email_sender import FakeEmailSender, LoginCodeEmail


def test_fake_sender_records_login_code_email():
    sender = FakeEmailSender()

    sender.send_login_code(
        LoginCodeEmail(
            to_email="user@example.com",
            code="123456",
            expires_minutes=5,
            product_name="DDL Agent",
        )
    )

    assert len(sender.sent) == 1
    assert sender.sent[0].to_email == "user@example.com"
    assert sender.sent[0].code == "123456"
    assert sender.sent[0].product_name == "DDL Agent"
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
.venv/bin/pytest backend/tests/test_email_sender.py -q
```

Expected: fails because `email_sender.py` does not exist.

- [ ] **Step 3: Implement sender protocol, fake sender, and Resend sender**

Create `backend/fucktheddl_agent/email_sender.py`:

```python
from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Protocol
from urllib import request


@dataclass(frozen=True)
class LoginCodeEmail:
    to_email: str
    code: str
    expires_minutes: int
    product_name: str


class EmailSender(Protocol):
    def send_login_code(self, email: LoginCodeEmail) -> None:
        ...


class FakeEmailSender:
    def __init__(self) -> None:
        self.sent: list[LoginCodeEmail] = []

    def send_login_code(self, email: LoginCodeEmail) -> None:
        self.sent.append(email)


class ResendEmailSender:
    def __init__(self, api_key: str, from_email: str, from_name: str = "DDL Agent") -> None:
        self.api_key = api_key
        self.from_email = from_email
        self.from_name = from_name or "DDL Agent"

    def send_login_code(self, email: LoginCodeEmail) -> None:
        payload = {
            "from": f"{self.from_name} <{self.from_email}>",
            "to": [email.to_email],
            "subject": f"你的 {email.product_name} 登录验证码",
            "html": (
                f"<p>你的登录验证码是：</p>"
                f"<p style='font-size:24px;font-weight:700;letter-spacing:4px'>{email.code}</p>"
                f"<p>验证码 {email.expires_minutes} 分钟内有效。如果不是你本人操作，可以忽略这封邮件。</p>"
            ),
            "text": f"你的 {email.product_name} 登录验证码是 {email.code}，{email.expires_minutes} 分钟内有效。",
        }
        body = json.dumps(payload).encode("utf-8")
        req = request.Request(
            "https://api.resend.com/emails",
            data=body,
            method="POST",
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
        )
        with request.urlopen(req, timeout=10) as response:
            if response.status < 200 or response.status >= 300:
                raise RuntimeError(f"Resend email failed with HTTP {response.status}")
```

- [ ] **Step 4: Run email sender tests**

Run:

```bash
.venv/bin/pytest backend/tests/test_email_sender.py -q
```

Expected: `1 passed`.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/fucktheddl_agent/email_sender.py backend/tests/test_email_sender.py
git commit -m "Isolate login email delivery behind a sender boundary" \
  -m "Email delivery should be replaceable in tests and deployment. The Resend implementation is isolated from auth logic, and tests use a fake sender without network calls." \
  -m "Constraint: Production email provider is Resend." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: .venv/bin/pytest backend/tests/test_email_sender.py -q"
```

---

## Task 3: Backend Auth Service And Schemas

**Files:**
- Modify: `backend/fucktheddl_agent/auth.py`
- Modify: `backend/fucktheddl_agent/schemas.py`
- Test: `backend/tests/test_auth_service.py`

- [ ] **Step 1: Write auth service tests**

Create `backend/tests/test_auth_service.py`:

```python
from datetime import datetime, timedelta, timezone

import pytest
from fastapi import HTTPException

from fucktheddl_agent.auth import AuthService
from fucktheddl_agent.auth_store import AuthStore
from fucktheddl_agent.email_sender import FakeEmailSender


def make_service(tmp_path):
    sender = FakeEmailSender()
    service = AuthService(
        store=AuthStore(tmp_path / "auth.sqlite3"),
        email_sender=sender,
        product_name="DDL Agent",
    )
    return service, sender


def test_request_code_sends_six_digit_email(tmp_path):
    service, sender = make_service(tmp_path)

    service.request_code("USER@example.com")

    assert len(sender.sent) == 1
    assert sender.sent[0].to_email == "user@example.com"
    assert sender.sent[0].code.isdigit()
    assert len(sender.sent[0].code) == 6


def test_repeat_code_request_within_cooldown_is_rejected(tmp_path):
    service, _sender = make_service(tmp_path)

    service.request_code("user@example.com")

    with pytest.raises(HTTPException) as error:
        service.request_code("user@example.com")

    assert error.value.status_code == 429


def test_wrong_code_locks_after_three_attempts(tmp_path):
    service, _sender = make_service(tmp_path)
    service.request_code("user@example.com")

    for _ in range(3):
        with pytest.raises(HTTPException):
            service.verify_code("user@example.com", "000000")

    with pytest.raises(HTTPException) as error:
        service.verify_code("user@example.com", "000000")

    assert error.value.status_code == 423


def test_verify_code_creates_user_and_token(tmp_path):
    service, sender = make_service(tmp_path)
    service.request_code("USER@example.com")

    result = service.verify_code("user@example.com", sender.sent[0].code)

    assert result.email == "user@example.com"
    assert result.user_id.startswith("usr_")
    assert result.access_token
    assert result.newly_created is True
    assert service.authenticate_token(result.access_token).user_id == result.user_id


def test_logout_revokes_token(tmp_path):
    service, sender = make_service(tmp_path)
    service.request_code("user@example.com")
    result = service.verify_code("user@example.com", sender.sent[0].code)

    assert service.logout(result.access_token) is True

    with pytest.raises(HTTPException) as error:
        service.authenticate_token(result.access_token)

    assert error.value.status_code == 401
```

- [ ] **Step 2: Add schema classes**

Modify `backend/fucktheddl_agent/schemas.py` by replacing old register schemas with:

```python
class AuthCodeRequest(BaseModel):
    email: str = Field(min_length=3)


class AuthCodeVerifyRequest(BaseModel):
    email: str = Field(min_length=3)
    code: str = Field(min_length=6, max_length=6)


class AuthCodeVerifyResponse(BaseModel):
    user_id: str
    email: str
    access_token: str
    newly_created: bool


class LogoutResponse(BaseModel):
    status: Literal["logged_out"]
```

- [ ] **Step 3: Implement `AuthService`**

Rewrite `backend/fucktheddl_agent/auth.py` so it owns FastAPI-facing auth behavior but not SQLite details:

```python
from __future__ import annotations

import hashlib
import hmac
import secrets
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from fastapi import HTTPException, Request, status

from fucktheddl_agent.auth_store import AuthStore
from fucktheddl_agent.email_sender import EmailSender, LoginCodeEmail


@dataclass(frozen=True)
class UserContext:
    user_id: str
    token: str | None = None


@dataclass(frozen=True)
class VerifiedLogin:
    user_id: str
    email: str
    access_token: str
    newly_created: bool


class AuthService:
    def __init__(
        self,
        store: AuthStore,
        email_sender: EmailSender,
        product_name: str = "DDL Agent",
        now_provider=None,
    ) -> None:
        self.store = store
        self.email_sender = email_sender
        self.product_name = product_name
        self.now_provider = now_provider or (lambda: datetime.now(timezone.utc))

    def request_code(self, email: str) -> None:
        normalized = _normalize_email(email)
        now = self.now_provider()
        latest = self.store.latest_verification_code(normalized)
        if latest and latest.created_at > now - timedelta(seconds=60):
            raise HTTPException(status_code=429, detail="Verification code was requested too recently")
        code = f"{secrets.randbelow(1_000_000):06d}"
        self.store.create_verification_code(
            email=normalized,
            code_hash=_hash_secret(code),
            expires_at=now + timedelta(minutes=5),
        )
        self.email_sender.send_login_code(
            LoginCodeEmail(
                to_email=normalized,
                code=code,
                expires_minutes=5,
                product_name=self.product_name,
            )
        )

    def verify_code(self, email: str, code: str) -> VerifiedLogin:
        normalized = _normalize_email(email)
        now = self.now_provider()
        record = self.store.latest_verification_code(normalized)
        if record is None or record.consumed_at is not None or record.expires_at <= now:
            raise HTTPException(status_code=401, detail="Invalid or expired verification code")
        if record.locked_until is not None and record.locked_until > now:
            raise HTTPException(status_code=423, detail="Verification is temporarily locked")
        if not hmac.compare_digest(record.code_hash, _hash_secret(code)):
            attempts_after = record.attempts + 1
            locked_until = now + timedelta(minutes=5) if attempts_after >= 3 else None
            self.store.increment_attempts(record.id, locked_until)
            raise HTTPException(
                status_code=423 if locked_until else 401,
                detail="Verification is temporarily locked" if locked_until else "Invalid verification code",
            )
        self.store.consume_code(record.id)
        existed = self.store.get_or_create_user(normalized)
        access_token = secrets.token_urlsafe(32)
        self.store.create_access_token(existed.user_id, _hash_secret(access_token))
        return VerifiedLogin(
            user_id=existed.user_id,
            email=existed.email,
            access_token=access_token,
            newly_created=record.created_at == record.created_at and True,
        )

    def authenticate_token(self, token: str) -> UserContext:
        token_hash = _hash_secret(token)
        user_id = self.store.user_id_for_token_hash(token_hash)
        if not user_id:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid access token")
        return UserContext(user_id=user_id, token=token)

    def logout(self, token: str) -> bool:
        return self.store.revoke_token_hash(_hash_secret(token))


def authenticate_request(request: Request, auth_service: AuthService) -> UserContext:
    token = _extract_token(request)
    if not token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing access token")
    return auth_service.authenticate_token(token)


def _extract_token(request: Request) -> str:
    authorization = request.headers.get("authorization", "")
    if authorization.lower().startswith("bearer "):
        return authorization[7:].strip()
    return request.headers.get("x-agent-token", "").strip()


def _normalize_email(email: str) -> str:
    normalized = email.strip().lower()
    if "@" not in normalized or "." not in normalized.rsplit("@", 1)[-1]:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Valid email is required")
    return normalized


def _hash_secret(secret: str) -> str:
    return hashlib.sha256(secret.encode("utf-8")).hexdigest()
```

Then fix `newly_created` by adding `find_user_by_email` to `AuthStore` or comparing before `get_or_create_user`. Prefer adding `find_user_by_email(email)` to `AuthStore` and setting:

```python
existing = self.store.find_user_by_email(normalized)
user = existing or self.store.get_or_create_user(normalized)
newly_created = existing is None
```

- [ ] **Step 4: Run service tests**

Run:

```bash
.venv/bin/pytest backend/tests/test_auth_store.py backend/tests/test_auth_service.py -q
```

Expected: all tests pass.

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/fucktheddl_agent/auth.py backend/fucktheddl_agent/auth_store.py backend/fucktheddl_agent/schemas.py backend/tests/test_auth_service.py
git commit -m "Add passwordless email auth service" \
  -m "The auth service coordinates code generation, email delivery, code verification, token issuance, and logout while keeping persistence and email provider details behind focused boundaries." \
  -m "Constraint: Codes are six digits, expire after five minutes, and lock after three wrong attempts." \
  -m "Rejected: Keep device registration endpoint | email verification is the selected identity model." \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: .venv/bin/pytest backend/tests/test_auth_store.py backend/tests/test_auth_service.py -q"
```

---

## Task 4: Backend API Integration And Strict Auth

**Files:**
- Modify: `backend/fucktheddl_agent/config.py`
- Modify: `backend/fucktheddl_agent/api.py`
- Modify: `backend/tests/test_agent_api.py`

- [ ] **Step 1: Write API tests for public/protected boundaries**

Extend `backend/tests/test_agent_api.py` with:

```python
def test_auth_code_routes_are_public_and_agent_requires_token(tmp_path, monkeypatch):
    monkeypatch.setenv("RESEND_API_KEY", "test-key")
    monkeypatch.setenv("RESEND_FROM_EMAIL", "noreply@example.com")
    app = create_app(tmp_path)
    client = TestClient(app)

    code_response = client.post("/auth/code/request", json={"email": "user@example.com"})
    assert code_response.status_code in (200, 204)

    agent_response = client.post(
        "/agent/propose",
        json={"text": "明天上午九点开会", "session_id": "s1", "commitments": {"events": [], "todos": []}},
    )
    assert agent_response.status_code == 401


def test_verified_email_token_can_call_protected_routes(tmp_path, monkeypatch):
    monkeypatch.setenv("RESEND_API_KEY", "test-key")
    monkeypatch.setenv("RESEND_FROM_EMAIL", "noreply@example.com")
    fake_queue = ImmediateJobQueue()
    app = create_app(tmp_path, job_queue=fake_queue)
    client = TestClient(app)

    client.post("/auth/code/request", json={"email": "user@example.com"})
    sender = app.state.email_sender
    code = sender.sent[0].code
    login = client.post("/auth/code/verify", json={"email": "user@example.com", "code": code})

    assert login.status_code == 200
    token = login.json()["access_token"]
    protected = client.post(
        "/agent/propose",
        headers={"Authorization": f"Bearer {token}"},
        json={"text": "明天上午九点开会", "session_id": "s1", "commitments": {"events": [], "todos": []}},
    )
    assert protected.status_code == 202
```

If `ImmediateJobQueue` does not exist, create a small test double inside the test file with `submit`, `get`, `bind_processor`, `start`, and `shutdown`.

- [ ] **Step 2: Add Resend config**

Modify `backend/fucktheddl_agent/config.py`:

```python
@dataclass(frozen=True)
class EmailSettings:
    resend_api_key: str
    resend_from_email: str
    resend_from_name: str = "DDL Agent"

    @property
    def configured(self) -> bool:
        return bool(self.resend_api_key and self.resend_from_email)
```

Add `email: EmailSettings` to the root settings dataclass and load:

```python
email = EmailSettings(
    resend_api_key=os.environ.get("RESEND_API_KEY", ""),
    resend_from_email=os.environ.get("RESEND_FROM_EMAIL", ""),
    resend_from_name=os.environ.get("RESEND_FROM_NAME", "DDL Agent"),
)
```

- [ ] **Step 3: Wire API routes**

Modify `backend/fucktheddl_agent/api.py`:

```python
from fucktheddl_agent.auth import AuthService, UserContext, authenticate_request
from fucktheddl_agent.auth_store import AuthStore
from fucktheddl_agent.email_sender import FakeEmailSender, ResendEmailSender
```

Inside `create_app`:

```python
auth_store = AuthStore(settings.data_root / ".runtime" / "auth.sqlite3")
email_sender = (
    ResendEmailSender(
        api_key=settings.email.resend_api_key,
        from_email=settings.email.resend_from_email,
        from_name=settings.email.resend_from_name,
    )
    if settings.email.configured
    else FakeEmailSender()
)
auth_service = AuthService(auth_store, email_sender, product_name="DDL Agent")
app.state.email_sender = email_sender

def current_user(request: Request) -> UserContext:
    return authenticate_request(request, auth_service)
```

Replace `/auth/register` with:

```python
@router.post("/auth/code/request", status_code=status.HTTP_204_NO_CONTENT)
def request_auth_code(request: AuthCodeRequest) -> Response:
    auth_service.request_code(request.email)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/auth/code/verify", response_model=AuthCodeVerifyResponse)
def verify_auth_code(request: AuthCodeVerifyRequest) -> AuthCodeVerifyResponse:
    result = auth_service.verify_code(request.email, request.code)
    return AuthCodeVerifyResponse(
        user_id=result.user_id,
        email=result.email,
        access_token=result.access_token,
        newly_created=result.newly_created,
    )


@router.post("/auth/logout", response_model=LogoutResponse)
def logout(request: Request, user: UserContext = Depends(current_user)) -> LogoutResponse:
    if user.token:
        auth_service.logout(user.token)
    return LogoutResponse(status="logged_out")
```

- [ ] **Step 4: Run backend tests**

Run:

```bash
.venv/bin/pytest backend/tests -q
```

Expected: all backend tests pass. Update or delete old `/auth/register` tests because that endpoint is no longer part of the chosen design.

- [ ] **Step 5: Commit Task 4**

```bash
git add backend/fucktheddl_agent/api.py backend/fucktheddl_agent/config.py backend/tests/test_agent_api.py
git commit -m "Route backend authentication through email verification" \
  -m "FastAPI now exposes code request and verification endpoints, hides Resend behind a sender boundary, and requires authenticated users for agent and ASR routes." \
  -m "Constraint: /health and auth code endpoints remain public; all other service routes require a token." \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: .venv/bin/pytest backend/tests -q"
```

---

## Task 5: Android Auth API And Session Store

**Files:**
- Create: `app/src/main/java/com/zpc/fucktheddl/auth/AuthModels.kt`
- Create: `app/src/main/java/com/zpc/fucktheddl/auth/AuthSessionStore.kt`
- Create: `app/src/main/java/com/zpc/fucktheddl/auth/AuthRepository.kt`
- Modify: `app/src/main/java/com/zpc/fucktheddl/agent/AgentApiClient.kt`
- Test: `app/src/test/java/com/zpc/fucktheddl/auth/AuthRepositoryTest.kt`

- [ ] **Step 1: Write AuthRepository HTTP tests**

Create `app/src/test/java/com/zpc/fucktheddl/auth/AuthRepositoryTest.kt`:

```kotlin
package com.zpc.fucktheddl.auth

import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentApiConfig
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetSocketAddress

class AuthRepositoryTest {
    @Test
    fun requestCodePostsEmail() {
        var receivedBody = ""
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/auth/code/request") { exchange ->
            receivedBody = exchange.requestBody.bufferedReader().readText()
            exchange.sendResponseHeaders(204, -1)
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}/"
            val result = AgentApiClient(AgentApiConfig(baseUrl)).requestLoginCode("user@example.com")

            assertNull(result)
            assertEquals(true, receivedBody.contains("\"email\":\"user@example.com\""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun verifyCodeParsesSession() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/auth/code/verify") { exchange ->
            val response = """
                {
                  "user_id": "usr_1",
                  "email": "user@example.com",
                  "access_token": "token-1",
                  "newly_created": true
                }
            """.trimIndent()
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}/"
            val result = AgentApiClient(AgentApiConfig(baseUrl)).verifyLoginCode("user@example.com", "123456")

            assertEquals("usr_1", result.userId)
            assertEquals("user@example.com", result.email)
            assertEquals("token-1", result.accessToken)
        } finally {
            server.stop(0)
        }
    }
}
```

- [ ] **Step 2: Create auth models**

Create `app/src/main/java/com/zpc/fucktheddl/auth/AuthModels.kt`:

```kotlin
package com.zpc.fucktheddl.auth

data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
) {
    val isLoggedIn: Boolean = userId.isNotBlank() && email.isNotBlank() && accessToken.isNotBlank()
}

data class LoginCodeVerifyResult(
    val userId: String = "",
    val email: String = "",
    val accessToken: String = "",
    val newlyCreated: Boolean = false,
    val error: String? = null,
)
```

- [ ] **Step 3: Create session store**

Create `app/src/main/java/com/zpc/fucktheddl/auth/AuthSessionStore.kt`:

```kotlin
package com.zpc.fucktheddl.auth

import android.content.Context

class AuthSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    fun load(): AuthSession {
        return AuthSession(
            userId = preferences.getString("user_id", "").orEmpty(),
            email = preferences.getString("email", "").orEmpty(),
            accessToken = preferences.getString("access_token", "").orEmpty(),
        )
    }

    fun save(session: AuthSession) {
        preferences.edit()
            .putString("user_id", session.userId)
            .putString("email", session.email)
            .putString("access_token", session.accessToken)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
```

- [ ] **Step 4: Add AgentApiClient auth methods**

Modify `app/src/main/java/com/zpc/fucktheddl/agent/AgentApiClient.kt`:

```kotlin
fun requestLoginCode(email: String): String? {
    return try {
        postJsonAllowEmpty(
            "auth/code/request",
            JSONObject().put("email", email.trim()),
        )
        null
    } catch (error: Exception) {
        error.message ?: "验证码发送失败"
    }
}

fun verifyLoginCode(email: String, code: String): LoginCodeVerifyResult {
    return try {
        val response = postJson(
            "auth/code/verify",
            JSONObject()
                .put("email", email.trim())
                .put("code", code.trim()),
        )
        LoginCodeVerifyResult(
            userId = response.optString("user_id", ""),
            email = response.optString("email", ""),
            accessToken = response.optString("access_token", ""),
            newlyCreated = response.optBoolean("newly_created", false),
            error = null,
        )
    } catch (error: Exception) {
        LoginCodeVerifyResult(error = error.message ?: "登录失败")
    }
}
```

Add import:

```kotlin
import com.zpc.fucktheddl.auth.LoginCodeVerifyResult
```

Add helper:

```kotlin
private fun postJsonAllowEmpty(path: String, body: JSONObject) {
    val connection = URL(config.normalizedBaseUrl + path).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.connectTimeout = 5000
    connection.readTimeout = 20000
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    connection.applyAuth()
    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
        writer.write(body.toString())
    }
    val text = connection.responseText()
    if (connection.responseCode !in 200..299) {
        error(text)
    }
}
```

- [ ] **Step 5: Create AuthRepository wrapper**

Create `app/src/main/java/com/zpc/fucktheddl/auth/AuthRepository.kt`:

```kotlin
package com.zpc.fucktheddl.auth

import com.zpc.fucktheddl.agent.AgentApiClient

class AuthRepository(
    private val client: AgentApiClient,
) {
    fun requestCode(email: String): String? {
        return client.requestLoginCode(email)
    }

    fun verifyCode(email: String, code: String): LoginCodeVerifyResult {
        return client.verifyLoginCode(email, code)
    }
}
```

- [ ] **Step 6: Run Android auth tests**

Run:

```bash
./gradlew testDebugUnitTest --tests com.zpc.fucktheddl.auth.AuthRepositoryTest
```

Expected: tests pass.

- [ ] **Step 7: Commit Task 5**

```bash
git add app/src/main/java/com/zpc/fucktheddl/auth app/src/main/java/com/zpc/fucktheddl/agent/AgentApiClient.kt app/src/test/java/com/zpc/fucktheddl/auth/AuthRepositoryTest.kt
git commit -m "Add Android email code auth client boundary" \
  -m "Android auth state is separated from connection settings and AgentApiClient exposes explicit code request and verify operations for the passwordless login flow." \
  -m "Constraint: Token remains hidden and stored locally in SharedPreferences for this phase." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: ./gradlew testDebugUnitTest --tests com.zpc.fucktheddl.auth.AuthRepositoryTest"
```

---

## Task 6: Android Room Commitment Repository

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/zpc/fucktheddl/commitments/CommitmentRepository.kt`
- Create: `app/src/main/java/com/zpc/fucktheddl/commitments/room/CommitmentDatabase.kt`
- Create: `app/src/main/java/com/zpc/fucktheddl/commitments/room/RoomCommitmentRepository.kt`
- Test: `app/src/test/java/com/zpc/fucktheddl/commitments/RoomCommitmentRepositoryTest.kt`

- [ ] **Step 1: Add Room dependencies**

Modify `settings.gradle.kts` plugin block:

```kotlin
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.21-2.0.4" apply false
}
```

Modify `app/build.gradle.kts` plugins:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
```

Add dependencies:

```kotlin
implementation("androidx.room:room-runtime:2.9.0")
implementation("androidx.room:room-ktx:2.9.0")
ksp("androidx.room:room-compiler:2.9.0")
testImplementation("androidx.room:room-testing:2.9.0")
testImplementation("androidx.test:core:1.7.0")
```

- [ ] **Step 2: Create repository interface**

Create `app/src/main/java/com/zpc/fucktheddl/commitments/CommitmentRepository.kt`:

```kotlin
package com.zpc.fucktheddl.commitments

import com.zpc.fucktheddl.agent.AgentApplyResult
import com.zpc.fucktheddl.agent.AgentCommitmentsPayload
import com.zpc.fucktheddl.agent.AgentProposal

interface CommitmentRepository {
    fun listCommitments(ownerUserId: String): AgentCommitmentsPayload
    fun applyProposal(ownerUserId: String, proposal: AgentProposal): AgentApplyResult
    fun deleteCommitment(ownerUserId: String, commitmentId: String): AgentApplyResult
}
```

- [ ] **Step 3: Create Room database**

Create `app/src/main/java/com/zpc/fucktheddl/commitments/room/CommitmentDatabase.kt` with `ScheduleEntity`, `TodoEntity`, `CommitmentDao`, and `CommitmentDatabase`. Entities must include `ownerUserId`.

Use these table definitions:

```kotlin
@Entity(
    tableName = "schedules",
    indices = [Index(value = ["ownerUserId", "startAt"])]
)
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val title: String,
    val startAt: String,
    val endAt: String,
    val timezone: String,
    val location: String,
    val notes: String,
    val tags: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)
```

Use equivalent `TodoEntity` with `due`, `priority`, and index `["ownerUserId", "due"]`.

DAO methods:

```kotlin
@Query("SELECT * FROM schedules WHERE ownerUserId = :ownerUserId AND status = 'confirmed' ORDER BY startAt")
fun listSchedules(ownerUserId: String): List<ScheduleEntity>

@Query("SELECT * FROM todos WHERE ownerUserId = :ownerUserId AND status IN ('active', 'done') ORDER BY due, title")
fun listTodos(ownerUserId: String): List<TodoEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
fun upsertSchedule(entity: ScheduleEntity)

@Insert(onConflict = OnConflictStrategy.REPLACE)
fun upsertTodo(entity: TodoEntity)

@Query("UPDATE schedules SET status = 'cancelled', updatedAt = :updatedAt WHERE ownerUserId = :ownerUserId AND id = :id")
fun cancelSchedule(ownerUserId: String, id: String, updatedAt: String): Int

@Query("UPDATE todos SET status = 'cancelled', updatedAt = :updatedAt WHERE ownerUserId = :ownerUserId AND id = :id")
fun cancelTodo(ownerUserId: String, id: String, updatedAt: String): Int
```

- [ ] **Step 4: Implement RoomCommitmentRepository**

Create `app/src/main/java/com/zpc/fucktheddl/commitments/room/RoomCommitmentRepository.kt`. Port the proposal application logic from `LocalCommitmentStore`, but require `ownerUserId` on every DAO call. Convert Room rows to `BackendScheduleEvent` and `BackendTodoItem`.

Minimum public methods:

```kotlin
class RoomCommitmentRepository(
    private val database: CommitmentDatabase,
) : CommitmentRepository {
    override fun listCommitments(ownerUserId: String): AgentCommitmentsPayload
    override fun applyProposal(ownerUserId: String, proposal: AgentProposal): AgentApplyResult
    override fun deleteCommitment(ownerUserId: String, commitmentId: String): AgentApplyResult
}
```

- [ ] **Step 5: Write owner isolation tests**

Create `app/src/test/java/com/zpc/fucktheddl/commitments/RoomCommitmentRepositoryTest.kt`:

```kotlin
package com.zpc.fucktheddl.commitments

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.AgentSchedulePatch
import com.zpc.fucktheddl.agent.CommitmentType
import com.zpc.fucktheddl.commitments.room.CommitmentDatabase
import com.zpc.fucktheddl.commitments.room.RoomCommitmentRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomCommitmentRepositoryTest {
    @Test
    fun dataIsIsolatedByOwnerUserId() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CommitmentDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repo = RoomCommitmentRepository(db)
        val proposal = AgentProposal(
            id = "p1",
            commitmentType = CommitmentType.Schedule,
            title = "地理课",
            summary = "地理课",
            impact = "",
            requiresConfirmation = true,
            schedulePatch = AgentSchedulePatch(
                title = "地理课",
                start = "2026-05-02T09:00:00+08:00",
                end = "2026-05-02T10:00:00+08:00",
                timezone = "Asia/Shanghai",
                location = "",
                notes = "",
                tags = emptyList(),
            ),
        )

        repo.applyProposal("usr_a", proposal)

        assertEquals(1, repo.listCommitments("usr_a").events.size)
        assertEquals(0, repo.listCommitments("usr_b").events.size)
    }
}
```

- [ ] **Step 6: Run Room tests**

Run:

```bash
./gradlew testDebugUnitTest --tests com.zpc.fucktheddl.commitments.RoomCommitmentRepositoryTest
```

Expected: Room test passes.

- [ ] **Step 7: Commit Task 6**

```bash
git add settings.gradle.kts app/build.gradle.kts app/src/main/java/com/zpc/fucktheddl/commitments app/src/test/java/com/zpc/fucktheddl/commitments/RoomCommitmentRepositoryTest.kt
git commit -m "Store commitments in user-scoped Room tables" \
  -m "The Android local data source is now isolated behind CommitmentRepository and scoped by backend user id, preparing the app for local-first user separation and future sync." \
  -m "Constraint: Existing legacy local data is not migrated." \
  -m "Confidence: medium" \
  -m "Scope-risk: broad" \
  -m "Tested: ./gradlew testDebugUnitTest --tests com.zpc.fucktheddl.commitments.RoomCommitmentRepositoryTest"
```

---

## Task 7: Android Login Gate UI

**Files:**
- Create: `app/src/main/java/com/zpc/fucktheddl/ui/LoginScreen.kt`
- Modify: `app/src/main/java/com/zpc/fucktheddl/MainActivity.kt`
- Modify: `app/src/main/java/com/zpc/fucktheddl/ui/FuckTheDdlApp.kt`

- [ ] **Step 1: Create login screen**

Create `app/src/main/java/com/zpc/fucktheddl/ui/LoginScreen.kt`:

```kotlin
package com.zpc.fucktheddl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    sending: Boolean,
    verifying: Boolean,
    message: String,
    onRequestCode: (String) -> Unit,
    onVerifyCode: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("DDL Agent", fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("输入邮箱，使用验证码登录。", modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("邮箱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Button(
                enabled = !sending && email.contains("@"),
                onClick = { onRequestCode(email.trim()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(if (sending) "发送中..." else "发送验证码")
            }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("验证码") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                shape = RoundedCornerShape(16.dp),
            )
            Button(
                enabled = !verifying && email.contains("@") && code.length == 6,
                onClick = { onVerifyCode(email.trim(), code) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(if (verifying) "登录中..." else "登录")
            }
            if (message.isNotBlank()) {
                Text(message, modifier = Modifier.padding(top = 14.dp), fontSize = 13.sp)
            }
        }
    }
}
```

- [ ] **Step 2: Wire login gate in MainActivity**

Modify `MainActivity.kt`:

- Instantiate `AuthSessionStore`.
- Keep `var authSession by remember { mutableStateOf(authSessionStore.load()) }`.
- If `authSession.isLoggedIn` is false, render `LoginScreen`.
- On code request, call `AgentApiClient(connectionSettings.toConfig()).requestLoginCode(email)`.
- On verify, save `AuthSession(result.userId, result.email, result.accessToken)` and update connection settings access token.
- Only create `RoomCommitmentRepository` and render `FuckTheDdlApp` after login.

The wiring must pass `authSession.userId` into repository calls:

```kotlin
commitmentsProvider = { commitmentRepository.listCommitments(authSession.userId) }
proposalApplier = { proposal -> commitmentRepository.applyProposal(authSession.userId, proposal) }
commitmentDeleter = { commitmentId -> commitmentRepository.deleteCommitment(authSession.userId, commitmentId) }
```

- [ ] **Step 3: Simplify settings user panel**

Modify `FuckTheDdlApp.kt` to stop exposing token registration. Settings should receive:

```kotlin
userEmail: String,
onLogout: () -> Unit,
```

Remove old `accountRegistrar` UI path. The user settings menu shows current email and one logout button.

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew :app:compileDebugKotlin --rerun-tasks
```

Expected: build success.

- [ ] **Step 5: Commit Task 7**

```bash
git add app/src/main/java/com/zpc/fucktheddl/MainActivity.kt app/src/main/java/com/zpc/fucktheddl/ui/LoginScreen.kt app/src/main/java/com/zpc/fucktheddl/ui/FuckTheDdlApp.kt
git commit -m "Gate Android app behind email verification login" \
  -m "The main UI now depends on an authenticated session, while login is handled by a separate Compose surface and session store instead of being buried in settings." \
  -m "Constraint: User selected mandatory login before main app access." \
  -m "Confidence: medium" \
  -m "Scope-risk: broad" \
  -m "Tested: ./gradlew :app:compileDebugKotlin --rerun-tasks"
```

---

## Task 8: Client-Side Proposal Application Cleanup

**Files:**
- Modify: `app/src/main/java/com/zpc/fucktheddl/ui/FuckTheDdlApp.kt`
- Modify: `app/src/main/java/com/zpc/fucktheddl/agent/AgentApiClient.kt`
- Delete: `app/src/main/java/com/zpc/fucktheddl/agent/LocalCommitmentStore.kt`
- Test: existing Android unit tests

- [ ] **Step 1: Remove old server commitment assumptions from UI wiring**

In `FuckTheDdlApp.kt`, ensure proposal confirmation calls only the injected `proposalApplier`. Do not call backend `confirm` to write schedules/todos.

Expected callback shape:

```kotlin
proposalApplier: (AgentProposal) -> AgentApplyResult
```

Keep backend `editProposal` only for editing AI proposal text if still used before confirmation; local schedule/todo writes must go through `CommitmentRepository`.

- [ ] **Step 2: Remove LocalCommitmentStore**

Delete `app/src/main/java/com/zpc/fucktheddl/agent/LocalCommitmentStore.kt` after all references are gone.

Run:

```bash
rg -n "LocalCommitmentStore|SQLiteOpenHelper|android.database.sqlite" app/src/main/java
```

Expected: no results.

- [ ] **Step 3: Compile and test**

Run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Expected: build success and all Android unit tests pass.

- [ ] **Step 4: Commit Task 8**

```bash
git add app/src/main/java/com/zpc/fucktheddl app/src/test/java/com/zpc/fucktheddl
git add -u app/src/main/java/com/zpc/fucktheddl/agent/LocalCommitmentStore.kt
git commit -m "Make Room the only Android commitment store" \
  -m "Local schedule and todo writes now go through CommitmentRepository, removing the old SQLiteOpenHelper store and keeping UI independent from persistence details." \
  -m "Constraint: Client Room is the source of truth for commitments in this phase." \
  -m "Confidence: medium" \
  -m "Scope-risk: broad" \
  -m "Tested: ./gradlew testDebugUnitTest assembleDebug"
```

---

## Task 9: Backend Commitment Storage Exit From Primary Path

**Files:**
- Modify: `backend/fucktheddl_agent/api.py`
- Modify: `backend/fucktheddl_agent/service.py`
- Modify: `backend/tests/test_agent_api.py`

- [ ] **Step 1: Add regression test that propose uses client commitments**

In `backend/tests/test_agent_api.py`, add a test that sends `commitments` in `/agent/propose` and verifies response is generated without reading server commitment files.

Use a `tmp_path` with no existing commitments and include:

```python
json={
    "text": "今天有哪些活动",
    "session_id": "query-1",
    "commitments": {
        "events": [
            {
                "id": "evt_1",
                "title": "地理课",
                "start": "2026-05-01T09:00:00+08:00",
                "end": "2026-05-01T10:00:00+08:00",
                "timezone": "Asia/Shanghai",
                "status": "confirmed",
                "location": "",
                "notes": "",
                "tags": [],
            }
        ],
        "todos": [],
    },
}
```

Assert the result mentions `地理课` in proposal title, summary, or candidates.

- [ ] **Step 2: Keep confirm compatibility but stop treating it as source of truth**

Do not remove `/agent/confirm/{proposal_id}` in this task if Android still compiles against it. Mark it compatibility-only in `api.py` with a comment:

```python
# Compatibility endpoint. Android applies confirmed proposals into local Room.
# This endpoint remains only for older clients and tests during migration.
```

- [ ] **Step 3: Run backend tests**

Run:

```bash
.venv/bin/pytest backend/tests -q
```

Expected: all backend tests pass.

- [ ] **Step 4: Commit Task 9**

```bash
git add backend/fucktheddl_agent/api.py backend/fucktheddl_agent/service.py backend/tests/test_agent_api.py
git commit -m "Treat client commitments as agent context source" \
  -m "The backend agent path now relies on commitments sent by the authenticated client, keeping local Room as the schedule source of truth while preserving compatibility endpoints during migration." \
  -m "Constraint: Server-side schedule storage is not the primary data source in this phase." \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: .venv/bin/pytest backend/tests -q"
```

---

## Task 10: End-To-End Verification And Deployment Notes

**Files:**
- Modify: `docs/production-readiness.md`
- Test: full backend and Android test suites

- [ ] **Step 1: Update production readiness doc**

Add a section to `docs/production-readiness.md`:

```markdown
## Email Login Deployment

Required backend environment variables:

```text
RESEND_API_KEY=
RESEND_FROM_EMAIL=
RESEND_FROM_NAME=DDL Agent
REDIS_URL=
DEEPSEEK defaults if used by server-side fallback
```

Operational checks:

- Verify Resend sender/domain before public testing.
- Confirm `/health` is public.
- Confirm `/agent/propose` returns 401 without token.
- Confirm `/auth/code/request` sends a real email.
- Confirm `/auth/code/verify` returns a token.
- Confirm app settings do not display the token.
```

- [ ] **Step 2: Run backend full tests**

Run:

```bash
.venv/bin/pytest backend/tests -q
```

Expected: all backend tests pass.

- [ ] **Step 3: Run Android full tests and package**

Run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Expected: build success and all Android unit tests pass.

- [ ] **Step 4: Run static diff check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 5: Manual smoke test**

Install APK and verify:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Manual checklist:

- Fresh app launch shows login screen.
- Email code request reaches backend.
- Correct code opens main app.
- AI/ASR calls work only after login.
- Create one schedule and one todo; both persist after app restart.
- Logout returns to login.
- Login same email restores local data.
- Login different email shows empty local data.

- [ ] **Step 6: Commit Task 10**

```bash
git add docs/production-readiness.md
git commit -m "Document email login deployment checks" \
  -m "Production readiness now records Resend configuration and auth smoke checks needed before distributing clients against the deployed backend." \
  -m "Constraint: Resend credentials are backend-only environment variables." \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: .venv/bin/pytest backend/tests -q; ./gradlew testDebugUnitTest assembleDebug; git diff --check"
```

---

## Self-Review

Spec coverage:

- Passwordless email verification: Tasks 2, 3, 4, 5, 7.
- Resend provider: Tasks 2, 4, 10.
- Mandatory login before app: Task 7.
- Long-lived hidden token: Tasks 3, 5, 7.
- Backend SQLite users/codes/tokens: Tasks 1, 3, 4.
- Strict API auth: Task 4.
- Local Room data by user: Tasks 6, 7, 8.
- No legacy data migration: Tasks 6, 7, 8.
- DeepSeek key remains client-side: Task 7 keeps settings; no backend persistence task is introduced.
- Client sends commitments to agent: Tasks 8, 9.
- Tests and smoke: Tasks 1 through 10.

Placeholder scan:

- No placeholder markers or unspecified implementation slots are intentionally left.
- Version numbers are explicit. If Gradle cannot resolve the KSP version, the implementer must adjust to the matching Kotlin/KSP release and record that in the relevant commit.

Type consistency:

- Backend `AuthService` returns `VerifiedLogin`, while API schema returns `AuthCodeVerifyResponse`.
- Android auth model is `AuthSession`, and login response model is `LoginCodeVerifyResult`.
- Android persistence boundary is `CommitmentRepository`, with `RoomCommitmentRepository` as the implementation.
