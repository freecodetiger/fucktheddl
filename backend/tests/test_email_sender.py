import json
from urllib.error import HTTPError, URLError

from fucktheddl_agent.email_sender import FakeEmailSender, LoginCodeEmail, ResendEmailSender


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


def test_resend_sender_builds_expected_request(monkeypatch):
    sender = ResendEmailSender(
        api_key="resend-key",
        from_email="noreply@example.com",
        from_name="DDL Agent",
    )
    captured: dict[str, object] = {}

    class FakeResponse:
        status = 200

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

    def fake_urlopen(req, timeout):
        captured["request"] = req
        captured["timeout"] = timeout
        return FakeResponse()

    monkeypatch.setattr("fucktheddl_agent.email_sender.request.urlopen", fake_urlopen)

    sender.send_login_code(
        LoginCodeEmail(
            to_email="user@example.com",
            code="654321",
            expires_minutes=10,
            product_name="DDL Agent",
        )
    )

    req = captured["request"]

    assert captured["timeout"] == 10
    assert req.full_url == "https://api.resend.com/emails"
    assert req.get_method() == "POST"
    assert req.get_header("Authorization") == "Bearer resend-key"
    assert req.get_header("Content-type") == "application/json"

    payload = json.loads(req.data.decode("utf-8"))
    assert payload["from"] == "DDL Agent <noreply@example.com>"
    assert payload["to"] == ["user@example.com"]
    assert payload["subject"] == "你的 DDL Agent 登录验证码"
    assert "654321" in payload["html"]
    assert "654321" in payload["text"]


def test_resend_sender_converts_http_error_to_runtime_error(monkeypatch):
    sender = ResendEmailSender(api_key="resend-key", from_email="noreply@example.com")

    def fake_urlopen(req, timeout):
        raise HTTPError(req.full_url, 401, "Unauthorized", hdrs=None, fp=None)

    monkeypatch.setattr("fucktheddl_agent.email_sender.request.urlopen", fake_urlopen)

    try:
        sender.send_login_code(
            LoginCodeEmail(
                to_email="user@example.com",
                code="654321",
                expires_minutes=10,
                product_name="DDL Agent",
            )
        )
    except RuntimeError as exc:
        assert str(exc) == "Resend email failed with HTTP 401"
    else:
        raise AssertionError("expected RuntimeError")


def test_resend_sender_converts_url_error_to_runtime_error(monkeypatch):
    sender = ResendEmailSender(api_key="resend-key", from_email="noreply@example.com")

    def fake_urlopen(req, timeout):
        raise URLError("temporary failure in name resolution")

    monkeypatch.setattr("fucktheddl_agent.email_sender.request.urlopen", fake_urlopen)

    try:
        sender.send_login_code(
            LoginCodeEmail(
                to_email="user@example.com",
                code="654321",
                expires_minutes=10,
                product_name="DDL Agent",
            )
        )
    except RuntimeError as exc:
        assert str(exc) == "Resend email request failed"
    else:
        raise AssertionError("expected RuntimeError")


def test_resend_sender_converts_timeout_error_to_runtime_error(monkeypatch):
    sender = ResendEmailSender(api_key="resend-key", from_email="noreply@example.com")

    def fake_urlopen(req, timeout):
        raise TimeoutError("timed out")

    monkeypatch.setattr("fucktheddl_agent.email_sender.request.urlopen", fake_urlopen)

    try:
        sender.send_login_code(
            LoginCodeEmail(
                to_email="user@example.com",
                code="654321",
                expires_minutes=10,
                product_name="DDL Agent",
            )
        )
    except RuntimeError as exc:
        assert str(exc) == "Resend email request failed"
    else:
        raise AssertionError("expected RuntimeError")
