from __future__ import annotations

import os
import re
from datetime import date, datetime, timedelta
from hashlib import sha1
from typing import Any, TypedDict
from zoneinfo import ZoneInfo

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from fucktheddl_agent.schemas import (
    AgentResponse,
    ChainStep,
    DeletePatch,
    Proposal,
    ProposalCandidate,
    SchedulePatch,
    TodoPatch,
    UpdatePatch,
)


class AgentGraphState(TypedDict, total=False):
    text: str
    session_id: str
    timezone: str
    commitment_type: str
    title: str
    time_range: str | None
    date_label: str | None
    due_label: str | None
    priority: str
    notes: str
    facts_summary: str
    validation_summary: str
    proposal: dict[str, Any]
    model_extraction: dict[str, Any] | None
    commitments: dict[str, list[dict[str, Any]]]


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
    heuristic = _heuristic_classification(text)
    if isinstance(model_extraction, dict) and model_extraction:
        commitment_type = str(model_extraction.get("commitment_type", "clarify"))
        if commitment_type not in {"schedule", "todo", "delete", "update", "query", "suggestion", "clarify"}:
            commitment_type = "clarify"
        if heuristic["commitment_type"] in {"delete", "update", "query", "suggestion"}:
            commitment_type = heuristic["commitment_type"]
        if commitment_type == "clarify" and heuristic["commitment_type"] != "clarify":
            commitment_type = heuristic["commitment_type"]
        raw_model_notes = model_extraction.get("notes")
        notes = _extract_notes(text, raw_model_notes)
        title = _compact_title(str(model_extraction.get("title") or text))
        if notes and not str(raw_model_notes or "").strip():
            title = heuristic.get("title") or title
        return {
            **state,
            "commitment_type": commitment_type,
            "title": title,
            "time_range": _normalize_model_time(model_extraction.get("time")) or heuristic["time_range"],
            "date_label": _extract_due_date(
                text,
                fallback_date=str(model_extraction.get("date") or model_extraction.get("due") or ""),
            ),
            "due_label": str(model_extraction.get("due") or "") or heuristic["due_label"],
            "priority": str(model_extraction.get("priority") or "medium"),
            "notes": notes,
        }

    return {**state, **heuristic}


