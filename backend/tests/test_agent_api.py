import os
import subprocess

from fastapi.testclient import TestClient

from fucktheddl_agent.api import create_app
from fucktheddl_agent.config import ModelSettings
from fucktheddl_agent.model_gateway import ModelGateway
from fucktheddl_agent.schemas import AgentJobAccepted, AgentJobStatus, AgentRequest
from fucktheddl_agent.workflow import build_agent_graph, classify_intent


class AgentTestResponse:
    def __init__(self, status_code, body):
        self.status_code = status_code
        self._body = body

    def json(self):
        return self._body


class InlineAgentJobQueue:
    def __init__(self):
        self._processor = None
        self._jobs = {}
        self._next = 0

    def bind_processor(self, processor):
        self._processor = processor

    def submit(self, user_id, request):
        assert self._processor is not None
        self._next += 1
        job_id = f"test-job-{self._next}"
        try:
            response = self._processor(user_id, AgentRequest.model_validate(request))
            self._jobs[job_id] = AgentJobStatus(
                job_id=job_id,
                status="succeeded",
                response=response,
                error=None,
            ).model_dump(mode="json")
        except Exception as error:
            self._jobs[job_id] = AgentJobStatus(
                job_id=job_id,
                status="failed",
                response=None,
                error=str(error),
            ).model_dump(mode="json")
        return AgentJobAccepted(job_id=job_id, status="queued").model_dump()

    def get(self, user_id, job_id):
        return self._jobs.get(job_id)


class AgentTestClient:
    def __init__(self, client, headers=None):
        self._client = client
        self._headers = headers or {}

    def post(self, path, **kwargs):
        kwargs = self._with_headers(kwargs)
        response = self._client.post(path, **kwargs)
        if path != "/agent/propose" or response.status_code != 202:
            return response
        job_id = response.json()["job_id"]
        status = self._client.get(f"/agent/jobs/{job_id}", headers=kwargs.get("headers"))
        if status.status_code != 200:
            return status
        body = status.json()
        if body["status"] == "succeeded":
            return AgentTestResponse(200, body["response"])
        return AgentTestResponse(500, body)

    def get(self, path, **kwargs):
        kwargs = self._with_headers(kwargs)
        return self._client.get(path, **kwargs)

    def _with_headers(self, kwargs):
        if not self._headers:
            return kwargs
        headers = dict(self._headers)
        headers.update(kwargs.get("headers") or {})
        return {**kwargs, "headers": headers}


def make_client(tmp_path, monkeypatch):
    monkeypatch.delenv("RESEND_API_KEY", raising=False)
    monkeypatch.delenv("RESEND_FROM_EMAIL", raising=False)
    monkeypatch.delenv("RESEND_FROM_NAME", raising=False)
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    monkeypatch.setenv("OPENAI_BASE_URL", "https://codex.example/v1")
    monkeypatch.setenv("OPENAI_MODEL", "deepseek-v4-flash")
    monkeypatch.setenv("OPENAI_DISABLE_THINKING", "true")
    if "FUCKTHEDDL_TODAY" not in os.environ:
        monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-29")
    app = create_app(data_root=tmp_path, job_queue=InlineAgentJobQueue())
    client = TestClient(app)
    token = login_test_user(client, app, "user@example.com")
    return AgentTestClient(client, headers={"Authorization": f"Bearer {token}"})


def login_test_user(client, app, email="user@example.com"):
    request = client.post("/auth/code/request", json={"email": email})
    assert request.status_code == 204
    code = app.state.email_sender.sent[-1].code
    verify = client.post("/auth/code/verify", json={"email": email, "code": code})
    assert verify.status_code == 200
    return verify.json()["access_token"]


