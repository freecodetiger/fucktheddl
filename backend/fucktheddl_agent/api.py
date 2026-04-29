from pathlib import Path

from fastapi import APIRouter, FastAPI, HTTPException

from fucktheddl_agent.config import load_settings
from fucktheddl_agent.model_gateway import ModelGateway
from fucktheddl_agent.schemas import (
    AgentRequest,
    AgentResponse,
    ApplyResponse,
    AsrSessionResponse,
    HealthResponse,
    ModelConfigResponse,
)
from fucktheddl_agent.service import AgentService


def create_app(data_root: Path | None = None) -> FastAPI:
    settings = load_settings(data_root)
    model_gateway = ModelGateway(settings.model)
    agent_service = AgentService(settings.data_root, model_gateway)
    app = FastAPI(title="fucktheddl Agent API", version="0.1.0")
    router = APIRouter()

    @router.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(
            status="ok",
            agent_framework="langgraph",
            write_policy="proposal_required",
            model=ModelConfigResponse(**model_gateway.health_payload()),
        )

    @router.post("/agent/propose", response_model=AgentResponse)
    def propose(request: AgentRequest) -> AgentResponse:
        return agent_service.propose(request)

    @router.post("/agent/confirm/{proposal_id}", response_model=ApplyResponse)
    def confirm(proposal_id: str) -> ApplyResponse:
        result = agent_service.confirm(proposal_id)
        if result is None:
            raise HTTPException(status_code=404, detail="Proposal not found")
        return result

    @router.post("/agent/undo/{commitment_id}", response_model=ApplyResponse)
    def undo(commitment_id: str) -> ApplyResponse:
        result = agent_service.undo(commitment_id)
        if result is None:
            raise HTTPException(status_code=404, detail="Commitment not found")
        return result

    @router.get("/asr/session", response_model=AsrSessionResponse)
    def asr_session() -> AsrSessionResponse:
        current_settings = load_settings(settings.data_root)
        if not current_settings.asr.configured:
            raise HTTPException(status_code=503, detail="Aliyun ASR credentials are not configured")
        return AsrSessionResponse(
            app_key=current_settings.asr.app_key or "",
            token=current_settings.asr.api_key or "",
            url=current_settings.asr.url,
            model="fun-asr-realtime-2025-09-15",
            sample_rate=current_settings.asr.sample_rate,
            service_type=current_settings.asr.service_type,
            expires_at=current_settings.asr.expires_at,
        )

    app.include_router(router)
    return app


app = create_app()
