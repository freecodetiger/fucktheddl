# Email Auth And Local Room Data Domains Design

Date: 2026-05-01
Status: approved for implementation planning

## Goal

Make email the user's permanent identity for fucktheddl while keeping schedule and todo data local-first on the Android device.

Users sign in with an email verification code. After sign-in, the app stores a long-lived token locally and uses it for protected backend features. Schedule and todo data are stored in Android Room SQLite and isolated by the signed-in email/user. The backend is responsible for authentication, AI forwarding, Redis-backed agent jobs, and ASR credentials. It does not store user schedules or todos in this phase.

## Decisions

- Authentication uses passwordless email verification codes.
- Resend is the production email provider.
- The email address is the permanent user identity.
- One email can be used on multiple devices.
- Users must sign in before entering the main app.
- Access tokens are long-lived and hidden from the UI.
- DeepSeek API keys stay only on the phone and are sent to the backend per AI request.
- Schedule and todo data remain local on Android Room SQLite.
- Existing local schedule/todo data is not migrated after first login.
- `/health`, `/auth/code/request`, and `/auth/code/verify` are public. All other backend routes require authentication.
- Token and DeepSeek key storage remain SharedPreferences for this phase.

## Backend Architecture

The backend should be split by responsibility:

```text
auth/
- Normalize email addresses
- Generate and verify email codes
- Create users
- Issue, hash, authenticate, and revoke access tokens
- Read/write auth SQLite tables

email/
- Resend client wrapper
- Test fake sender

agent/
- AI request parsing and proposal generation
- Redis queue integration
- No persistence of user DeepSeek keys

asr/
- Authenticated ASR session configuration

api/
- FastAPI route composition and schema boundaries
```

The current `registered-users.json` token store should be retired from the production path. Auth state moves to backend SQLite.

## Backend Data Model

Use SQLite for user identity and auth state:

```text
users
- id text primary key
- email text unique not null
- created_at text not null
- updated_at text not null

email_verification_codes
- id text primary key
- email text not null
- code_hash text not null
- expires_at text not null
- attempts integer not null default 0
- locked_until text
- consumed_at text
- created_at text not null

access_tokens
- id text primary key
- user_id text not null references users(id)
- token_hash text unique not null
- created_at text not null
- revoked_at text
- last_used_at text
```

Verification codes and access tokens must never be stored in plaintext. Store only hashes and compare using constant-time comparison.

## Email Verification Flow

`POST /auth/code/request`

Request:

```json
{
  "email": "user@example.com"
}
```

Behavior:

- Normalize email to lowercase and trim whitespace.
- Generate a 6-digit numeric code.
- Store only the code hash.
- Code expires after 5 minutes.
- Reject repeat code requests for the same email within 60 seconds.
- Send the email through Resend.

`POST /auth/code/verify`

Request:

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

Behavior:

- Normalize email.
- Reject expired, consumed, locked, or missing codes.
- Lock after 3 incorrect attempts.
- If the code is correct, mark it consumed.
- Create the user if the email does not exist.
- Reuse the existing user if the email exists.
- Issue a long-lived access token and return it once.

Response:

```json
{
  "user_id": "usr_...",
  "email": "user@example.com",
  "access_token": "...",
  "newly_created": true
}
```

## Resend Configuration

The backend reads Resend configuration from environment variables:

```text
RESEND_API_KEY
RESEND_FROM_EMAIL
RESEND_FROM_NAME=DDL Agent
```

The client never sees Resend credentials. Tests should use a fake email sender instead of calling Resend.

Email branding uses `DDL Agent`, not the previous project codename.

## Authenticated API Boundaries

Public:

```text
GET  /health
POST /auth/code/request
POST /auth/code/verify
```

Protected:

```text
POST /auth/logout
POST /agent/propose
GET  /agent/jobs/{job_id}
POST /agent/confirm/{proposal_id}
POST /agent/proposal/{proposal_id}/edit
GET  /asr/session
```

Requests may authenticate with either:

```text
Authorization: Bearer <token>
X-Agent-Token: <token>
```

Long-term sync routes should also be protected.

## Agent And Commitments Boundary

The backend should not be the source of truth for schedules and todos in this phase.

The client sends local Room commitments in each AI request:

```text
client Room data
-> AgentRequest.commitments
-> backend proposal generation
-> proposal returned to client
-> user confirms or edits
-> client applies proposal into Room
```

The existing backend commitment storage and file-based confirm behavior should be removed from the primary path. During migration, keep only compatibility code where needed to avoid breaking current UI compilation, then move toward client-side proposal application as the single write path.

## Android Startup Flow

```text
App launch
-> read local login state
-> no email/token: show login screen
-> email/token present: load Room data for this user
-> enter main app
```

The main app is not accessible before login.

## Android Login Flow

```text
User enters email
-> request verification code
-> user enters 6-digit code
-> verify code
-> save userId, email, accessToken
-> initialize Room user domain
-> enter main app
```

The UI should not expose the access token. Settings show only the email and login state.

## Android Room Data Domains

Introduce Room as the local source of truth for schedule and todo data.

Every schedule and todo row must be scoped by the signed-in user:

```text
schedules
- id
- owner_user_id
- title
- start
- end
- timezone
- location
- notes
- status
- tags
- created_at
- updated_at

todos
- id
- owner_user_id
- title
- due
- timezone
- priority
- notes
- status
- tags
- created_at
- updated_at
```

All DAO reads and writes must filter by `owner_user_id`, which comes from the verified backend login response. Different emails on the same phone must see fully separate local data.

On first login, existing legacy local data is not migrated. The signed-in user starts with an empty Room data domain.

## Android Main Data Flow

```text
Room local data
-> map to ScheduleShellState
-> Compose UI
```

Create, edit, delete, and proposal confirmation update only Room for the current user.

AI and voice:

```text
hold to speak
-> ASR transcription
-> read current user's Room commitments
-> send commitments + DeepSeek settings + auth token to backend
-> receive proposal
-> user confirms/edits/cancels
-> apply proposal into Room
-> refresh UI
```

## Settings

Settings should contain:

```text
User
- current email
- logged-in state
- logout

Connection
- backend URL
- connection test
- no visible token field

AI
- DeepSeek API key
- DeepSeek base URL
- model name

Theme
- three existing themes
```

Logout clears the local email/token login state only. It does not delete Room data. Logging back into the same email on the same phone restores that local user's data.

## Backend Tests

Required backend coverage:

```text
auth
- request code sends email through fake sender
- repeat request within 60 seconds is rejected
- code expires after 5 minutes
- 3 wrong attempts locks verification
- correct code creates a user
- existing email login reuses the same user
- token and code are stored as hashes
- revoked token is rejected

api
- /health is public
- /auth/code/request is public
- /auth/code/verify is public
- /agent/propose requires auth
- /asr/session requires auth
```

## Android Tests

Required Android coverage:

```text
auth client
- requestCode serializes email
- verifyCode parses userId, email, and accessToken
- protected requests attach token

login gate
- no login state shows login screen
- login success enters main app

Room
- same email can read/write its data
- different emails do not see each other's data
- delete/edit only affect current user
- first login does not import legacy data
```

## End-To-End Smoke Test

Manual smoke test after implementation:

```text
1. Launch app without login state.
2. Confirm login screen appears.
3. Enter email and request code.
4. Read delivered email and enter code.
5. Confirm main app opens.
6. Create a schedule through AI/voice and confirm it.
7. Confirm the schedule appears from Room.
8. Logout.
9. Login with the same email and confirm the schedule is still visible.
10. Login with another email and confirm the schedule list is empty.
11. Call /agent/propose without token and confirm 401.
12. Call /agent/propose with token and confirm success.
```

## Risks

- Room changes are broad because the current app still has legacy commitment store assumptions.
- Moving proposal application to the client changes the old backend confirm mental model.
- Resend requires a verified sender/domain for reliable production delivery.
- Long-lived tokens are convenient but need logout/revoke support for lost devices.
- SharedPreferences plaintext storage is accepted for this phase but should be revisited before public release.

## Non-Goals For This Phase

- Password login.
- Refresh tokens.
- Cloud schedule/todo sync.
- Server-side schedule/todo source of truth.
- Migrating old local schedule/todo data.
- Encrypting local Token or DeepSeek API key storage.
- Multi-device conflict resolution.
