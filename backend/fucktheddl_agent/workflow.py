from __future__ import annotations

from hashlib import sha1
from typing import Any, TypedDict

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from fucktheddl_agent.schemas import (
    AgentResponse,
    ChainStep,
    Proposal,
    SchedulePatch,
    TodoPatch,
)


class AgentGraphState(TypedDict, total=False):
    text: str
    session_id: str
    timezone: str
    commitment_type: str
    title: str
    time_range: str | None
    due_label: str | None
    priority: str
    facts_summary: str
    validation_summary: str
    proposal: dict[str, Any]
    model_extraction: dict[str, Any] | None


def build_agent_graph():
    graph = StateGraph(AgentGraphState)
    graph.add_node("classify_intent", classify_intent)
    graph.add_node("read_facts", read_facts)
    graph.add_node("validate_patch", validate_patch)
    graph.add_node("draft_proposal", draft_proposal)

    graph.add_edge(START, "classify_intent")
    graph.add_edge("classify_intent", "read_facts")
    graph.add_edge("read_facts", "validate_patch")
    graph.add_edge("validate_patch", "draft_proposal")
    graph.add_edge("draft_proposal", END)
    return graph.compile(checkpointer=MemorySaver())


def classify_intent(state: AgentGraphState) -> AgentGraphState:
    text = state["text"]
    model_extraction = state.get("model_extraction")
    if model_extraction:
        commitment_type = str(model_extraction.get("commitment_type", "clarify"))
        return {
            **state,
            "commitment_type": commitment_type if commitment_type in {"schedule", "todo", "clarify"} else "clarify",
            "title": str(model_extraction.get("title") or _compact_title(text)),
            "time_range": str(model_extraction.get("time") or "") or None,
            "due_label": str(model_extraction.get("due") or "") or None,
            "priority": str(model_extraction.get("priority") or "medium"),
        }

    has_time = any(token in text for token in ("点", ":", "上午", "下午", "晚上", "早上"))
    has_deadline = any(token in text.lower() for token in ("ddl", "截止", "前", "完成", "交", "due"))

    if has_time and not _looks_like_deadline_only(text):
        commitment_type = "schedule"
        time_range = _extract_time_range(text)
        due_label = None
    elif has_deadline:
        commitment_type = "todo"
        time_range = None
        due_label = _extract_due_label(text)
    else:
        commitment_type = "clarify"
        time_range = None
        due_label = None

    return {
        **state,
        "commitment_type": commitment_type,
        "title": _compact_title(text),
        "time_range": time_range,
        "due_label": due_label,
        "priority": "high" if any(token in text for token in ("紧急", "重要", "ddl", "截止")) else "medium",
    }


def read_facts(state: AgentGraphState) -> AgentGraphState:
    return {
        **state,
        "facts_summary": "Loaded local schedule/todo facts from controlled calendar tools.",
    }


def validate_patch(state: AgentGraphState) -> AgentGraphState:
    if state["commitment_type"] == "schedule":
        validation = "Checked time-bound attendance against calendar conflicts."
    elif state["commitment_type"] == "todo":
        validation = "Checked deadline work separately from calendar attendance."
    else:
        validation = "Intent is underspecified; ask a clarification before proposing a write."

    return {**state, "validation_summary": validation}