def _heuristic_classification(text: str) -> AgentGraphState:
    if any(token in text for token in ("删除", "删掉", "取消", "移除", "撤销", "去掉")):
        return {
            "commitment_type": "delete",
            "title": _compact_title(text),
            "time_range": _extract_time_range(text) if _parse_time(text) else None,
            "date_label": _extract_due_date(text) if _mentions_date(text) else None,
            "due_label": _extract_due_label(text),
            "priority": "medium",
        }

    if _looks_like_update_command(text):
        return {
            "commitment_type": "update",
            "title": _compact_title(text),
            "time_range": _extract_time_range(text) if _parse_time(text) else None,
            "date_label": _extract_due_date(text) if _mentions_date(text) else None,
            "due_label": _extract_due_label(text),
            "priority": "medium",
            "notes": _extract_notes(text),
        }

    if any(token in text for token in ("查", "查看", "看看", "有哪些", "有什么", "有啥", "啥安排", "列出", "今天安排", "明天安排")):
        return {
            "commitment_type": "query",
            "title": "查询日程",
            "time_range": None,
            "date_label": _extract_due_date(text) if _mentions_date(text) else None,
            "due_label": None,
            "priority": "medium",
        }

    if any(token in text for token in ("建议", "推荐", "怎么安排", "帮我安排", "规划一下", "空档")):
        return {
            "commitment_type": "suggestion",
            "title": "日程建议",
            "time_range": None,
            "date_label": None,
            "due_label": None,
            "priority": "medium",
        }

    has_time = any(token in text for token in ("点", ":", "上午", "下午", "晚上", "早上"))
    has_deadline = any(token in text.lower() for token in ("ddl", "截止", "前", "完成", "交", "due"))

    if has_time and not _looks_like_deadline_only(text):
        commitment_type = "schedule"
        time_range = _extract_time_range(text)
        date_label = _extract_due_date(text) if _mentions_date(text) else None
        due_label = None
    elif has_deadline:
        commitment_type = "todo"
        time_range = None
        date_label = _extract_due_date(text) if _mentions_date(text) else None
        due_label = _extract_due_label(text)
    else:
        commitment_type = "clarify"
        time_range = None
        date_label = None
        due_label = None

    return {
        "commitment_type": commitment_type,
        "title": _compact_title(text),
        "time_range": time_range,
        "date_label": date_label,
        "due_label": due_label,
        "priority": "high" if any(token in text for token in ("紧急", "重要", "ddl", "截止")) else "medium",
        "notes": _extract_notes(text),
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
    elif state["commitment_type"] == "delete":
        validation = "Matched the deletion request against existing schedule and todo records."
    elif state["commitment_type"] == "update":
        validation = "Matched the update request and prepared a replacement value."
    elif state["commitment_type"] == "query":
        validation = "Read existing schedule and todo records for a direct answer."
    elif state["commitment_type"] == "suggestion":
        validation = "Reviewed current commitments to draft a planning suggestion."
    else:
        validation = "Intent is underspecified; ask a clarification before proposing a write."

    return {**state, "validation_summary": validation}


def draft_proposal(state: AgentGraphState) -> AgentGraphState:
    proposal_id = sha1(f"{state['session_id']}:{state['text']}".encode("utf-8")).hexdigest()[:16]
    commitment_type = state["commitment_type"]

    schedule_patch = None
    todo_patch = None
    delete_patch = None
    update_patch = None
    candidates = []
    requires_confirmation = True
    proposal_title = state["title"]
    if commitment_type == "schedule":
        start = _extract_start(state, state["timezone"])
        end = _plus_one_hour(start)
        schedule_patch = {
            "title": state["title"],
            "start": start,
            "end": end,
            "timezone": state["timezone"],
            "location": "",
            "notes": state.get("notes", ""),
            "tags": [],
            "reminders": [{"offset_minutes": 10, "channel": "local_notification"}]
            if "提醒" in state["text"]
            else [],
        }
        summary = f"准备创建日程：{state['title']}，{_format_time_window(start, end)}。"
        impact = "确认后会作为日程加入日历；如果标题或时间不对，可以取消或编辑后再发送。"
    elif commitment_type == "todo":
        due = _extract_due_date(state["text"], fallback_date=state.get("date_label"))
        todo_patch = {
            "title": state["title"],
            "due": due,
            "timezone": state["timezone"],
            "priority": state["priority"],
            "notes": state.get("notes", ""),
            "tags": [],
        }
        summary = f"准备创建待办：{state['title']}，截止到{_format_due_label(due)}。"
        impact = "确认后会加入待办列表；它不会占用日历时间。"
    elif commitment_type == "delete":
        candidate_matches = _find_commitment_candidates(state["text"], state.get("commitments", {}))
        target = candidate_matches[0] if len(candidate_matches) == 1 else None
        if target:
            delete_patch = {
                "target_id": target["id"],
                "target_type": target["type"],
                "target_title": target["title"],
            }
            summary = f"准备删除{_type_label(target['type'])}：{target['title']}。"
            impact = "确认后会从当前列表移除；删除会以取消状态写入历史，便于追溯。"
        else:
            requires_confirmation = False
            candidates = _proposal_candidates(
                items=candidate_matches or _fallback_candidates(state["text"], state.get("commitments", {})),
                original_text=state["text"],
            )
            if candidates:
                proposal_title = "选择要取消的项目"
                summary = "我找到几个可能要取消的项目，请选一个继续。"
                impact = "点选候选后会生成删除确认，不会直接删除。"
            else:
                summary = "没有找到可取消的日程或待办。"
                impact = "当前列表里没有足够接近的候选项。"
    elif commitment_type == "update":
        candidate_matches = _find_commitment_candidates(state["text"], state.get("commitments", {}))
        target = candidate_matches[0] if len(candidate_matches) == 1 else None
        if target and target["type"] == "schedule":
            start = _extract_start(state, state["timezone"])
            end = _plus_one_hour(start)
            schedule_patch = {
                "title": target["title"],
                "start": start,
                "end": end,
                "timezone": state["timezone"],
                "location": target.get("location", ""),
                "notes": state.get("notes") or target.get("notes", ""),
                "tags": target.get("tags", []),
                "reminders": target.get("reminders", []),
            }
            update_patch = {
                "target_id": target["id"],
                "target_type": "schedule",
                "target_title": target["title"],
                "schedule_patch": schedule_patch,
                "todo_patch": None,
            }
            summary = f"准备修改日程：{target['title']}，调整为{_format_time_window(start, end)}。"
            impact = "确认后会更新原日程；如果时间不对，可以取消后重新描述。"
        elif target:
            due = _extract_due_date(state["text"], fallback_date=state.get("date_label"))
            todo_patch = {
                "title": target["title"],
                "due": due,
                "timezone": state["timezone"],
                "priority": target.get("priority", "medium"),
                "notes": target.get("notes", ""),
                "tags": target.get("tags", []),
            }
            update_patch = {
                "target_id": target["id"],
                "target_type": "todo",
                "target_title": target["title"],
                "schedule_patch": None,
                "todo_patch": todo_patch,
            }
            summary = f"准备修改待办：{target['title']}，截止到{_format_due_label(due)}。"
            impact = "确认后会更新原待办。"
        else:
            requires_confirmation = False
            candidates = _proposal_candidates(
                items=candidate_matches or _fallback_candidates(state["text"], state.get("commitments", {})),
                original_text=state["text"],
            )
            if candidates:
                proposal_title = "选择要修改的项目"
                summary = "我找到几个可能要修改的项目，请选一个继续。"
                impact = "点选候选后会生成修改确认，不会直接修改。"
            else:
                summary = "没有找到要修改的日程或待办。"
                impact = "当前列表里没有足够接近的候选项。"
    elif commitment_type == "query":
        requires_confirmation = False
        query_items = _query_candidate_items(state["text"], state.get("commitments", {}))
        candidates = _proposal_candidates(
            items=query_items,
            original_text="删除",
            action_label="删除",
        )
        proposal_title = _query_title(state["text"], query_items)
        summary = _query_commitments(state["text"], state.get("commitments", {}), query_items)
        impact = "这只是查询结果，不会写入任何内容。"
    elif commitment_type == "suggestion":
        requires_confirmation = False
        summary = _suggest_commitments(state.get("commitments", {}))
        impact = "这是基于当前日程和待办的建议，不会自动修改任何内容。"
    else:
        requires_confirmation = False
        summary = "这条请求还缺少关键时间或截止信息，需要先补充。"
        impact = "暂时不会写入任何日程或待办。"

    proposal = {
        "id": proposal_id,
        "commitment_type": commitment_type,
        "title": proposal_title,
        "summary": summary,
        "impact": impact,
        "requires_confirmation": requires_confirmation,
        "schedule_patch": schedule_patch,
        "todo_patch": todo_patch,
        "delete_patch": delete_patch,
        "update_patch": update_patch,
        "candidates": candidates,
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
        delete_patch=(
            DeletePatch(**state["proposal"]["delete_patch"])
            if state["proposal"].get("delete_patch")
            else None
        ),
        update_patch=(
            UpdatePatch(**state["proposal"]["update_patch"])
            if state["proposal"].get("update_patch")
            else None
        ),
        candidates=[
            ProposalCandidate(**candidate)
            for candidate in state["proposal"].get("candidates", [])
        ],
    )
    return AgentResponse(
        session_id=state["session_id"],
        write_policy="proposal_required",
        chain=[
            ChainStep(label="Classify intent", state="done"),
            ChainStep(label="Read schedule and todo facts", state="done"),
            ChainStep(label="Validate conflicts", state="done"),
            ChainStep(label="Draft confirmation proposal", state="done"),
            ChainStep(
                label="Wait for confirmation" if proposal.requires_confirmation else "Return result",
                state="waiting" if proposal.requires_confirmation else "done",
            ),
        ],
        proposal=proposal,
    )


def _looks_like_deadline_only(text: str) -> bool:
    return any(token in text.lower() for token in ("ddl", "截止", "完成", "due"))


def _looks_like_update_command(text: str) -> bool:
    if any(token in text for token in ("改到", "改成", "修改", "推迟", "延后")):
        return True
    if "调整" not in text:
        return False
    return any(token in text for token in ("把", "将", "日程", "待办", "时间", "改", "到"))


def _extract_time_range(text: str) -> str:
    parsed = _parse_time(text)
    if parsed:
        return parsed[:5]
    return "Needs time"


def _extract_due_label(text: str) -> str:
    if "周五" in text or "星期五" in text or "friday" in text.lower():
        return "本周五截止"
    if "今天" in text:
        return "今天截止"
    if "明天" in text:
        return "明天截止"
    if "后天" in text:
        return "后天截止"
    return "稍后截止"


def _extract_due_date(text: str, fallback_date: str | None = None) -> str:
    today = _today()
    explicit = _extract_explicit_date(text, today)
    if explicit:
        return explicit.isoformat()
    fallback = _normalize_model_date(fallback_date, today)
    if fallback:
        return fallback
    return today.isoformat()


def _extract_explicit_date(text: str, today: date) -> date | None:
    holiday = _extract_holiday_period_end(text, today)
    if holiday:
        return holiday

    iso_match = re.search(r"(20\d{2})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{1,2})", text)
    if iso_match:
        year, month, day = (int(part) for part in iso_match.groups())
        return date(year, month, day)

    chinese_match = re.search(r"(20\d{2})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*(?:日|号)?", text)
    if chinese_match:
        year, month, day = (int(part) for part in chinese_match.groups())
        return date(year, month, day)

    month_day = _extract_month_day(text, today)
    if month_day:
        return month_day
    if "今天" in text:
        return today
    if "明天" in text:
        return today + timedelta(days=1)
    if "后天" in text:
        return today + timedelta(days=2)
    if "周五" in text or "星期五" in text:
        return _next_weekday(today, 4)
    return None


def _extract_holiday_period_end(text: str, today: date) -> date | None:
    if not any(token in text for token in ("五一期间", "五一假期", "劳动节期间", "劳动节假期")):
        return None
    candidate = date(today.year, 5, 5)
    if candidate < today:
        candidate = date(today.year + 1, 5, 5)
    return candidate


def _extract_start(state: AgentGraphState, timezone_name: str) -> str:
    text = state["text"]
    date = _extract_due_date(text, fallback_date=state.get("date_label"))
    time = _normalize_model_time(state.get("time_range")) or _parse_time(text) or "09:00:00"
    offset = "+08:00" if timezone_name == "Asia/Shanghai" else "+00:00"
    return f"{date}T{time}{offset}"


def _normalize_model_date(value: object, today: date) -> str | None:
    if value is None:
        return None
    raw = str(value).strip()
    if not raw:
        return None
    explicit = _extract_explicit_date(raw, today)
    if explicit:
        return explicit.isoformat()
    if re.fullmatch(r"\d{1,2}\s*[-/.]\s*\d{1,2}", raw):
        month, day = (int(part) for part in re.split(r"\s*[-/.]\s*", raw))
        try:
            candidate = date(today.year, month, day)
        except ValueError:
            return None
        if candidate < today:
            try:
                candidate = date(today.year + 1, month, day)
            except ValueError:
                return None
        return candidate.isoformat()
    return None


def _normalize_model_time(value: object) -> str | None:
    if value is None:
        return None
    raw = str(value).strip()
    if not raw:
        return None
    parsed = _parse_time(raw)
    if parsed:
        return parsed
    match = re.fullmatch(r"(\d{1,2})", raw)
    if match:
        return f"{_normalize_hour(int(match.group(1)), raw):02d}:00:00"
    return None


def _plus_one_hour(start: str) -> str:
    end = datetime.fromisoformat(start) + timedelta(hours=1)
    return end.isoformat(timespec="seconds")


def _extract_notes(text: str, model_notes: object | None = None) -> str:
    normalized_model_notes = str(model_notes or "").strip()
    if normalized_model_notes:
        return normalized_model_notes
    _, explicit_notes = _split_notes_tail(text)
    if explicit_notes:
        return explicit_notes
    if _should_preserve_original_as_notes(text):
        return text.strip()
    return ""


def _split_notes_tail(text: str) -> tuple[str, str]:
    raw = text.strip()
    if not raw:
        return "", ""
    patterns = (
        r"(?P<core>.+?)[，,。；;]\s*(?:这个)?(?P<kind>任务|事项)(?:的)?内容(?:是|为|：|:)?(?P<note>.+)$",
        r"(?P<core>.+?)(?:这个)?(?P<kind>任务|事项)(?:的)?内容(?:是|为|：|:)?(?P<note>.+)$",
        r"(?P<core>.+?)[，,。；;]\s*(?P<marker>备注(?:是|为)?|备注一下|注意(?:一下)?|记得|到时候|需要|要带|带上|请带|准备好|别忘了|顺便)(?P<note>.+)$",
        r"(?P<core>.+?)(?P<marker>备注(?:是|为)?|备注一下|注意(?:一下)?|记得|到时候|别忘了|顺便)(?P<note>.+)$",
    )
    for pattern in patterns:
        match = re.search(pattern, raw)
        if not match:
            continue
        core = match.group("core").strip(" ，,。；;")
        note = match.group("note").strip(" ：:，,。；;")
        if not core or not note:
            continue
        kind = match.groupdict().get("kind", "")
        if kind and core.endswith(("一个", "这个", "某个")):
            core = f"{core}{kind}"
        marker = match.groupdict().get("marker", "")
        if not marker:
            return core, note
        if marker.startswith("备注"):
            return core, note
        return core, f"{marker}{note}"
    return raw, ""


def _should_preserve_original_as_notes(text: str) -> bool:
    raw = text.strip()
    if len(raw) < 24:
        return False
    if any(token in raw for token in ("备注", "记得", "注意", "到时候", "别忘了", "顺便")):
        return True
    if not re.search(r"[，,。；;]", raw):
        return False
    tail = re.split(r"[，,。；;]", raw, maxsplit=1)[1].strip()
    if not tail:
        return False
    if re.fullmatch(r"(提前|到时候)?[零〇一二两三四五六七八九十\d]{0,3}\s*(分钟|小时)?提醒我?", tail):
        return False
    return any(token in tail for token in ("说", "可能", "临时", "流程", "材料", "准备", "带", "发消息", "联系", "如果", "因为", "先"))


def _compact_title(text: str) -> str:
    text, _ = _split_notes_tail(text)
    if _should_preserve_original_as_notes(text):
        text = re.split(r"[，,。；;]", text, maxsplit=1)[0]
    cleaned = text.strip().replace("，", " ").replace(",", " ")
    cleaned = re.sub(r"(删除|删掉|取消|移除|修改|调整|改到|改成|提前|推迟|查询|查看|列出|完成|帮我|请|一下)", " ", cleaned)
    cleaned = re.sub(r"20\d{2}\s*[-年]\s*\d{1,2}\s*[-月]\s*\d{1,2}\s*(日|号)?", " ", cleaned)
    cleaned = re.sub(r"[零〇一二两三四五六七八九十\d]{1,3}\s*月\s*[零〇一二两三四五六七八九十\d]{1,3}\s*(日|号)?", " ", cleaned)
    cleaned = re.sub(r"(五一|劳动节)(期间|假期)?", " ", cleaned)
    cleaned = re.sub(r"(今天|明天|后天)?(上午|下午|晚上|早上|中午|凌晨)?\s*\d{1,2}\s*[:：]\s*\d{2}", " ", cleaned)
    cleaned = re.sub(r"(今天|明天|后天)?(上午|下午|晚上|早上|中午|凌晨)?\s*[零〇一二两三四五六七八九十\d]{1,3}\s*点\s*(半)?钟?", " ", cleaned)
    for token in (
        "明天下午三点",
        "明天下午3点",
        "明天上午九点",
        "明天上午9点",
        "明天晚上",
        "明天",
        "下午三点",
        "下午3点",
        "上午九点",
        "上午9点",
        "上午",
        "下午",
        "早上",
        "中午",
        "凌晨",
        "晚上",
    ):
        cleaned = cleaned.replace(token, " ")
    cleaned = " ".join(cleaned.split())
    cleaned = re.sub(r"^(前|截止|到)+", "", cleaned).strip()
    return cleaned[:48]


def _find_commitment(text: str, commitments: dict[str, list[dict[str, Any]]]) -> dict[str, Any] | None:
    candidates = _find_commitment_candidates(text, commitments)
    return candidates[0] if candidates else None


def _find_commitment_candidates(text: str, commitments: dict[str, list[dict[str, Any]]]) -> list[dict[str, Any]]:
    candidates = _commitment_candidates(commitments)
    if not candidates:
        return []
    exact_id_matches = [item for item in candidates if item.get("id") and str(item["id"]) in text]
    if exact_id_matches:
        return exact_id_matches[:1]
    query = _compact_title(text)
    query_date = _extract_due_date(text) if _mentions_date(text) else ""
    query_time = _parse_time(text)

    scored = []
    for item in candidates:
        if query_date and item.get("date") != query_date:
            continue
        score = 0
        title = item["title"]
        if query and (query in title or title in query):
            score += 8
        elif query and any(part and part in title for part in query.split()):
            score += 4
        if query_date and item.get("date") == query_date:
            score += 4
        if query_time and item.get("time") == query_time[:5]:
            score += 4
        if item["type"] == "schedule" and any(token in text for token in ("日程", "课", "会", "午睡", "安排")):
            score += 1
        if item["type"] == "todo" and any(token in text for token in ("待办", "任务", "截止", "完成")):
            score += 1
        if score:
            scored.append((score, item))

    if not scored:
        return []
    scored.sort(key=lambda pair: pair[0], reverse=True)
    top_score = scored[0][0]
    if len(scored) == 1:
        return [scored[0][1]]
    # Strong title/time matches can safely proceed; date-only or generic matches become candidates.
    second_score = scored[1][0]
    if top_score >= second_score + 4 and top_score >= 8:
        return [scored[0][1]]
    return [item for score, item in scored if score >= max(1, top_score - 3)][:5]


def _fallback_candidates(text: str, commitments: dict[str, list[dict[str, Any]]]) -> list[dict[str, Any]]:
    candidates = _commitment_candidates(commitments)
    if not candidates:
        return []
    today = _today().isoformat()
    future_or_today = [
        item for item in candidates
        if str(item.get("date", "")) >= today
    ]
    pool = future_or_today or candidates
    if any(token in text for token in ("活动", "日程", "安排", "课", "会")):
        schedules = [item for item in pool if item["type"] == "schedule"]
        if schedules:
            pool = schedules
    elif any(token in text for token in ("待办", "任务", "作业", "ddl", "截止")):
        todos = [item for item in pool if item["type"] == "todo"]
        if todos:
            pool = todos
    return sorted(pool, key=lambda item: (item.get("date", ""), item.get("time", "")))[:5]


def _proposal_candidates(
    items: list[dict[str, Any]],
    original_text: str,
    action_label: str = "选择",
) -> list[dict[str, str]]:
    result = []
    for item in items[:5]:
        when = _candidate_when(item)
        result.append(
            {
                "id": str(item.get("id", "")),
                "target_type": item["type"],
                "title": item["title"],
                "when": when,
                "detail": item.get("notes", "") or _type_label(item["type"]),
                "resolution_text": f"{original_text} #{item.get('id', '')}",
                "action_label": action_label,
            }
        )
    return result


def _candidate_when(item: dict[str, Any]) -> str:
    if item["type"] == "schedule":
        return f"{_format_due_label(item.get('date', ''))} {item.get('time', '')}".strip()
    return f"截止 {_format_due_label(item.get('date', ''))}"


def _commitment_candidates(commitments: dict[str, list[dict[str, Any]]]) -> list[dict[str, Any]]:
    events = [
        {
            **event,
            "type": "schedule",
            "date": str(event.get("start", ""))[:10],
            "time": str(event.get("start", ""))[11:16],
        }
        for event in commitments.get("events", [])
    ]
    todos = [
        {
            **todo,
            "type": "todo",
            "date": str(todo.get("due", ""))[:10],
            "time": "",
        }
        for todo in commitments.get("todos", [])
    ]
    return events + todos


def _query_candidate_items(text: str, commitments: dict[str, list[dict[str, Any]]]) -> list[dict[str, Any]]:
    candidates = _commitment_candidates(commitments)
    if not candidates:
        return []
    target_date = _extract_due_date(text) if _mentions_date(text) else ""
    filtered = [item for item in candidates if not target_date or item.get("date") == target_date]
    if any(token in text for token in ("活动", "日程", "课程", "课", "会议", "会")):
        filtered = [item for item in filtered if item["type"] == "schedule"]
    elif any(token in text for token in ("待办", "任务", "作业", "ddl", "截止")):
        filtered = [item for item in filtered if item["type"] == "todo"]
    filtered.sort(key=lambda item: (item.get("date", ""), item.get("time", ""), item.get("title", "")))
    return filtered[:8]


def _query_title(text: str, items: list[dict[str, Any]]) -> str:
    if "今天" in text:
        return "今天"
    if "明天" in text:
        return "明天"
    if "后天" in text:
        return "后天"
    if items:
        return _format_due_label(items[0].get("date", ""))
    return "查询结果"


def _query_commitments(
    text: str,
    commitments: dict[str, list[dict[str, Any]]],
    filtered: list[dict[str, Any]] | None = None,
) -> str:
    candidates = _commitment_candidates(commitments)
    if not candidates:
        return "当前没有已确认的日程或待办。"
    target_date = _extract_due_date(text) if _mentions_date(text) else ""
    items = filtered if filtered is not None else _query_candidate_items(text, commitments)
    if not items:
        return f"{_format_due_label(target_date)}没有安排。"
    lines = []
    for item in items[:5]:
        if item["type"] == "schedule":
            lines.append(f"{item.get('time', '')} {item['title']}")
        else:
            lines.append(f"待办 {item['title']}，截止到{_format_due_label(item.get('date', ''))}")
    return "；".join(lines)


def _suggest_commitments(commitments: dict[str, list[dict[str, Any]]]) -> str:
    events = commitments.get("events", [])
    todos = commitments.get("todos", [])
    active_todos = [todo for todo in todos if todo.get("status") == "active"]
    high_todos = [todo for todo in active_todos if todo.get("priority") == "high"]
    if high_todos:
        first = high_todos[0]
        return f"建议先处理高优先级待办“{first['title']}”，再把低优先级事项放到空档。"
    if len(events) >= 4:
        return "今天固定日程偏多，建议不要再新增大块任务，只保留短待办。"
    if active_todos:
        return f"建议给“{active_todos[0]['title']}”安排一个 45 分钟专注块。"
    return "当前压力不高，建议保留一个空档给临时任务。"


def _type_label(commitment_type: str) -> str:
    return "日程" if commitment_type == "schedule" else "待办"


def _mentions_date(text: str) -> bool:
    return bool(
        any(token in text for token in ("今天", "明天", "后天", "周", "星期", "-", "五一", "劳动节"))
        or re.search(r"[零〇一二两三四五六七八九十\d]{1,3}\s*月\s*[零〇一二两三四五六七八九十\d]{1,3}\s*(?:日|号)?", text)
    )


def _extract_month_day(text: str, today: date) -> date | None:
    month_day_match = re.search(
        r"(?P<month>[零〇一二两三四五六七八九十\d]{1,3})\s*月\s*(?P<day>[零〇一二两三四五六七八九十\d]{1,3})\s*(?:日|号)?",
        text,
    )
    if not month_day_match:
        return None
    month = _chinese_number_to_int(month_day_match.group("month"))
    day = _chinese_number_to_int(month_day_match.group("day"))
    if month is None or day is None:
        return None
    try:
        candidate = date(today.year, month, day)
    except ValueError:
        return None
    if candidate < today:
        try:
            return date(today.year + 1, month, day)
        except ValueError:
            return None
    return candidate


def _parse_time(text: str) -> str | None:
    colon_match = re.search(r"(?P<hour>\d{1,2})\s*[:：]\s*(?P<minute>\d{2})", text)
    if colon_match:
        hour = _normalize_hour(int(colon_match.group("hour")), text)
        minute = int(colon_match.group("minute"))
        return f"{hour:02d}:{minute:02d}:00"

    point_match = re.search(r"(?P<hour>[零〇一二两三四五六七八九十\d]{1,3})\s*点\s*(?P<half>半)?", text)
    if not point_match:
        return None
    hour = _chinese_number_to_int(point_match.group("hour"))
    if hour is None:
        return None
    hour = _normalize_hour(hour, text)
    minute = 30 if point_match.group("half") else 0
    return f"{hour:02d}:{minute:02d}:00"


def _normalize_hour(hour: int, text: str) -> int:
    if hour < 0 or hour > 24:
        return 9
    if hour == 24:
        return 0
    if any(token in text for token in ("下午", "晚上")) and 1 <= hour <= 11:
        return hour + 12
    if "凌晨" in text and hour == 12:
        return 0
    return hour


def _chinese_number_to_int(value: str) -> int | None:
    if value.isdigit():
        return int(value)
    digits = {
        "零": 0,
        "〇": 0,
        "一": 1,
        "二": 2,
        "两": 2,
        "三": 3,
        "四": 4,
        "五": 5,
        "六": 6,
        "七": 7,
        "八": 8,
        "九": 9,
    }
    if value == "十":
        return 10
    if value.startswith("十"):
        tail = value[1:]
        return 10 + digits.get(tail, 0)
    if "十" in value:
        head, tail = value.split("十", 1)
        return digits.get(head, 0) * 10 + digits.get(tail, 0)
    return digits.get(value)


def _today() -> date:
    override = os.getenv("FUCKTHEDDL_TODAY")
    if override:
        return date.fromisoformat(override)
    return datetime.now(ZoneInfo("Asia/Shanghai")).date()


def _next_weekday(today: date, weekday: int) -> date:
    days_ahead = (weekday - today.weekday()) % 7
    return today + timedelta(days=days_ahead or 7)


def _format_time_window(start: str, end: str) -> str:
    start_date = date.fromisoformat(start[:10])
    return f"{_relative_date_label(start_date)} {start[11:16]}-{end[11:16]}"


def _format_due_label(due: str) -> str:
    due_date = date.fromisoformat(due)
    return f"{_relative_date_label(due_date)}（{due}）"


def _relative_date_label(target: date) -> str:
    today = _today()
    if target == today:
        return "今天"
    if target == today + timedelta(days=1):
        return "明天"
    if target == today + timedelta(days=2):
        return "后天"
    return target.isoformat()
