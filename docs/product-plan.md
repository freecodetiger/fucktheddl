# fucktheddl Product Plan

## Summary

fucktheddl is a personal Android-first schedule management Agent. The product goal is to let the user manage schedules through natural language while preserving a highly visual, elegant calendar interface and smooth animated state changes.

The first version uses a native Android client, a private Agent backend, JSON files as the durable source of truth, and Git for synchronization and history. The system is personal-use only. It does not target commercial use, multi-user collaboration, or public distribution.

## Product Direction

- Primary platform: Android phone.
- Primary interaction: natural-language conversation with a strong Agent.
- Primary interface: chat plus day/week/month calendar views.
- Primary data principle: local-first facts with Git-backed synchronization.
- Primary intelligence principle: strong cloud Agent capability, while keeping durable schedule data in user-owned JSON files.

## Recommended Architecture

The first version should use this architecture:

- Android app: Kotlin + Jetpack Compose.
- Agent backend: private Python service.
- Agent framework: LangGraph as the main orchestration layer.
- Model provider: OpenAI as the default strong model provider.
- Persistence: monthly JSON schedule files.
- Sync: private Git repository.

The Android app should focus on high-quality mobile UX: chat, calendar visualization, animation, local cache, and offline browsing. The backend should own complex reasoning, schedule proposal generation, JSON writes, and Git operations.

## Agent Workflow

The Agent should not directly mutate schedule data during normal conversation. It should propose a change, wait for explicit user confirmation, then apply the confirmed patch.

Standard workflow:

1. Understand the user's natural-language request.
2. Extract candidate schedule changes.
3. Read relevant existing schedule JSON files.
4. Check missing fields, time ambiguity, conflicts, duplicates, and reminders.
5. Ask clarifying questions when required.
6. Generate a clear change proposal.
7. Wait for user confirmation.
8. Apply the confirmed JSON patch.
9. Create a Git commit.
10. Push to the private remote repository when sync is available.

The Agent should expose controlled calendar tools instead of broad filesystem access:

- `list_events(range)`
- `propose_event_patch(input)`
- `validate_patch(patch)`
- `apply_confirmed_patch(patch_id)`
- `git_sync()`

## Data Model

JSON files are the durable source of truth. The first version should store schedules by month:

```text
schedules/
  2026-04.json
  2026-05.json
metadata/
  tags.json
  preferences.json
agent_logs/
  2026-04-29.jsonl
```

Each event should have a stable ID and a compact schema:

```json
{
  "id": "evt_20260429_150000_dentist",
  "title": "Dentist appointment",
  "start": "2026-05-03T15:00:00+08:00",
  "end": "2026-05-03T16:00:00+08:00",
  "timezone": "Asia/Shanghai",
  "status": "confirmed",
  "location": "",
  "notes": "",
  "tags": ["health"],
  "reminders": [
    {
      "offset_minutes": 1440,
      "channel": "local_notification"
    }
  ],
  "source_text": "下周三下午三点约牙医，提前一天提醒我",
  "created_at": "2026-04-29T19:10:00+08:00",
  "updated_at": "2026-04-29T19:10:00+08:00"
}
```

Monthly files are the default because they keep Git diffs readable while avoiding excessive file counts. If conflicts become frequent later, the project can migrate to one event per JSON file.

## Git Sync

Git is the synchronization and history layer, not the runtime database.

Best practices:

- Use a private GitHub, Gitea, or self-hosted Git repository.
- Commit only validated JSON changes.
- Use structured commit messages, such as `schedule: add dentist appointment on 2026-05-03`.
- Include the Agent proposal summary in the commit body.
- Pull before applying a confirmed patch.
- If a merge conflict occurs, stop automatic writes and ask the user to choose a resolution.

The Android client should not operate Git directly in the first version. The private backend should manage Git operations because it is easier to debug, secure, and recover.

## Android Experience

The app should open directly into the useful schedule surface. It should not have a marketing homepage.

Core screens:

- Today view with chat input.
- Week calendar with animated event blocks.
- Todo view for deadline-bound work that does not require attendance at a specific time.
- Month calendar with density and tag visualization.
- Event detail and edit confirmation sheet.
- Agent activity stream showing reasoning state at a high level.

Interaction principles:

- Natural language input should always be reachable.
- Any Agent change should appear first as a confirmation card.
- Timed attendance belongs in the calendar; deadline-bound work belongs in Todo.
- New events should animate from the confirmation card into the calendar.
- Event edits should animate position, duration, or label changes instead of hard refreshing.
- Deletes should support undo.
- The UI should remain usable when the backend or model provider is unavailable.

## Backend Design

The backend should be a private service, likely Python/FastAPI for the first version.

Core responsibilities:

- Conversation session handling.
- LangGraph Agent orchestration.
- OpenAI model calls.
- Schedule JSON reading and writing.
- Deterministic validation.
- Git commit, pull, and push.
- Audit logging.

Important implementation boundary:

- LLM output is never trusted directly.
- All schedule mutations must pass deterministic validation.
- Only confirmed patches can call write tools.
- Failed model calls or network failures must not create partial schedule files.

## Testing Strategy

Agent tests:

- Create a single event from natural language.
- Create a cross-day event.
- Create an event with reminder.
- Ask clarifying questions for ambiguous time.
- Detect conflicts.
- Modify an existing event.
- Cancel an existing event.
- Avoid duplicate creation after retry.

JSON tests:

- Validate event schema.
- Keep monthly files sorted.
- Handle events that cross month boundaries.
- Produce readable Git diffs.
- Reject malformed patches.

Git tests:

- Commit confirmed changes.
- Pull before write.
- Handle no-network mode.
- Detect and stop on merge conflicts.
- Avoid duplicate commits for idempotent retries.

Android tests:

- Day, week, and month rendering.
- Chat-to-confirmation-card flow.
- Confirmation-to-calendar animation.
- Dark and light mode.
- Offline read-only state.
- Small and large Android screen sizes.

End-to-end acceptance scenario:

```text
User: 下周三下午三点约牙医，提前一天提醒我

Expected:
1. Agent resolves the intended date and timezone or asks if ambiguous.
2. Agent checks existing schedules for conflicts.
3. Agent presents a confirmation card.
4. User confirms.
5. Backend writes the event into the correct monthly JSON file.
6. Backend creates a Git commit.
7. Android calendar animates the event into place.
```

## Initial Development Milestones

1. Create repository structure and product documentation.
2. Define JSON schema and sample schedule files.
3. Build backend schedule store with validation and Git operations.
4. Build minimal LangGraph Agent workflow with confirmation-gated writes.
5. Build Android Compose shell with chat and basic calendar rendering.
6. Connect Android to backend APIs.
7. Add animations, conflict handling, and offline cache.
8. Add test coverage for Agent, JSON, Git, and Android UI flows.

## Explicit Non-Goals For Version 1

- No commercial deployment.
- No multi-user collaboration.
- No app store release requirement.
- No system calendar or Google Calendar two-way sync.
- No direct Git operations from Android.
- No fully local LLM requirement.
- No complex permission model.

## Assumptions

- The app name is `fucktheddl`.
- The project root is `/Users/zpc/projects/fucktheddl`.
- The first version is for one user's personal schedule management.
- OpenAI is acceptable as the default strong model provider.
- A private backend is acceptable and preferred.
- JSON files, not SQLite, are the durable schedule source of truth.
- Git is the sync and history mechanism.
