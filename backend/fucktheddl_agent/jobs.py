from __future__ import annotations

import json
import threading
import uuid
from collections.abc import Callable
from typing import Any

from fucktheddl_agent.config import QueueSettings
from fucktheddl_agent.schemas import AgentJobAccepted, AgentJobStatus, AgentRequest, AgentResponse


AgentProcessor = Callable[[str, AgentRequest], AgentResponse]


class RedisAgentJobQueue:
    def __init__(self, settings: QueueSettings) -> None:
        if not settings.redis_url:
            raise ValueError("Redis URL is required")
        try:
            from redis import Redis
        except ImportError as error:
            raise RuntimeError("Install the redis package to enable Redis-backed jobs") from error

        self._redis = Redis.from_url(settings.redis_url, decode_responses=True)
        self._worker_count = settings.worker_count
        self._job_ttl_seconds = settings.job_ttl_seconds
        self._queue_key = "fucktheddl:agent:jobs"
        self._stop = threading.Event()
        self._threads: list[threading.Thread] = []
        self._processor: AgentProcessor | None = None

    def start(self, processor: AgentProcessor) -> None:
        if self._threads:
            return
        self._processor = processor
        for index in range(self._worker_count):
            thread = threading.Thread(
                target=self._run_worker,
                name=f"fucktheddl-agent-worker-{index + 1}",
                daemon=True,
            )
            thread.start()
            self._threads.append(thread)

    def shutdown(self) -> None:
        self._stop.set()
        for thread in self._threads:
            thread.join(timeout=2)

    def submit(self, user_id: str, request: dict[str, Any]) -> dict[str, str]:
        job_id = uuid.uuid4().hex
        payload = {
            "job_id": job_id,
            "user_id": user_id,
            "status": "queued",
            "request": request,
            "response": None,
            "error": None,
        }
        self._save_job(job_id, payload)
        self._redis.lpush(self._queue_key, job_id)
        return AgentJobAccepted(job_id=job_id, status="queued").model_dump()

    def get(self, user_id: str, job_id: str) -> dict[str, Any] | None:
        payload = self._load_job(job_id)
        if not payload or payload.get("user_id") != user_id:
            return None
        return AgentJobStatus(
            job_id=job_id,
            status=payload.get("status", "failed"),
            response=payload.get("response"),
            error=payload.get("error"),
        ).model_dump(mode="json")

    def _run_worker(self) -> None:
        while not self._stop.is_set():
            item = self._redis.brpop(self._queue_key, timeout=1)
            if item is None:
                continue
            _, job_id = item
            self._process_job(str(job_id))

    def _process_job(self, job_id: str) -> None:
        processor = self._processor
        if processor is None:
            return
        payload = self._load_job(job_id)
        if not payload:
            return
        payload["status"] = "running"
        self._save_job(job_id, payload)
        try:
            request = AgentRequest.model_validate(payload["request"])
            response = processor(str(payload["user_id"]), request)
            payload["status"] = "succeeded"
            payload["response"] = response.model_dump(mode="json")
            payload["error"] = None
        except Exception as error:
            payload["status"] = "failed"
            payload["response"] = None
            payload["error"] = str(error)[:500]
        self._save_job(job_id, payload)

    def _job_key(self, job_id: str) -> str:
        return f"fucktheddl:agent:job:{job_id}"

    def _save_job(self, job_id: str, payload: dict[str, Any]) -> None:
        self._redis.setex(
            self._job_key(job_id),
            self._job_ttl_seconds,
            json.dumps(payload, ensure_ascii=False),
        )

    def _load_job(self, job_id: str) -> dict[str, Any] | None:
        raw = self._redis.get(self._job_key(job_id))
        if not raw:
            return None
        return json.loads(raw)
