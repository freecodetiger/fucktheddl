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
            raise RuntimeError(f"Resend email failed with HTTP {exc.code}") from exc
        except (URLError, TimeoutError) as exc:
            raise RuntimeError("Resend email request failed") from exc
