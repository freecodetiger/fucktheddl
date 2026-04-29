from pathlib import Path

from fucktheddl_agent.model_gateway import ModelGateway
from fucktheddl_agent.schemas import AgentRequest, AgentResponse, ApplyResponse
from fucktheddl_agent.storage import ScheduleStore
from fucktheddl_agent.workflow import build_agent_graph, to_response


class AgentService:
    def __init__(self, data_root: Path, model_gateway: ModelGateway | None = None) -> None:
        self._graph = build_agent_graph()
        self._store = ScheduleStore(data_root)
        self._model_gateway = model_gateway

    def propose(self, request: AgentRequest) -> AgentResponse:
        model_extraction = self._model_gateway.extract_commitment(request.text) if self._model_gateway else None
        state = self._graph.invoke(
            {
                "text": request.text,
                "session_id": request.session_id,
                "timezone": request.timezone,
                "model_extraction": model_extraction,
            },
            config={"configurable": {"thread_id": request.session_id}},
        )
        response = to_response(state)
        self._store.save_proposal(response.proposal)
        return response

    def confirm(self, proposal_id: str) -> ApplyResponse | None:
        proposal = self._store.load_proposal(proposal_id)
        if proposal is None:
            return None
        result = self._store.apply_proposal(proposal)
        return ApplyResponse(
            status="applied",
            proposal_id=proposal_id,
            commitment_id=result.commitment_id,
            commitment_type=result.commitment_type,  # type: ignore[arg-type]
            file_path=str(result.file_path),
            commit_hash=result.commit_hash,
        )

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
