from datetime import date
from pathlib import Path

from fucktheddl_agent.config import ModelSettings
from fucktheddl_agent.model_gateway import ModelGateway
from fucktheddl_agent.schemas import AgentRequest, AgentResponse, ApplyResponse, CommitmentsResponse, Proposal, ProposalEditRequest
from fucktheddl_agent.storage import ScheduleStore
from fucktheddl_agent.workflow import build_agent_graph, to_response


class AgentService:
    def __init__(self, data_root: Path, model_gateway: ModelGateway | None = None) -> None:
        self._graph = build_agent_graph()
        self._store = ScheduleStore(data_root)
        self._model_gateway = model_gateway

    def propose(self, request: AgentRequest, user_id: str = "anonymous") -> AgentResponse:
        server_disabled = (
            self._model_gateway is None
            or not self._model_gateway.settings.enabled
        )
        if server_disabled and not request.model_api_key:
            from fastapi import HTTPException, status
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="请在设置中填入你的 DeepSeek API Key",
            )
        model_extraction = self._model_gateway.extract_commitment(
            request.text,
            settings=_request_model_settings(request),
        ) if self._model_gateway else None
        commitments = request.commitments if request.commitments is not None else self._store.list_commitments()
        thread_id = f"{user_id}:{request.session_id}"
        state = self._graph.invoke(
            {
                "text": request.text,
                "session_id": request.session_id,
                "timezone": request.timezone,
                "model_extraction": model_extraction,
                "commitments": commitments,
            },
            config={"configurable": {"thread_id": thread_id}},
        )
        response = to_response(state)
        if request.commitments is None:
            self._store.save_proposal(response.proposal)
        return response

    def confirm(self, proposal_id: str) -> ApplyResponse | None:
        proposal = self._store.load_proposal(proposal_id)
        if proposal is None:
            return None
        if not proposal.requires_confirmation:
            return None
        try:
            result = self._store.apply_proposal(proposal)
        except ValueError:
            return None
        return ApplyResponse(
            status="applied",
            proposal_id=proposal_id,
            commitment_id=result.commitment_id,
            commitment_type=result.commitment_type,  # type: ignore[arg-type]
            file_path=str(result.file_path),
            commit_hash=result.commit_hash,
        )

    def edit_proposal(self, proposal_id: str, request: ProposalEditRequest) -> Proposal | None:
        proposal = self._store.load_proposal(proposal_id)
        if proposal is None or not proposal.requires_confirmation:
            return None
        if proposal.commitment_type == "schedule" and request.schedule_patch is not None:
            proposal.schedule_patch = request.schedule_patch
            proposal.title = request.title or request.schedule_patch.title
            proposal.summary = request.summary or _schedule_summary(request.schedule_patch.title, request.schedule_patch.start, request.schedule_patch.end)
        elif proposal.commitment_type == "todo" and request.todo_patch is not None:
            proposal.todo_patch = request.todo_patch
            proposal.title = request.title or request.todo_patch.title
            proposal.summary = request.summary or _todo_summary(request.todo_patch.title, request.todo_patch.due)
        elif proposal.commitment_type == "update" and proposal.update_patch is not None and request.schedule_patch is not None:
            proposal.schedule_patch = request.schedule_patch
            proposal.update_patch.schedule_patch = request.schedule_patch
            proposal.update_patch.todo_patch = None
            proposal.title = request.title or request.schedule_patch.title
            proposal.summary = request.summary or _update_schedule_summary(
                proposal.update_patch.target_title,
                request.schedule_patch.start,
                request.schedule_patch.end,
            )
        elif proposal.commitment_type == "update" and proposal.update_patch is not None and request.todo_patch is not None:
            proposal.todo_patch = request.todo_patch
            proposal.update_patch.todo_patch = request.todo_patch
            proposal.update_patch.schedule_patch = None
            proposal.title = request.title or request.todo_patch.title
            proposal.summary = request.summary or _update_todo_summary(
                proposal.update_patch.target_title,
                request.todo_patch.due,
            )
        else:
            return None
        self._store.save_proposal(proposal)
        return proposal

    def undo(self, commitment_id: str) -> ApplyResponse | None:
        result = self._store.undo_commitment(commitment_id)
        if result is None:
            return None
        return ApplyResponse(
            status="undone",
            proposal_id=None,
            commitment_id=result.commitment_id,
            commitment_type=result.commitment_type,  # type: ignore[arg-type]
            file_path=str(result.file_path),
            commit_hash=result.commit_hash,
        )

    def commitments(self) -> CommitmentsResponse:
        return CommitmentsResponse(**self._store.list_commitments())


def _schedule_summary(title: str, start: str, end: str) -> str:
    return f"准备创建日程：{title}，{_date_time_label(start, end)}。"


def _request_model_settings(request: AgentRequest) -> ModelSettings | None:
    if not request.model_api_key:
        return None
    return ModelSettings(
        api_key=request.model_api_key,
        base_url=request.model_base_url or "https://api.deepseek.com/v1",
        model=request.model or "deepseek-v4-flash",
        enabled=True,
        disable_thinking=request.disable_thinking,
    )


def _todo_summary(title: str, due: str) -> str:
    return f"准备创建待办：{title}，截止到{due}。"


def _update_schedule_summary(title: str, start: str, end: str) -> str:
    return f"准备修改日程：{title}，调整为{_date_time_label(start, end)}。"


def _update_todo_summary(title: str, due: str) -> str:
    return f"准备修改待办：{title}，截止到{due}。"


def _date_time_label(start: str, end: str) -> str:
    try:
        start_date = date.fromisoformat(start[:10])
        return f"{start_date.isoformat()} {start[11:16]}-{end[11:16]}"
    except ValueError:
        return f"{start} - {end}"
