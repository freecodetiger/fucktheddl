from typing import Any, Literal

from pydantic import BaseModel, Field


CommitmentType = Literal["schedule", "todo", "delete", "update", "query", "suggestion", "clarify"]
StepState = Literal["done", "active", "waiting"]
CommitmentStatus = Literal["confirmed", "cancelled", "done", "active"]


class AgentRequest(BaseModel):
    text: str = Field(min_length=1)
    session_id: str = Field(default="default")
    timezone: str = Field(default="Asia/Shanghai")
    commitments: dict[str, list[dict[str, Any]]] | None = None
    model_api_key: str | None = None
    model_base_url: str | None = None
    model: str | None = None
    disable_thinking: bool = True


class AuthCodeRequest(BaseModel):
    email: str = Field(min_length=3)


class AuthCodeVerifyRequest(BaseModel):
    email: str = Field(min_length=3)
    code: str = Field(min_length=6, max_length=6)


class AuthCodeVerifyResponse(BaseModel):
    user_id: str
    email: str
    access_token: str
    newly_created: bool


class LogoutResponse(BaseModel):
    status: Literal["logged_out"]


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


class DeletePatch(BaseModel):
    target_id: str
    target_type: Literal["schedule", "todo"]
    target_title: str


class UpdatePatch(BaseModel):
    target_id: str
    target_type: Literal["schedule", "todo"]
    target_title: str
    schedule_patch: SchedulePatch | None = None
    todo_patch: TodoPatch | None = None


class ProposalCandidate(BaseModel):
    id: str
    target_type: Literal["schedule", "todo"]
    title: str
    when: str
    detail: str = ""
    resolution_text: str


class Proposal(BaseModel):
    id: str
    commitment_type: CommitmentType
    title: str
    summary: str
    impact: str
    requires_confirmation: bool
    schedule_patch: SchedulePatch | None
    todo_patch: TodoPatch | None
    delete_patch: DeletePatch | None = None
    update_patch: UpdatePatch | None = None
    candidates: list[ProposalCandidate] = Field(default_factory=list)


class ProposalEditRequest(BaseModel):
    title: str | None = None
    summary: str | None = None
    schedule_patch: SchedulePatch | None = None
    todo_patch: TodoPatch | None = None


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
    disable_thinking: bool = True


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


class CommitmentEvent(BaseModel):
    id: str
    title: str
    start: str
    end: str
    timezone: str
    status: CommitmentStatus
    location: str = ""
    notes: str = ""
    tags: list[str] = Field(default_factory=list)


class CommitmentTodo(BaseModel):
    id: str
    title: str
    due: str
    timezone: str = "Asia/Shanghai"
    status: CommitmentStatus
    priority: Literal["low", "medium", "high"]
    notes: str = ""
    tags: list[str] = Field(default_factory=list)


class CommitmentsResponse(BaseModel):
    events: list[CommitmentEvent] = Field(default_factory=list)
    todos: list[CommitmentTodo] = Field(default_factory=list)


class AsrSessionResponse(BaseModel):
    api_key: str
    url: str
    model: Literal["fun-asr-realtime-2025-09-15"]
    sample_rate: int
    service_type: int
    expires_at: str
