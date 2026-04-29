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
        }

    def extract_commitment(self, text: str) -> dict[str, object] | None:
        if not (self.settings.configured and self.settings.enabled):
            return None
        try:
            response = self.build_chat_model().invoke(
                [
                    (
                        "system",
                        "Extract a schedule-management commitment as JSON only. "
                        "Use commitment_type schedule for time-bound attendance, todo for deadline-bound work, clarify when ambiguous. "
                        "Fields: commitment_type, title, date, time, due, priority, reminder_minutes.",
                    ),
                    ("user", text),
                ],
            )
            content = response.content if isinstance(response.content, str) else str(response.content)
            return json.loads(content)
        except Exception:
            return None

    def build_chat_model(self):
        if not self.settings.configured:
            raise RuntimeError("OPENAI_API_KEY and OPENAI_BASE_URL are required")
        from langchain_openai import ChatOpenAI

        return ChatOpenAI(
            api_key=self.settings.api_key,
            base_url=self.settings.base_url,
            model=self.settings.model,
            temperature=0,
        )
