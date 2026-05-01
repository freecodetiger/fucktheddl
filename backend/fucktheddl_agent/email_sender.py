from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Protocol
from urllib import request
from urllib.error import HTTPError, URLError


@dataclass(frozen=True)
class LoginCodeEmail:
    to_email: str
    code: str
    expires_minutes: int
    product_name: str


class EmailSender(Protocol):
    def send_login_code(self, email: LoginCodeEmail) -> None:
        ...


class EmailDeliveryError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


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

        try:
            with request.urlopen(req, timeout=10) as response:
                if response.status < 200 or response.status >= 300:
                    raise RuntimeError(f"Resend email failed with HTTP {response.status}")
        except HTTPError as exc:
            detail = _read_http_error_detail(exc)
            message = f"Resend email failed with HTTP {exc.code}"
            if detail:
                message = f"{message}: {detail}"
            raise EmailDeliveryError(message, status_code=exc.code) from exc
        except (URLError, TimeoutError) as exc:
            raise EmailDeliveryError("Resend email request failed") from exc


def _read_http_error_detail(error: HTTPError) -> str:
    if error.fp is None:
        return ""
    try:
        raw = error.fp.read()
    except Exception:
        return ""
    if not raw:
        return ""
    try:
        payload = json.loads(raw.decode("utf-8", errors="replace"))
    except json.JSONDecodeError:
        return raw.decode("utf-8", errors="replace")[:300]
    if isinstance(payload, dict):
        for key in ("message", "error", "detail"):
            value = payload.get(key)
            if isinstance(value, str):
                return value[:300]
    return ""
