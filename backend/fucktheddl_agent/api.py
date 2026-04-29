from fastapi import APIRouter, FastAPI, HTTPException

from fucktheddl_agent.schemas import AgentRequest, AgentResponse, HealthResponse
from fucktheddl_agent.service import AgentService


app = FastAPI(title="fucktheddl Agent API", version="0.1.0")
router = APIRouter()
agent_service = AgentService()


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        agent_framework="langgraph",
        write_policy="proposal_required",
    )


@router.post("/agent/propose", response_model=AgentResponse)
def propose(request: AgentRequest) -> AgentResponse:
    return agent_service.propose(request)


@router.post("/agent/confirm/{proposal_id}")
def confirm(proposal_id: str) -> dict[str, str]:
    raise HTTPException(status_code=404, detail="Proposal not found")


app.include_router(router)

