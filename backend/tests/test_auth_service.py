import sqlite3
from datetime import datetime, timedelta, timezone

import pytest
from fastapi import HTTPException

from fucktheddl_agent.auth import AuthService
from fucktheddl_agent.auth_store import AuthStore
from fucktheddl_agent.email_sender import EmailDeliveryError, FakeEmailSender


def make_service(tmp_path, now_provider=None):
    sender = FakeEmailSender()
    service = AuthService(
        store=AuthStore(tmp_path / "auth.sqlite3"),
        email_sender=sender,
        product_name="DDL Agent",
        now_provider=now_provider,
    )
    return service, sender


def test_request_code_sends_normalized_email_and_six_digit_code(tmp_path):
    service, sender = make_service(tmp_path)

    service.request_code(" USER@example.com ")

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


def test_failed_email_delivery_does_not_create_cooldown_record(tmp_path):
    class FailingSender:
        def send_login_code(self, email):
            raise EmailDeliveryError("resend rejected sender", status_code=403)

    store = AuthStore(tmp_path / "auth.sqlite3")
    service = AuthService(store=store, email_sender=FailingSender())

    with pytest.raises(EmailDeliveryError):
        service.request_code("user@example.com")

    assert store.latest_verification_code("user@example.com") is None


def test_wrong_code_locks_after_three_attempts(tmp_path):
    service, _sender = make_service(tmp_path)
    service.request_code("user@example.com")

    with pytest.raises(HTTPException) as first_error:
        service.verify_code("user@example.com", "000000")
    with pytest.raises(HTTPException) as second_error:
        service.verify_code("user@example.com", "000000")
    with pytest.raises(HTTPException) as third_error:
        service.verify_code("user@example.com", "000000")

    assert first_error.value.status_code == 401
    assert second_error.value.status_code == 401
    assert third_error.value.status_code == 423

    with pytest.raises(HTTPException) as locked_error:
        service.verify_code("user@example.com", "000000")

    assert locked_error.value.status_code == 423


def test_verify_code_creates_user_token_and_authenticates(tmp_path):
    service, sender = make_service(tmp_path)
    service.request_code("USER@example.com")

    result = service.verify_code("user@example.com", sender.sent[0].code)
    authenticated = service.authenticate_token(result.access_token)

    assert result.email == "user@example.com"
    assert result.user_id.startswith("usr_")
    assert result.access_token
    assert result.newly_created is True
    assert authenticated.user_id == result.user_id
    assert authenticated.token == result.access_token


def test_existing_email_login_reuses_user_and_marks_not_new(tmp_path):
    service, sender = make_service(tmp_path)

    service.request_code("user@example.com")
    first = service.verify_code("user@example.com", sender.sent[0].code)

    older_than_cooldown = datetime.now(timezone.utc) - timedelta(seconds=61)
    with sqlite3.connect(service.store.path) as db:
        db.execute(
            "UPDATE email_verification_codes SET created_at = ? WHERE email = ?",
            (older_than_cooldown.isoformat(), "user@example.com"),
        )

    service.request_code("USER@example.com")
    second = service.verify_code("user@example.com", sender.sent[1].code)

    assert second.user_id == first.user_id
    assert second.email == "user@example.com"
    assert second.newly_created is False
    assert second.access_token != first.access_token


def test_logout_revokes_token_and_authentication_then_fails(tmp_path):
    service, sender = make_service(tmp_path)
    service.request_code("user@example.com")
    result = service.verify_code("user@example.com", sender.sent[0].code)

    assert service.logout(result.access_token) is True

    with pytest.raises(HTTPException) as error:
        service.authenticate_token(result.access_token)

    assert error.value.status_code == 401
