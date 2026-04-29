import os
import subprocess

from fastapi.testclient import TestClient

from fucktheddl_agent.api import create_app


def make_client(tmp_path, monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    monkeypatch.setenv("OPENAI_BASE_URL", "https://codex.example/v1")
    monkeypatch.setenv("OPENAI_MODEL", "gpt-5.4")
    return TestClient(create_app(data_root=tmp_path))


def test_health_reports_agent_backend_ready(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["agent_framework"] == "langgraph"
    assert body["write_policy"] == "proposal_required"
    assert body["model"]["base_url"] == "https://codex.example/v1"
    assert body["model"]["model"] == "gpt-5.4"


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
    monkeypatch.delenv("ALIYUN_APP_KEY", raising=False)

    response = client.get("/asr/session")

    assert response.status_code == 503
    assert response.json()["detail"] == "Aliyun ASR credentials are not configured"


def test_asr_session_returns_realtime_fun_asr_config(tmp_path, monkeypatch):
    client = make_client(tmp_path, monkeypatch)
    monkeypatch.setenv("ALIYUN_API_KEY", "test-aliyun-token")
    monkeypatch.setenv("ALIYUN_APP_KEY", "test-app-key")

    response = client.get("/asr/session")

    assert response.status_code == 200
    body = response.json()
    assert body["app_key"] == "test-app-key"
    assert body["token"] == "test-aliyun-token"
    assert body["model"] == "fun-asr-realtime-2025-09-15"
    assert body["sample_rate"] == 16000
    assert body["service_type"] == 4
