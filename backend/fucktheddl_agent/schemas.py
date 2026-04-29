from typing import Literal

from pydantic import BaseModel, Field


CommitmentType = Literal["schedule", "todo", "clarify"]
StepState = Literal["done", "active", "waiting"]
CommitmentStatus = Literal["confirmed", "cancelled", "done", "active"]


class AgentRequest(BaseModel):
    text: str = Field(min_length=1)
    session_id: str = Field(default="default")
    timezone: str = Field(default="Asia/Shanghai")


class ChainStep(BaseModel):
    label: str
    state: StepState


class Reminder(BaseModel):
    offset_minutes: int
    channel: Literal["local_notification"] = "local_notification"


class SchedulePatch(BaseModel):
    title: str
    start: str
    end: str
    timezone: str
    location: str = ""
    notes: str = ""
    tags: list[str] = Field(default_factory=list)
    reminders: list[Reminder] = Field(default_factory=list)


class TodoPatch(BaseModel):
    title: str
    due: str
    timezone: str = "Asia/Shanghai"
    priority: Literal["low", "medium", "high"]
    notes: str = ""
    tags: list[str] = Field(default_factory=list)


class Proposal(BaseModel):
    id: str
    commitment_type: CommitmentType
    title: str
    summary: str
    impact: str
    requires_confirmation: bool
    schedule_patch: SchedulePatch | None
    todo_patch: TodoPatch | None


class AgentResponse(BaseModel):
    session_id: str
    write_policy: Literal["proposal_required"]
    chain: list[ChainStep]
    proposal: Proposal


class ModelConfigResponse(BaseModel):
    base_url: str | None
    model: str
    configured: bool
    enabled: bool


class HealthResponse(BaseModel):
    status: Literal["ok"]
    agent_framework: Literal["langgraph"]
    write_policy: Literal["proposal_required"]
    model: ModelConfigResponse


class ApplyResponse(BaseModel):
    status: Literal["applied", "undone"]
    proposal_id: str | None = None
    commitment_id: str
    commitment_type: Literal["schedule", "todo"]
    file_path: str
    commit_hash: str


class AsrSessionResponse(BaseModel):
    app_key: str
    token: str
    url: str
    model: Literal["fun-asr-realtime-2025-09-15"]
    sample_rate: int
    service_type: int
    expires_at: str
