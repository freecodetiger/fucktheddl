from pathlib import Path
from contextlib import asynccontextmanager

from fastapi import APIRouter, Depends, FastAPI, HTTPException, Request, Response, status

from fucktheddl_agent.auth import AuthService, UserContext, authenticate_request
from fucktheddl_agent.auth_store import AuthStore
from fucktheddl_agent.config import load_settings
from fucktheddl_agent.email_sender import EmailDeliveryError, FakeEmailSender, ResendEmailSender
from fucktheddl_agent.jobs import RedisAgentJobQueue
from fucktheddl_agent.model_gateway import ModelGateway
from fucktheddl_agent.schemas import (
    AgentJobAccepted,
    AgentJobStatus,
    AgentRequest,
    AgentResponse,
    ApplyResponse,
    AsrSessionResponse,
    AuthCodeRequest,
    AuthCodeVerifyRequest,
    AuthCodeVerifyResponse,
    CommitmentsResponse,
    HealthResponse,
    LogoutResponse,
    ModelConfigResponse,
    Proposal,
    ProposalEditRequest,
)
from fucktheddl_agent.service import AgentService


def create_app(data_root: Path | None = None, job_queue=None) -> FastAPI:
    settings = load_settings(data_root)
    model_gateway = ModelGateway(settings.model)
    agent_service = AgentService(settings.data_root, model_gateway)
    auth_store = AuthStore(settings.data_root / ".runtime" / "auth.sqlite3")
    email_sender = (
        ResendEmailSender(
            api_key=settings.email.resend_api_key,
            from_email=settings.email.resend_from_email,
            from_name=settings.email.resend_from_name,
        )
        if settings.email.configured
        else FakeEmailSender()
    )
    auth_service = AuthService(auth_store, email_sender, product_name="DDL Agent")
    queue = job_queue or RedisAgentJobQueue(settings.queue)
    processor = lambda user_id, request: agent_service.propose(request, user_id=user_id)
    if hasattr(queue, "bind_processor"):
        queue.bind_processor(processor)

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        if queue is not None and hasattr(queue, "start"):
            queue.start(processor)
        try:
            yield
        finally:
            if queue is not None and hasattr(queue, "shutdown"):
                queue.shutdown()

    app = FastAPI(title="fucktheddl Agent API", version="0.1.0", lifespan=lifespan)
    app.state.email_sender = email_sender
    router = APIRouter()

    def current_user(request: Request) -> UserContext:
        return authenticate_request(request, auth_service)

    @router.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(
            status="ok",
            agent_framework="langgraph",
            write_policy="proposal_required",
            model=ModelConfigResponse(**model_gateway.health_payload()),
        )

    @router.post("/auth/code/request", status_code=status.HTTP_204_NO_CONTENT)
    def request_auth_code(request: AuthCodeRequest) -> Response:
        try:
            auth_service.request_code(request.email)
        except EmailDeliveryError as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Email delivery failed: {exc}",
            ) from exc
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    @router.post("/auth/code/verify", response_model=AuthCodeVerifyResponse)
    def verify_auth_code(request: AuthCodeVerifyRequest) -> AuthCodeVerifyResponse:
        result = auth_service.verify_code(request.email, request.code)
        return AuthCodeVerifyResponse(
            user_id=result.user_id,
            email=result.email,
            access_token=result.access_token,
            newly_created=result.newly_created,
        )

    @router.post("/auth/logout", response_model=LogoutResponse)
    def logout(user: UserContext = Depends(current_user)) -> LogoutResponse:
        if user.token:
            auth_service.logout(user.token)
        return LogoutResponse(status="logged_out")

    @router.post("/agent/propose", response_model=AgentJobAccepted)
    def propose(
        request: AgentRequest,
        response: Response,
        user: UserContext = Depends(current_user),
    ) -> AgentJobAccepted:
        response.status_code = status.HTTP_202_ACCEPTED
        return AgentJobAccepted.model_validate(queue.submit(user.user_id, request.model_dump(mode="json")))

    @router.get("/agent/jobs/{job_id}", response_model=AgentJobStatus)
    def get_agent_job(job_id: str, user: UserContext = Depends(current_user)) -> AgentJobStatus:
        payload = queue.get(user.user_id, job_id)
        if payload is None:
            raise HTTPException(status_code=404, detail="Job not found")
        return AgentJobStatus.model_validate(payload)

    @router.get("/commitments", response_model=CommitmentsResponse)
    def commitments(_user: UserContext = Depends(current_user)) -> CommitmentsResponse:
        return agent_service.commitments()

    @router.post("/agent/confirm/{proposal_id}", response_model=ApplyResponse)
    def confirm(proposal_id: str, _user: UserContext = Depends(current_user)) -> ApplyResponse:
        # Compatibility endpoint. Android applies confirmed proposals into local Room.
        # This endpoint remains only for older clients and tests during migration.
        result = agent_service.confirm(proposal_id)
        if result is None:
            raise HTTPException(status_code=404, detail="Proposal not found")
        return result

    @router.post("/agent/proposal/{proposal_id}/edit", response_model=Proposal)
    def edit_proposal(
        proposal_id: str,
        request: ProposalEditRequest,
        _user: UserContext = Depends(current_user),
    ) -> Proposal:
        result = agent_service.edit_proposal(proposal_id, request)
        if result is None:
            raise HTTPException(status_code=404, detail="Editable proposal not found")
        return result

    @router.post("/agent/undo/{commitment_id}", response_model=ApplyResponse)
    def undo(commitment_id: str, _user: UserContext = Depends(current_user)) -> ApplyResponse:
        result = agent_service.undo(commitment_id)
        if result is None:
            raise HTTPException(status_code=404, detail="Commitment not found")
        return result

    @router.get("/asr/session", response_model=AsrSessionResponse)
    def asr_session(_user: UserContext = Depends(current_user)) -> AsrSessionResponse:
        current_settings = load_settings(settings.data_root)
        if not current_settings.asr.configured:
            raise HTTPException(status_code=503, detail="Aliyun ASR credentials are not configured")
        return AsrSessionResponse(
            api_key=current_settings.asr.api_key or "",
            url=current_settings.asr.url,
            model="fun-asr-realtime-2025-09-15",
            sample_rate=current_settings.asr.sample_rate,
            service_type=current_settings.asr.service_type,
            expires_at=current_settings.asr.expires_at,
        )

    app.include_router(router)
    return app


app = create_app()
