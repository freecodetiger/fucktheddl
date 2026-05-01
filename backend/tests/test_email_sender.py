import json

from fucktheddl_agent.email_sender import EmailDeliveryError, FakeEmailSender, LoginCodeEmail, ResendEmailSender


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
        status_code = 200
        text = ""

        def json(self):
            return {}

    def fake_post(url, json, headers, timeout):
        captured["url"] = url
        captured["json"] = json
        captured["headers"] = headers
        captured["timeout"] = timeout
        return FakeResponse()

    monkeypatch.setattr("fucktheddl_agent.email_sender.requests.post", fake_post)

    sender.send_login_code(
        LoginCodeEmail(
            to_email="user@example.com",
            code="654321",
            expires_minutes=10,
            product_name="DDL Agent",
        )
    )

    assert captured["timeout"] == 10
    assert captured["url"] == "https://api.resend.com/emails"
    assert captured["headers"]["Authorization"] == "Bearer resend-key"

    payload = captured["json"]
    assert payload["from"] == "DDL Agent <noreply@example.com>"
    assert payload["to"] == ["user@example.com"]
    assert payload["subject"] == "你的 DDL Agent 登录验证码"
    assert "654321" in payload["html"]
    assert "654321" in payload["text"]


def test_resend_sender_converts_http_error_to_runtime_error(monkeypatch):
    sender = ResendEmailSender(api_key="resend-key", from_email="noreply@example.com")

    class FakeResponse:
        status_code = 401
        text = ""

        def json(self):
            return {}

    monkeypatch.setattr("fucktheddl_agent.email_sender.requests.post", lambda *args, **kwargs: FakeResponse())

    try:
        sender.send_login_code(
            LoginCodeEmail(
                to_email="user@example.com",
                code="654321",
                expires_minutes=10,
                product_name="DDL Agent",
            )
        )
    except EmailDeliveryError as exc:
        assert str(exc) == "Resend email failed with HTTP 401"
        assert exc.status_code == 401
    else:
        raise AssertionError("expected EmailDeliveryError")


def test_resend_sender_includes_http_error_body_message(monkeypatch):
    sender = ResendEmailSender(api_key="resend-key", from_email="noreply@example.com")

    class FakeResponse:
        status_code = 403
        text = '{"message":"domain is not verified"}'

        def json(self):
            return json.loads(self.text)

    monkeypatch.setattr("fucktheddl_agent.email_sender.requests.post", lambda *args, **kwargs: FakeResponse())

    try:
        sender.send_login_code(
            LoginCodeEmail(
                to_email="user@example.com",
                code="654321",
                expires_minutes=10,
                product_name="DDL Agent",
            )
        )
    except EmailDeliveryError as exc:
        assert str(exc) == "Resend email failed with HTTP 403: domain is not verified"
        assert exc.status_code == 403
    else:
        raise AssertionError("expected EmailDeliveryError")


def test_resend_sender_includes_plain_http_error_body(monkeypatch):
    sender = ResendEmailSender(api_key="resend-key", from_email="noreply@example.com")

    class FakeResponse:
        status_code = 403
        text = "domain is not verified"

        def json(self):
            raise json.JSONDecodeError("bad", self.text, 0)

    monkeypatch.setattr("fucktheddl_agent.email_sender.requests.post", lambda *args, **kwargs: FakeResponse())

    try:
        sender.send_login_code(
            LoginCodeEmail(
                to_email="user@example.com",
                code="654321",
                expires_minutes=10,
                product_name="DDL Agent",
            )
        )
    except EmailDeliveryError as exc:
        assert str(exc) == "Resend email failed with HTTP 403: domain is not verified"
        assert exc.status_code == 403
    else:
        raise AssertionError("expected EmailDeliveryError")


def test_resend_sender_converts_timeout_error_to_runtime_error(monkeypatch):
    sender = ResendEmailSender(api_key="resend-key", from_email="noreply@example.com")

    def fake_post(*args, **kwargs):
        raise TimeoutError("timed out")

    monkeypatch.setattr("fucktheddl_agent.email_sender.requests.post", fake_post)

    try:
        sender.send_login_code(
            LoginCodeEmail(
                to_email="user@example.com",
                code="654321",
                expires_minutes=10,
                product_name="DDL Agent",
            )
        )
    except EmailDeliveryError as exc:
        assert str(exc) == "Resend email request failed"
    else:
        raise AssertionError("expected EmailDeliveryError")