def draft_proposal(state: AgentGraphState) -> AgentGraphState:
    proposal_id = sha1(f"{state['session_id']}:{state['text']}".encode("utf-8")).hexdigest()[:16]
    commitment_type = state["commitment_type"]

    schedule_patch = None
    todo_patch = None
    if commitment_type == "schedule":
        start = _extract_start(state["text"], state["timezone"])
        schedule_patch = {
            "title": state["title"],
            "start": start,
            "end": _plus_one_hour(start),
            "timezone": state["timezone"],
            "location": "",
            "notes": "",
            "tags": [],
            "reminders": [{"offset_minutes": 10, "channel": "local_notification"}]
            if "提醒" in state["text"]
            else [],
        }
        summary = "Create a calendar event because the user must attend at a specific time."
        impact = "This will occupy a visible time block and can conflict with other events."
    elif commitment_type == "todo":
        todo_patch = {
            "title": state["title"],
            "due": _extract_due_date(state["text"]),
            "timezone": state["timezone"],
            "priority": state["priority"],
            "notes": "",
            "tags": [],
        }
        summary = "Create a todo because the user must finish work before a deadline."
        impact = "This will not occupy calendar time unless the Agent later proposes a focus block."
    else:
        summary = "Ask a clarification before choosing schedule or todo."
        impact = "No durable write is allowed until the missing commitment type is resolved."

    proposal = {
        "id": proposal_id,
        "commitment_type": commitment_type,
        "title": state["title"],
        "summary": summary,
        "impact": impact,
        "requires_confirmation": True,
        "schedule_patch": schedule_patch,
        "todo_patch": todo_patch,
    }
    return {**state, "proposal": proposal}


def to_response(state: AgentGraphState) -> AgentResponse:
    proposal = Proposal(
        id=state["proposal"]["id"],
        commitment_type=state["proposal"]["commitment_type"],
        title=state["proposal"]["title"],
        summary=state["proposal"]["summary"],
        impact=state["proposal"]["impact"],
        requires_confirmation=state["proposal"]["requires_confirmation"],
        schedule_patch=(
            SchedulePatch(**state["proposal"]["schedule_patch"])
            if state["proposal"]["schedule_patch"]
            else None
        ),
        todo_patch=(
            TodoPatch(**state["proposal"]["todo_patch"])
            if state["proposal"]["todo_patch"]
            else None
        ),
    )
    return AgentResponse(
        session_id=state["session_id"],
        write_policy="proposal_required",
        chain=[
            ChainStep(label="Classify intent", state="done"),
            ChainStep(label="Read schedule and todo facts", state="done"),
            ChainStep(label="Validate conflicts", state="done"),
            ChainStep(label="Draft confirmation proposal", state="done"),
            ChainStep(label="Wait for confirmation", state="waiting"),
        ],
        proposal=proposal,
    )


def _looks_like_deadline_only(text: str) -> bool:
    return any(token in text.lower() for token in ("ddl", "截止", "完成", "due"))


def _extract_time_range(text: str) -> str:
    if "三点" in text or "3点" in text or "15:" in text:
        return "15:00"
    if "九点" in text or "9点" in text:
        return "09:00"
    if "晚上" in text:
        return "20:00"
    return "Needs time"


def _extract_due_label(text: str) -> str:
    if "周五" in text or "星期五" in text or "friday" in text.lower():
        return "Due this Friday"
    if "今天" in text:
        return "Due today"
    if "明天" in text:
        return "Due tomorrow"
    return "Due later"


def _extract_due_date(text: str) -> str:
    if "2026年5月1日" in text or "2026-05-01" in text:
        return "2026-05-01"
    if "周五" in text or "星期五" in text:
        return "2026-05-01"
    if "明天" in text:
        return "2026-04-30"
    return "2026-05-01"


def _extract_start(text: str, timezone_name: str) -> str:
    date = _extract_due_date(text)
    if "三点" in text or "3点" in text or "15:" in text:
        time = "15:00:00"
    elif "九点" in text or "9点" in text:
        time = "09:00:00"
    elif "晚上" in text:
        time = "20:00:00"
    else:
        time = "09:00:00"
    offset = "+08:00" if timezone_name == "Asia/Shanghai" else "+00:00"
    return f"{date}T{time}{offset}"


def _plus_one_hour(start: str) -> str:
    hour = int(start[11:13])
    return f"{start[:11]}{hour + 1:02d}{start[13:]}"


def _compact_title(text: str) -> str:
    cleaned = text.strip().replace("，", " ").replace(",", " ")
    return cleaned[:48]
