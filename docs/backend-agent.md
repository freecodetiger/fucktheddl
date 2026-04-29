# Backend Agent Skeleton

The backend lives under `backend/fucktheddl_agent` and exposes a private FastAPI API.

## Commands

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -e '.[test]'
.venv/bin/python -m pytest backend/tests -q
.venv/bin/uvicorn fucktheddl_agent.api:app --app-dir backend --reload
```

## Current Agent Chain

The first LangGraph chain is deterministic and confirmation-gated:

1. Classify intent as `schedule`, `todo`, or `clarify`.
2. Read controlled schedule/todo facts.
3. Validate conflicts and category boundaries.
4. Draft a confirmation proposal.
5. Wait for explicit confirmation before durable writes.

No endpoint currently writes schedule JSON or creates Git commits. That is intentional until patch validation and proposal storage are implemented.