def test_health_reports_agent_backend_ready(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["agent_framework"] == "langgraph"
    assert body["write_policy"] == "proposal_required"
    assert body["model"]["base_url"] == "https://codex.example/v1"
    assert body["model"]["model"] == "deepseek-v4-flash"
    assert body["model"]["disable_thinking"] is True


def test_auth_code_routes_are_public_and_agent_requires_token(tmp_path, monkeypatch):
    monkeypatch.delenv("RESEND_API_KEY", raising=False)
    monkeypatch.delenv("RESEND_FROM_EMAIL", raising=False)
    monkeypatch.delenv("RESEND_FROM_NAME", raising=False)
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    monkeypatch.setenv("OPENAI_BASE_URL", "https://codex.example/v1")
    monkeypatch.setenv("OPENAI_MODEL", "deepseek-v4-flash")
    app = create_app(data_root=tmp_path, job_queue=InlineAgentJobQueue())
    client = TestClient(app)

    code_response = client.post("/auth/code/request", json={"email": "user@example.com"})
    missing = client.post(
        "/agent/propose",
        json={"text": "明天下午三点开会", "session_id": "auth-missing"},
    )
    wrong = client.post(
        "/agent/propose",
        headers={"Authorization": "Bearer wrong-token"},
        json={"text": "明天下午三点开会", "session_id": "auth-wrong"},
    )

    assert code_response.status_code == 204
    assert missing.status_code == 401
    assert wrong.status_code == 401


def test_verified_email_token_can_call_protected_routes(tmp_path, monkeypatch):
    monkeypatch.delenv("RESEND_API_KEY", raising=False)
    monkeypatch.delenv("RESEND_FROM_EMAIL", raising=False)
    monkeypatch.delenv("RESEND_FROM_NAME", raising=False)
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    monkeypatch.setenv("OPENAI_BASE_URL", "https://codex.example/v1")
    monkeypatch.setenv("OPENAI_MODEL", "deepseek-v4-flash")
    app = create_app(data_root=tmp_path, job_queue=InlineAgentJobQueue())
    client = TestClient(app)
    token = login_test_user(client, app, "user@example.com")

    ok = client.post(
        "/agent/propose",
        headers={"Authorization": f"Bearer {token}"},
        json={"text": "明天下午三点开会", "session_id": "registered-token"},
    )

    assert ok.status_code == 202


def test_agent_propose_can_enqueue_redis_backed_job(tmp_path, monkeypatch):
    monkeypatch.delenv("RESEND_API_KEY", raising=False)
    monkeypatch.delenv("RESEND_FROM_EMAIL", raising=False)
    monkeypatch.delenv("RESEND_FROM_NAME", raising=False)

    class FakeQueue:
        def __init__(self):
            self.submitted = None

        def submit(self, user_id, request):
            self.submitted = (user_id, request)
            return {"job_id": "job-1", "status": "queued"}

        def get(self, user_id, job_id):
            return {
                "job_id": job_id,
                "status": "succeeded",
                "response": {
                    "session_id": "queued-session",
                    "write_policy": "proposal_required",
                    "chain": [],
                    "proposal": {
                        "id": "proposal-1",
                        "commitment_type": "clarify",
                        "title": "已排队",
                        "summary": "已完成",
                        "impact": "",
                        "requires_confirmation": False,
                        "schedule_patch": None,
                        "todo_patch": None,
                        "delete_patch": None,
                        "update_patch": None,
                        "candidates": [],
                    },
                },
                "error": None,
            }

    queue = FakeQueue()
    app = create_app(data_root=tmp_path, job_queue=queue)
    client = TestClient(app)
    token = login_test_user(client, app, "alice@example.com")

    accepted = client.post(
        "/agent/propose",
        headers={"X-Agent-Token": token},
        json={"text": "明天下午三点开会", "session_id": "queued-session"},
    )
    status = client.get("/agent/jobs/job-1", headers={"Authorization": f"Bearer {token}"})

    assert accepted.status_code == 202
    assert accepted.json() == {"job_id": "job-1", "status": "queued"}
    assert queue.submitted[0].startswith("usr_")
    assert queue.submitted[1]["session_id"] == "queued-session"
    assert status.status_code == 200
    assert status.json()["response"]["proposal"]["title"] == "已排队"


def test_schedule_request_returns_confirmation_gated_schedule_proposal(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "2026年5月1日下午三点开项目复盘会，提前十分钟提醒我",
            "session_id": "test-schedule",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["proposal"]["commitment_type"] == "schedule"
    assert body["proposal"]["requires_confirmation"] is True
    assert body["proposal"]["schedule_patch"]["start"] == "2026-05-01T15:00:00+08:00"
    assert body["proposal"]["schedule_patch"]["reminders"][0]["offset_minutes"] == 10
    assert body["write_policy"] == "proposal_required"
    assert [step["label"] for step in body["chain"]] == [
        "Classify intent",
        "Read schedule and todo facts",
        "Validate conflicts",
        "Draft confirmation proposal",
        "Wait for confirmation",
    ]
    proposal_id = body["proposal"]["id"]
    assert (tmp_path / ".runtime" / "proposals" / f"{proposal_id}.json").exists()


def test_noon_nap_request_keeps_requested_time_and_friendly_chinese_summary(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "明天12点午睡",
            "session_id": "test-noon-nap",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "schedule"
    assert proposal["title"] == "午睡"
    assert proposal["schedule_patch"]["start"] == "2026-05-01T12:00:00+08:00"
    assert proposal["schedule_patch"]["end"] == "2026-05-01T13:00:00+08:00"
    assert "午睡" in proposal["summary"]
    assert "明天 12:00-13:00" in proposal["summary"]
    assert "日程" in proposal["impact"]


def test_day_after_tomorrow_noon_request_uses_day_after_tomorrow(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "后天12点午睡",
            "session_id": "test-day-after-tomorrow-noon-nap",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["title"] == "午睡"
    assert proposal["schedule_patch"]["start"] == "2026-05-02T12:00:00+08:00"
    assert "后天 12:00-13:00" in proposal["summary"]


def test_tomorrow_morning_geography_class_stays_on_tomorrow_timeline(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "明天上午九点钟上地理课",
            "session_id": "test-tomorrow-geography-class",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "schedule"
    assert proposal["title"] == "上地理课"
    assert proposal["schedule_patch"]["start"] == "2026-05-01T09:00:00+08:00"
    assert "明天 09:00-10:00" in proposal["summary"]


def test_month_day_morning_event_uses_spoken_calendar_date(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "5月6号上午有一个活动",
            "session_id": "test-month-day-morning-event",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "schedule"
    assert proposal["title"] == "有一个活动"
    assert proposal["schedule_patch"]["start"] == "2026-05-06T09:00:00+08:00"
    assert "2026-05-06 09:00-10:00" in proposal["summary"]


def test_model_extracted_calendar_date_is_not_dropped(monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    state = build_agent_graph().invoke(
        {
            "text": "上午有一个金山的面试",
            "session_id": "test-model-date-not-dropped",
            "timezone": "Asia/Shanghai",
            "model_extraction": {
                "commitment_type": "schedule",
                "title": "金山面试",
                "date": "2026-05-06",
                "time": "09:00",
                "priority": "medium",
            },
            "commitments": {"events": [], "todos": []},
        },
        config={"configurable": {"thread_id": "test-model-date-not-dropped"}},
    )

    assert state["proposal"]["schedule_patch"]["start"] == "2026-05-06T09:00:00+08:00"


def test_spaced_asr_month_day_text_uses_spoken_calendar_date(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "5 月 6 号上午有一个金山的面试",
            "session_id": "test-spaced-asr-month-day",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["schedule_patch"]["start"] == "2026-05-06T09:00:00+08:00"


def test_complex_schedule_request_splits_remark_into_notes(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "5月6号上午九点有一个金山面试，记得带简历和作品集，到了先给HR发消息",
            "session_id": "test-schedule-notes-split",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "schedule"
    assert proposal["title"] == "有一个金山面试"
    assert proposal["schedule_patch"]["notes"] == "记得带简历和作品集，到了先给HR发消息"
    assert proposal["schedule_patch"]["start"] == "2026-05-06T09:00:00+08:00"


def test_complex_schedule_request_falls_back_to_original_text_as_notes(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)
    text = "5月6号上午九点有一个金山面试，王总说可能会临时调整流程"

    response = client.post(
        "/agent/propose",
        json={
            "text": text,
            "session_id": "test-schedule-notes-original-fallback",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "schedule"
    assert proposal["title"] == "有一个金山面试"
    assert proposal["schedule_patch"]["notes"] == text


def test_model_extracted_schedule_notes_are_preserved(monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    state = build_agent_graph().invoke(
        {
            "text": "明天下午三点面试，备注带简历",
            "session_id": "test-model-notes",
            "timezone": "Asia/Shanghai",
            "model_extraction": {
                "commitment_type": "schedule",
                "title": "面试",
                "date": "2026-05-01",
                "time": "15:00",
                "notes": "带简历",
            },
            "commitments": {"events": [], "todos": []},
        },
        config={"configurable": {"thread_id": "test-model-notes"}},
    )

    assert state["proposal"]["title"] == "面试"
    assert state["proposal"]["schedule_patch"]["notes"] == "带简历"


def test_explicit_notes_from_text_repair_incomplete_model_extraction(monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    state = classify_intent(
        {
            "text": "五一期间完成某项任务这个任务的内容是使用人工智能识别人物面部表情并做成一个后端接口",
            "session_id": "test-model-title-repair",
            "timezone": "Asia/Shanghai",
            "model_extraction": {
                "commitment_type": "todo",
                "title": "使用人工智能识别人物面部表情并做成一个后端接口",
                "due": "2026-05-05",
                "priority": "medium",
                "notes": "",
            },
            "commitments": {"events": [], "todos": []},
        },
    )

    assert state["title"] == "某项任务"
    assert state["notes"] == "使用人工智能识别人物面部表情并做成一个后端接口"


def test_model_gateway_accepts_fenced_json(monkeypatch):
    class FakeResponse:
        content = """```json
{"commitment_type":"todo","title":"某项任务","due":"2026-05-05","priority":"medium","notes":"接口说明"}
```"""

    class FakeModel:
        def invoke(self, _messages):
            return FakeResponse()

    monkeypatch.setattr(ModelGateway, "build_chat_model", lambda self, settings=None: FakeModel())
    gateway = ModelGateway(
        ModelSettings(
            api_key="test-key",
            base_url="https://codex.example/v1",
            model="deepseek-v4-flash",
            enabled=True,
            disable_thinking=True,
        ),
    )

    parsed = gateway.extract_commitment("五一期间完成某项任务")

    assert parsed == {
        "commitment_type": "todo",
        "title": "某项任务",
        "due": "2026-05-05",
        "priority": "medium",
        "notes": "接口说明",
    }


def test_deadline_request_returns_todo_proposal_not_calendar_event(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "2026年5月1日前完成安卓原生骨架",
            "session_id": "test-todo",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "todo"
    assert proposal["schedule_patch"] is None
    assert proposal["todo_patch"]["due"] == "2026-05-01"


def test_labor_day_task_content_goes_to_todo_notes(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "五一期间完成某项任务，这个任务的内容是使用人工智能识别人物面部表情，并做成一个后端接口",
            "session_id": "test-labor-day-task-notes",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "todo"
    assert proposal["title"] == "某项任务"
    assert proposal["todo_patch"]["due"] == "2026-05-05"
    assert proposal["todo_patch"]["notes"] == "使用人工智能识别人物面部表情，并做成一个后端接口"


def test_asr_task_content_without_punctuation_goes_to_todo_notes(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "五一期间完成某项任务这个任务的内容是使用人工智能识别人物面部表情并做成一个后端接口",
            "session_id": "test-asr-task-notes-without-punctuation",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "todo"
    assert proposal["title"] == "某项任务"
    assert proposal["todo_patch"]["due"] == "2026-05-05"
    assert proposal["todo_patch"]["notes"] == "使用人工智能识别人物面部表情并做成一个后端接口"


def test_local_rules_override_uncertain_model_classification_for_clear_deadline():
    state = classify_intent(
        {
            "text": "明天截止，完成安卓后端冒烟测试",
            "session_id": "test",
            "timezone": "Asia/Shanghai",
            "model_extraction": {
                "commitment_type": "clarify",
                "title": "完成安卓后端冒烟测试",
            },
        },
    )

    assert state["commitment_type"] == "todo"
    assert state["due_label"] == "明天截止"


def test_model_can_classify_non_create_agent_actions(monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    state = classify_intent(
        {
            "text": "帮我处理一下那个安排",
            "session_id": "test",
            "timezone": "Asia/Shanghai",
            "model_extraction": {
                "commitment_type": "query",
                "title": "查询安排",
            },
        },
    )

    assert state["commitment_type"] == "query"


def test_confirming_unknown_proposal_does_not_write_anything(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post("/agent/confirm/missing-proposal")

    assert response.status_code == 404
    assert response.json()["detail"] == "Proposal not found"


def test_confirming_schedule_proposal_writes_month_file_and_git_commit(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "2026年5月1日下午三点开项目复盘会，提前十分钟提醒我",
            "session_id": "confirm-schedule",
        },
    )
    proposal_id = response.json()["proposal"]["id"]

    confirm = client.post(f"/agent/confirm/{proposal_id}")

    assert confirm.status_code == 200
    body = confirm.json()
    assert body["status"] == "applied"
    assert body["commit_hash"]
    schedule_file = tmp_path / "schedules" / "2026-05.json"
    assert schedule_file.exists()
    assert "项目复盘会" in schedule_file.read_text(encoding="utf-8")
    git_log = subprocess.check_output(
        ["git", "-C", str(tmp_path), "log", "--oneline", "-1"],
        text=True,
    )
    assert body["commit_hash"] in git_log


def test_editing_schedule_proposal_updates_patch_before_confirmation(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "5月6号上午有一个金山的面试",
            "session_id": "test-edit-schedule-proposal",
        },
    )
    proposal = response.json()["proposal"]

    edited = client.post(
        f"/agent/proposal/{proposal['id']}/edit",
        json={
            "schedule_patch": {
                **proposal["schedule_patch"],
                "title": "金山云面试",
                "start": "2026-05-06T10:30:00+08:00",
                "end": "2026-05-06T11:30:00+08:00",
                "notes": "带简历",
            },
        },
    )

    assert edited.status_code == 200
    assert edited.json()["schedule_patch"]["title"] == "金山云面试"
    assert edited.json()["schedule_patch"]["start"] == "2026-05-06T10:30:00+08:00"

    confirm = client.post(f"/agent/confirm/{proposal['id']}")
    assert confirm.status_code == 200
    events = client.get("/commitments").json()["events"]
    assert events[0]["title"] == "金山云面试"
    assert events[0]["start"] == "2026-05-06T10:30:00+08:00"
    assert events[0]["notes"] == "带简历"


def test_commitments_returns_confirmed_schedule_after_agent_confirmation(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "明天下午三点去电玩城",
            "session_id": "confirm-arcade",
        },
    )
    proposal_id = response.json()["proposal"]["id"]
    confirm = client.post(f"/agent/confirm/{proposal_id}")
    assert confirm.status_code == 200

    commitments = client.get("/commitments")

    assert commitments.status_code == 200
    body = commitments.json()
    assert body["events"][0]["title"] == "去电玩城"
    assert body["events"][0]["start"] == "2026-04-30T15:00:00+08:00"
    assert body["events"][0]["status"] == "confirmed"
    assert body["todos"] == []


def test_delete_schedule_request_targets_existing_commitment_instead_of_creating(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "明天下午三点去电玩城",
            "session_id": "create-before-delete",
        },
    )
    proposal_id = response.json()["proposal"]["id"]
    client.post(f"/agent/confirm/{proposal_id}")

    delete = client.post(
        "/agent/propose",
        json={
            "text": "删除明天下午三点的电玩城日程",
            "session_id": "delete-arcade",
        },
    )

    assert delete.status_code == 200
    proposal = delete.json()["proposal"]
    assert proposal["commitment_type"] == "delete"
    assert proposal["schedule_patch"] is None
    assert proposal["delete_patch"]["target_title"] == "去电玩城"
    assert "准备删除日程" in proposal["summary"]

    confirm = client.post(f"/agent/confirm/{proposal['id']}")
    assert confirm.status_code == 200
    assert client.get("/commitments").json()["events"] == []


def test_fuzzy_delete_returns_candidates_instead_of_clarifying(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)
    for text, session in (
        ("今天上午九点有个活动", "create-morning-activity"),
        ("今天下午三点有个活动", "create-afternoon-activity"),
    ):
        response = client.post("/agent/propose", json={"text": text, "session_id": session})
        client.post(f"/agent/confirm/{response.json()['proposal']['id']}")

    fuzzy = client.post(
        "/agent/propose",
        json={"text": "今天取消某项活动", "session_id": "fuzzy-delete-today"},
    )

    assert fuzzy.status_code == 200
    proposal = fuzzy.json()["proposal"]
    assert proposal["commitment_type"] == "delete"
    assert proposal["requires_confirmation"] is False
    assert proposal["delete_patch"] is None
    assert proposal["title"] == "选择要取消的项目"
    assert len(proposal["candidates"]) == 2
    assert all("#" in candidate["resolution_text"] for candidate in proposal["candidates"])

    selected = client.post(
        "/agent/propose",
        json={
            "text": proposal["candidates"][0]["resolution_text"],
            "session_id": "fuzzy-delete-selected",
        },
    ).json()["proposal"]
    assert selected["commitment_type"] == "delete"
    assert selected["requires_confirmation"] is True
    assert selected["delete_patch"]["target_id"] == proposal["candidates"][0]["id"]


def test_fuzzy_delete_falls_back_to_nearby_schedule_when_today_empty(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={"text": "明天上午九点有个活动", "session_id": "create-tomorrow-activity"},
    )
    client.post(f"/agent/confirm/{response.json()['proposal']['id']}")

    fuzzy = client.post(
        "/agent/propose",
        json={"text": "今天取消某项活动", "session_id": "fuzzy-delete-empty-today"},
    ).json()["proposal"]

    assert fuzzy["commitment_type"] == "delete"
    assert fuzzy["requires_confirmation"] is False
    assert len(fuzzy["candidates"]) == 1
    assert fuzzy["candidates"][0]["title"] == "有个活动"


def test_fuzzy_update_returns_candidates_and_selected_candidate_updates(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)
    for text, session in (
        ("今天上午九点有个活动", "create-update-morning"),
        ("今天下午三点有个活动", "create-update-afternoon"),
    ):
        response = client.post("/agent/propose", json={"text": text, "session_id": session})
        client.post(f"/agent/confirm/{response.json()['proposal']['id']}")

    fuzzy = client.post(
        "/agent/propose",
        json={"text": "把今天那个活动改到晚上八点", "session_id": "fuzzy-update-today"},
    ).json()["proposal"]

    assert fuzzy["commitment_type"] == "update"
    assert fuzzy["requires_confirmation"] is False
    assert fuzzy["title"] == "选择要修改的项目"
    assert len(fuzzy["candidates"]) == 2

    selected = client.post(
        "/agent/propose",
        json={
            "text": fuzzy["candidates"][0]["resolution_text"],
            "session_id": "fuzzy-update-selected",
        },
    ).json()["proposal"]
    assert selected["commitment_type"] == "update"
    assert selected["requires_confirmation"] is True
    assert selected["update_patch"]["target_id"] == fuzzy["candidates"][0]["id"]
    assert selected["update_patch"]["schedule_patch"]["start"] == "2026-04-30T20:00:00+08:00"


def test_update_schedule_request_changes_existing_commitment_time(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "明天12点午睡",
            "session_id": "create-before-update",
        },
    )
    proposal_id = response.json()["proposal"]["id"]
    client.post(f"/agent/confirm/{proposal_id}")

    update = client.post(
        "/agent/propose",
        json={
            "text": "把午睡改到明天下午2点",
            "session_id": "update-nap",
        },
    )

    assert update.status_code == 200
    proposal = update.json()["proposal"]
    assert proposal["commitment_type"] == "update"
    assert proposal["update_patch"]["target_title"] == "午睡"
    assert proposal["update_patch"]["schedule_patch"]["start"] == "2026-04-30T14:00:00+08:00"

    confirm = client.post(f"/agent/confirm/{proposal['id']}")
    assert confirm.status_code == 200
    events = client.get("/commitments").json()["events"]
    assert events[0]["title"] == "午睡"
    assert events[0]["start"] == "2026-04-30T14:00:00+08:00"


def test_editing_update_proposal_updates_replacement_before_confirmation(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "明天12点午睡",
            "session_id": "create-before-edit-update",
        },
    )
    client.post(f"/agent/confirm/{response.json()['proposal']['id']}")

    update = client.post(
        "/agent/propose",
        json={
            "text": "把午睡改到明天下午2点",
            "session_id": "edit-update-nap",
        },
    ).json()["proposal"]

    edited = client.post(
        f"/agent/proposal/{update['id']}/edit",
        json={
            "schedule_patch": {
                **update["schedule_patch"],
                "start": "2026-04-30T15:30:00+08:00",
                "end": "2026-04-30T16:30:00+08:00",
            },
        },
    )

    assert edited.status_code == 200
    assert edited.json()["commitment_type"] == "update"
    assert edited.json()["update_patch"]["schedule_patch"]["start"] == "2026-04-30T15:30:00+08:00"

    confirm = client.post(f"/agent/confirm/{update['id']}")
    assert confirm.status_code == 200
    events = client.get("/commitments").json()["events"]
    assert events[0]["start"] == "2026-04-30T15:30:00+08:00"


def test_query_and_suggestion_requests_return_non_write_results(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "明天下午三点去电玩城",
            "session_id": "create-before-query",
        },
    )
    client.post(f"/agent/confirm/{response.json()['proposal']['id']}")

    query = client.post(
        "/agent/propose",
        json={"text": "明天有什么安排", "session_id": "query-tomorrow"},
    ).json()["proposal"]
    assert query["commitment_type"] == "query"
    assert query["requires_confirmation"] is False
    assert "去电玩城" in query["summary"]
    assert query["candidates"][0]["title"] == "去电玩城"
    assert query["candidates"][0]["action_label"] == "删除"
    assert query["candidates"][0]["resolution_text"].startswith("删除")

    suggestion = client.post(
        "/agent/propose",
        json={"text": "给我一些安排建议", "session_id": "suggest-plan"},
    ).json()["proposal"]
    assert suggestion["commitment_type"] == "suggestion"
    assert suggestion["requires_confirmation"] is False
    assert suggestion["summary"]


def test_query_activity_returns_visual_schedule_candidates_only(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)
    for text, session in (
        ("今天上午九点开晨会", "query-list-event"),
        ("今天完成接口文档", "query-list-todo"),
        ("明天上午十点开评审会", "query-list-tomorrow"),
    ):
        response = client.post("/agent/propose", json={"text": text, "session_id": session})
        client.post(f"/agent/confirm/{response.json()['proposal']['id']}")

    query = client.post(
        "/agent/propose",
        json={"text": "今天有哪些活动", "session_id": "query-today-activity-list"},
    ).json()["proposal"]

    assert query["commitment_type"] == "query"
    assert query["title"] == "今天"
    assert [candidate["title"] for candidate in query["candidates"]] == ["开晨会"]
    assert query["candidates"][0]["target_type"] == "schedule"
    assert query["candidates"][0]["action_label"] == "删除"


def test_fuzzy_delete_candidates_prioritize_date_then_title(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)
    for text, session in (
        ("今天上午九点开项目复盘会", "delete-rank-today-title"),
        ("今天下午三点开普通会议", "delete-rank-today-other"),
        ("明天上午九点开项目复盘会", "delete-rank-tomorrow-title"),
    ):
        response = client.post("/agent/propose", json={"text": text, "session_id": session})
        client.post(f"/agent/confirm/{response.json()['proposal']['id']}")

    proposal = client.post(
        "/agent/propose",
        json={"text": "今天取消项目相关的活动", "session_id": "delete-rank"},
    ).json()["proposal"]

    assert proposal["commitment_type"] == "delete"
    assert proposal["requires_confirmation"] is False
    assert [candidate["title"] for candidate in proposal["candidates"][:2]] == ["开项目复盘会", "开普通会议"]
    assert all("今天" in candidate["when"] for candidate in proposal["candidates"])


def test_agent_propose_uses_client_commitment_context_without_server_persistence(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-30")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "今天取消某项活动",
            "session_id": "stateless-client-context",
            "commitments": {
                "events": [
                    {
                        "id": "evt_local_1",
                        "title": "本地活动",
                        "start": "2026-04-30T09:00:00+08:00",
                        "end": "2026-04-30T10:00:00+08:00",
                        "status": "confirmed",
                        "location": "",
                        "notes": "",
                        "tags": [],
                    }
                ],
                "todos": [],
            },
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "delete"
    assert proposal["delete_patch"]["target_id"] == "evt_local_1"
    assert not (tmp_path / ".runtime" / "proposals" / f"{proposal['id']}.json").exists()


def test_agent_query_uses_client_commitment_context_without_server_persistence(tmp_path, monkeypatch):
    monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-05-01")
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "今天有哪些活动",
            "session_id": "query-local-context",
            "commitments": {
                "events": [
                    {
                        "id": "evt_1",
                        "title": "地理课",
                        "start": "2026-05-01T09:00:00+08:00",
                        "end": "2026-05-01T10:00:00+08:00",
                        "timezone": "Asia/Shanghai",
                        "status": "confirmed",
                        "location": "",
                        "notes": "",
                        "tags": [],
                    }
                ],
                "todos": [],
            },
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    searchable = " ".join(
        [
            proposal.get("title") or "",
            proposal.get("summary") or "",
            " ".join(candidate.get("title") or "" for candidate in proposal.get("candidates") or []),
        ]
    )
    assert "地理课" in searchable
    assert not (tmp_path / ".runtime" / "commitments").exists()


def test_agent_propose_uses_request_scoped_model_credentials(tmp_path, monkeypatch):
    captured = {}

    def fake_extract(self, text, settings=None):
        captured["settings"] = settings
        return {
            "commitment_type": "schedule",
            "title": "测试会议",
            "date": "2026-05-01",
            "time": "15:00",
            "priority": "medium",
        }

    monkeypatch.setattr("fucktheddl_agent.model_gateway.ModelGateway.extract_commitment", fake_extract)
    client = make_client(tmp_path, monkeypatch)

    response = client.post(
        "/agent/propose",
        json={
            "text": "明天下午三点开会",
            "session_id": "request-model-credentials",
            "model_api_key": "user-key",
            "model_base_url": "https://api.deepseek.com/v1",
            "model": "deepseek-v4-flash",
            "disable_thinking": True,
            "commitments": {"events": [], "todos": []},
        },
    )

    assert response.status_code == 200
    assert captured["settings"].api_key == "user-key"
    assert captured["settings"].enabled is True
    assert captured["settings"].disable_thinking is True


def test_confirming_todo_proposal_writes_todo_month_file(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "2026年5月1日前完成安卓原生骨架",
            "session_id": "confirm-todo",
        },
    )
    proposal_id = response.json()["proposal"]["id"]

    confirm = client.post(f"/agent/confirm/{proposal_id}")

    assert confirm.status_code == 200
    todo_file = tmp_path / "todos" / "2026-05.json"
    assert todo_file.exists()
    assert "安卓原生骨架" in todo_file.read_text(encoding="utf-8")


def test_undo_creates_reverse_business_patch_without_git_revert(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.post(
        "/agent/propose",
        json={
            "text": "2026年5月1日前完成安卓原生骨架",
            "session_id": "undo-todo",
        },
    )
    proposal_id = response.json()["proposal"]["id"]
    applied = client.post(f"/agent/confirm/{proposal_id}").json()

    undo = client.post(f"/agent/undo/{applied['commitment_id']}")

    assert undo.status_code == 200
    assert undo.json()["status"] == "undone"
    todo_text = (tmp_path / "todos" / "2026-05.json").read_text(encoding="utf-8")
    assert '"status": "cancelled"' in todo_text
    git_log = subprocess.check_output(
        ["git", "-C", str(tmp_path), "log", "--pretty=%s", "-2"],
        text=True,
    )
    assert "Undo" in git_log


def test_asr_session_requires_aliyun_configuration(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    monkeypatch.delenv("ALIYUN_API_KEY", raising=False)

    response = client.get("/asr/session")

    assert response.status_code == 503
    assert response.json()["detail"] == "Aliyun ASR credentials are not configured"


def test_asr_session_returns_realtime_fun_asr_config(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    monkeypatch.setenv("ALIYUN_API_KEY", "test-aliyun-api-key")

    response = client.get("/asr/session")

    assert response.status_code == 200
    body = response.json()
    assert body["api_key"] == "test-aliyun-api-key"
    assert body["url"] == "wss://dashscope.aliyuncs.com/api-ws/v1/inference"
    assert body["model"] == "fun-asr-realtime-2025-09-15"
    assert body["sample_rate"] == 16000
    assert body["service_type"] == 4
