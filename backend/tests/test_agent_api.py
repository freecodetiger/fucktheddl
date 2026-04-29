from fastapi.testclient import TestClient

from fucktheddl_agent.api import app


client = TestClient(app)


def test_health_reports_agent_backend_ready():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "agent_framework": "langgraph",
        "write_policy": "proposal_required",
    }


def test_schedule_request_returns_confirmation_gated_schedule_proposal():
    response = client.post(
        "/agent/propose",
        json={
            "text": "明天下午三点开项目复盘会，提前十分钟提醒我",
            "session_id": "test-schedule",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["proposal"]["commitment_type"] == "schedule"
    assert body["proposal"]["requires_confirmation"] is True
    assert body["proposal"]["schedule_patch"]["time_range"] == "15:00"
    assert body["write_policy"] == "proposal_required"
    assert [step["label"] for step in body["chain"]] == [
        "Classify intent",
        "Read schedule and todo facts",
        "Validate conflicts",
        "Draft confirmation proposal",
        "Wait for confirmation",
    ]


def test_deadline_request_returns_todo_proposal_not_calendar_event():
    response = client.post(
        "/agent/propose",
        json={
            "text": "周五前完成安卓原生骨架",
            "session_id": "test-todo",
        },
    )

    assert response.status_code == 200
    proposal = response.json()["proposal"]
    assert proposal["commitment_type"] == "todo"
    assert proposal["schedule_patch"] is None
    assert proposal["todo_patch"]["due_label"] == "Due this Friday"


def test_confirming_unknown_proposal_does_not_write_anything():
    response = client.post("/agent/confirm/missing-proposal")

    assert response.status_code == 404
    assert response.json()["detail"] == "Proposal not found"

