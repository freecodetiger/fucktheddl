# Backend Agent Skeleton

The backend lives under `backend/fucktheddl_agent` and exposes a private FastAPI API.

## Commands

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -e '.[test]'
.venv/bin/python -m pytest backend/tests -q
.venv/bin/uvicorn fucktheddl_agent.api:app --app-dir backend --reload
```

## Environment

The backend expects OpenAI-compatible model gateway variables:

```bash
export OPENAI_API_KEY="..."
export OPENAI_BASE_URL="https://your-codex-gateway.example/v1"
export OPENAI_MODEL="gpt-5.4"
```

Aliyun realtime ASR configuration stays on the backend:

```bash
export ALIYUN_API_KEY="..."
export ALIYUN_APP_KEY="..."
```

Android debug builds read the private backend address from untracked `local.properties`:

```properties
agent.baseUrl=http://<ubuntu-lan-ip>:8000
```

## Current Agent Chain

The first LangGraph chain is confirmation-gated:

1. Classify intent as `schedule`, `todo`, or `clarify`.
2. Read controlled schedule/todo facts.
3. Validate conflicts and category boundaries.
4. Draft a confirmation proposal.
5. Wait for explicit confirmation before durable writes.

## Current Write Boundary

The backend now persists confirmation proposals under `.runtime/proposals/`. Confirming a proposal writes one monthly JSON file and creates a local Git commit. It does not push automatically. Undo is implemented as a reverse business patch that marks the item cancelled and creates another commit; it does not call `git revert`.

## Voice Chain

The Android app uses the Aliyun NUI SDK from `app/libs/nuisdk-release.aar`.

- Partial ASR results update the composer draft in real time.
- Sentence/final results submit the recognized text to `/agent/propose`.
- ASR startup config comes from `/asr/session`.
- Failed ASR keeps the draft text instead of clearing input.
