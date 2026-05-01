from __future__ import annotations

import hashlib
import hmac
import secrets
from collections.abc import Callable
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
        now_provider: Callable[[], datetime] | None = None,
    ) -> None:
        self.store = store
        self.email_sender = email_sender
        self.product_name = product_name
        self.now_provider = now_provider or (lambda: datetime.now(timezone.utc))

    def request_code(self, email: str) -> None:
        normalized = _normalize_email(email)
        now = self.now_provider()
        latest = self.store.latest_verification_code(normalized)
        if latest is not None and latest.created_at > now - timedelta(seconds=60):
            raise HTTPException(status_code=429, detail="Verification code was requested too recently")

        code = f"{secrets.randbelow(1_000_000):06d}"
        self.email_sender.send_login_code(
            LoginCodeEmail(
                to_email=normalized,
                code=code,
                expires_minutes=5,
                product_name=self.product_name,
            )
        )
        self.store.create_verification_code(
            email=normalized,
            code_hash=_hash_secret(code),
            expires_at=now + timedelta(minutes=5),
        )

    def verify_code(self, email: str, code: str) -> VerifiedLogin:
        normalized = _normalize_email(email)
        now = self.now_provider()
        record = self.store.latest_verification_code(normalized)
        if record is None or record.consumed_at is not None or record.expires_at <= now:
            raise HTTPException(status_code=401, detail="Invalid or expired verification code")
        if record.locked_until is not None and record.locked_until > now:
            raise HTTPException(status_code=423, detail="Verification is temporarily locked")

        provided_hash = _hash_secret(code.strip())
        if not hmac.compare_digest(record.code_hash, provided_hash):
            attempts_after = record.attempts + 1
            locked_until = now + timedelta(minutes=5) if attempts_after >= 3 else None
            self.store.increment_attempts(record.id, locked_until)
            raise HTTPException(
                status_code=423 if locked_until is not None else 401,
                detail="Verification is temporarily locked" if locked_until is not None else "Invalid verification code",
            )

        self.store.consume_code(record.id)
        existing_user = self.store.find_user_by_email(normalized)
        stored_user = existing_user or self.store.get_or_create_user(normalized)
        access_token = secrets.token_urlsafe(32)
        self.store.create_access_token(stored_user.user_id, _hash_secret(access_token))
        return VerifiedLogin(
            user_id=stored_user.user_id,
            email=stored_user.email,
            access_token=access_token,
            newly_created=existing_user is None,
        )

    def authenticate_token(self, token: str) -> UserContext:
        normalized_token = token.strip()
        if not normalized_token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing access token")

        user_id = self.store.user_id_for_token_hash(_hash_secret(normalized_token))
        if user_id is None:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid access token")
        return UserContext(user_id=user_id, token=normalized_token)

    def logout(self, token: str) -> bool:
        normalized_token = token.strip()
        if not normalized_token:
            return False
        return self.store.revoke_token_hash(_hash_secret(normalized_token))


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
