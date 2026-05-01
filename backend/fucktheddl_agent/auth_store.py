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
        normalized = _normalize_email(email)
        now = _now()
        with self._connect() as db:
            user_id = f"usr_{uuid4().hex}"
            db.execute(
                """
                INSERT INTO users (id, email, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(email) DO NOTHING
                """,
                (user_id, normalized, _iso(now), _iso(now)),
            )
            row = db.execute("SELECT id, email FROM users WHERE email = ?", (normalized,)).fetchone()
        if row is None:
            raise RuntimeError(f"user row missing after insert/select for {normalized}")
        return StoredUser(user_id=row["id"], email=row["email"])

    def find_user_by_email(self, email: str) -> StoredUser | None:
        normalized = _normalize_email(email)
        with self._connect() as db:
            row = db.execute("SELECT id, email FROM users WHERE email = ?", (normalized,)).fetchone()
        if row is None:
            return None
        return StoredUser(user_id=row["id"], email=row["email"])

    def create_verification_code(self, email: str, code_hash: str, expires_at: datetime) -> str:
        normalized = _normalize_email(email)
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
        normalized = _normalize_email(email)
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
        with self._connect() as db:
            row = db.execute(
                """
                UPDATE access_tokens
                SET last_used_at = ?
                WHERE token_hash = ? AND revoked_at IS NULL
                RETURNING user_id
                """,
                (_iso(_now()), token_hash),
            ).fetchone()
        if row is None:
            return None
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
        db.execute("PRAGMA foreign_keys = ON")
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


def _normalize_email(email: str) -> str:
    return email.strip().lower()


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
    if value.tzinfo is None:
        raise ValueError("datetime must be timezone-aware")
    return value.astimezone(timezone.utc).isoformat()


def _parse(value: str) -> datetime:
    return datetime.fromisoformat(value)


def _parse_optional(value: str | None) -> datetime | None:
    return datetime.fromisoformat(value) if value else None
