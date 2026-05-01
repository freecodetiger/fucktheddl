from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Protocol

import requests


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
        resp = requests.post(
            "https://api.resend.com/emails",
            json=payload,
            headers={
                "Authorization": f"Bearer {self.api_key}",
            },
            timeout=10,
        )
        if resp.status_code >= 400:
            body = resp.text
            detail = ""
            try:
                data = resp.json()
                if isinstance(data, dict):
                    for key in ("message", "error", "detail"):
                        value = data.get(key)
                        if isinstance(value, str):
                            detail = value[:300]
                            break
            except json.JSONDecodeError:
                detail = body[:300]
            message = f"Resend email failed with HTTP {resp.status_code}"
            if detail:
                message = f"{message}: {detail}"
            raise EmailDeliveryError(message, status_code=resp.status_code)
