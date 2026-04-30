from __future__ import annotations

import json

from fucktheddl_agent.config import ModelSettings


class ModelGateway:
    def __init__(self, settings: ModelSettings) -> None:
        self.settings = settings

    def health_payload(self) -> dict[str, object]:
        return {
            "base_url": self.settings.base_url,
            "model": self.settings.model,
            "configured": self.settings.configured,
            "enabled": self.settings.enabled,
            "disable_thinking": self.settings.disable_thinking,
        }

    def extract_commitment(self, text: str) -> dict[str, object] | None:
        if not (self.settings.configured and self.settings.enabled):
            return None
        try:
            response = self.build_chat_model().invoke(
                [
                    (
                        "system",
                        "只提取日程管理承诺，返回 JSON，不要解释。"
                        "有明确开始时间、需要在某个时间出现或参与的是 schedule；"
                        "只要求在截止前完成的是 todo；信息不足时是 clarify。"
                        "title 必须使用简洁中文，不要包含日期和时间。"
                        "Fields: commitment_type, title, date, time, due, priority, reminder_minutes.",
                    ),
                    ("user", text),
                ],
            )
            content = response.content if isinstance(response.content, str) else str(response.content)
            parsed = json.loads(content)
            if isinstance(parsed, dict):
                return parsed
            if isinstance(parsed, list) and parsed and isinstance(parsed[0], dict):
                return parsed[0]
            return None
        except Exception:
            return None

    def build_chat_model(self):
        if not self.settings.configured:
            raise RuntimeError("OPENAI_API_KEY and OPENAI_BASE_URL are required")
        from langchain_openai import ChatOpenAI

        kwargs = {
            "api_key": self.settings.api_key,
            "base_url": self.settings.base_url,
            "model": self.settings.model,
            "temperature": 0,
        }
        if self.settings.disable_thinking:
            kwargs["extra_body"] = {
                "thinking": {"type": "disabled"},
            }
        return ChatOpenAI(**kwargs)
