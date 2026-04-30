from __future__ import annotations

import os
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path


@dataclass(frozen=True)
class ModelSettings:
    api_key: str | None
    base_url: str | None
    model: str
    enabled: bool
    disable_thinking: bool

    @property
    def configured(self) -> bool:
        return bool(self.api_key and self.base_url and self.model)


@dataclass(frozen=True)
class AsrSettings:
    api_key: str | None
    url: str
    model: str = "fun-asr-realtime-2025-09-15"
    sample_rate: int = 16000
    service_type: int = 4

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    @property
    def expires_at(self) -> str:
        return (datetime.now(timezone.utc) + timedelta(minutes=30)).isoformat()


@dataclass(frozen=True)
class AppSettings:
    data_root: Path
    model: ModelSettings
    asr: AsrSettings


def load_settings(data_root: Path | None = None) -> AppSettings:
    root = data_root or Path(os.environ.get("FUCKTHEDDL_DATA_ROOT", ".")).resolve()
    model = ModelSettings(
        api_key=os.environ.get("OPENAI_API_KEY"),
        base_url=os.environ.get("OPENAI_BASE_URL"),
        model=os.environ.get("OPENAI_MODEL", "deepseek-v4-flash"),
        enabled=os.environ.get("FUCKTHEDDL_USE_MODEL", "false").lower() == "true",
        disable_thinking=os.environ.get("OPENAI_DISABLE_THINKING", "true").lower() == "true",
    )
    asr = AsrSettings(
        api_key=os.environ.get("ALIYUN_API_KEY"),
        url=os.environ.get("ALIYUN_ASR_URL", "wss://dashscope.aliyuncs.com/api-ws/v1/inference"),
    )
    return AppSettings(data_root=root, model=model, asr=asr)
