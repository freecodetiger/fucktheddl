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
