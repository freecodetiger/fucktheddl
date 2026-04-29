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

    @property
    def configured(self) -> bool:
        return bool(self.api_key and self.base_url and self.model)


@dataclass(frozen=True)
class AsrSettings:
    api_key: str | None
    app_key: str | None
    url: str
    model: str = "fun-asr-realtime-2025-09-15"
    sample_rate: int = 16000
    service_type: int = 4

    @property
    def configured(self) -> bool:
        return bool(self.api_key and self.app_key)

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
        model=os.environ.get("OPENAI_MODEL", "gpt-5.4"),
        enabled=os.environ.get("FUCKTHEDDL_USE_MODEL", "false").lower() == "true",
    )
    asr = AsrSettings(
        api_key=os.environ.get("ALIYUN_API_KEY"),
        app_key=os.environ.get("ALIYUN_APP_KEY"),
        url=os.environ.get("ALIYUN_ASR_URL", "wss://nls-gateway.cn-shanghai.aliyuncs.com:443/ws/v1"),
    )
    return AppSettings(data_root=root, model=model, asr=asr)
