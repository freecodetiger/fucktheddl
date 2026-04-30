import os
import subprocess

from fastapi.testclient import TestClient

from fucktheddl_agent.api import create_app
from fucktheddl_agent.workflow import build_agent_graph, classify_intent


def make_client(tmp_path, monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    monkeypatch.setenv("OPENAI_BASE_URL", "https://codex.example/v1")
    monkeypatch.setenv("OPENAI_MODEL", "deepseek-v4-flash")
    monkeypatch.setenv("OPENAI_DISABLE_THINKING", "true")
    if "FUCKTHEDDL_TODAY" not in os.environ:
        monkeypatch.setenv("FUCKTHEDDL_TODAY", "2026-04-29")
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
    assert body["model"]["model"] == "deepseek-v4-flash"
    assert body["model"]["disable_thinking"] is True


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

    suggestion = client.post(
        "/agent/propose",
        json={"text": "给我一些安排建议", "session_id": "suggest-plan"},
    ).json()["proposal"]
    assert suggestion["commitment_type"] == "suggestion"
    assert suggestion["requires_confirmation"] is False
    assert suggestion["summary"]


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
