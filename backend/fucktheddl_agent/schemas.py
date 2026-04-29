from typing import Literal

from pydantic import BaseModel, Field


CommitmentType = Literal["schedule", "todo", "clarify"]
StepState = Literal["done", "active", "waiting"]


class AgentRequest(BaseModel):
    text: str = Field(min_length=1)
    session_id: str = Field(default="default")
    timezone: str = Field(default="Asia/Shanghai")


class ChainStep(BaseModel):
    label: str
    state: StepState


class SchedulePatch(BaseModel):
    title: str
    time_range: str
    timezone: str
    reminder: str | None = None


class TodoPatch(BaseModel):
    title: str
    due_label: str
    priority: Literal["low", "medium", "high"]


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


class HealthResponse(BaseModel):
    status: Literal["ok"]
    agent_framework: Literal["langgraph"]
    write_policy: Literal["proposal_required"]

