from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import threading

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


def test_concurrent_same_email_get_or_create_user_calls_share_one_user(tmp_path, monkeypatch):
    store = AuthStore(tmp_path / "auth.sqlite3")
    barrier = threading.Barrier(2)
    original_connect = store._connect

    class BarrierConnection:
        def __init__(self, connection):
            self._connection = connection

        def __enter__(self):
            self._connection.__enter__()
            return self

        def __exit__(self, exc_type, exc, tb):
            return self._connection.__exit__(exc_type, exc, tb)

        def execute(self, sql, parameters=()):
            if "INSERT INTO users" in sql:
                barrier.wait(timeout=2)
            return self._connection.execute(sql, parameters)

        def __getattr__(self, name):
            return getattr(self._connection, name)

    def connect_with_barrier():
        return BarrierConnection(original_connect())

    monkeypatch.setattr(store, "_connect", connect_with_barrier)

    with ThreadPoolExecutor(max_workers=2) as executor:
        futures = [
            executor.submit(store.get_or_create_user, "USER@example.com"),
            executor.submit(store.get_or_create_user, "user@example.com"),
        ]
        results = [future.result() for future in futures]

    assert {result.user_id for result in results} == {results[0].user_id}
    assert {result.email for result in results} == {"user@example.com"}


def test_revoked_token_hash_does_not_authenticate_user(tmp_path):
    store = AuthStore(tmp_path / "auth.sqlite3")
    user = store.get_or_create_user("user@example.com")
    store.create_access_token(user.user_id, token_hash="token-hash")

    assert store.revoke_token_hash("token-hash") is True
    assert store.user_id_for_token_hash("token-hash") is None


def test_concurrent_token_auth_and_revoke_is_safe(tmp_path):
    store = AuthStore(tmp_path / "auth.sqlite3")
    user = store.get_or_create_user("user@example.com")

    for attempt in range(50):
        token_hash = f"token-hash-{attempt}"
        store.create_access_token(user.user_id, token_hash=token_hash)
        barrier = threading.Barrier(2)

        def authenticate():
            barrier.wait(timeout=2)
            return store.user_id_for_token_hash(token_hash)

        def revoke():
            barrier.wait(timeout=2)
            return store.revoke_token_hash(token_hash)

        with ThreadPoolExecutor(max_workers=2) as executor:
            auth_future = executor.submit(authenticate)
            revoke_future = executor.submit(revoke)
            auth_result = auth_future.result()
            revoke_result = revoke_future.result()

        assert auth_result in {user.user_id, None}
        assert revoke_result is True
        assert store.user_id_for_token_hash(token_hash) is None
