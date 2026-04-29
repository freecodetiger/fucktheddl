# fucktheddl Frontend Design

## Diagnosis

fucktheddl is not a calendar with a chatbot attached. It is an Agent-centered schedule operating surface. The user opens the app, speaks or types, and the Agent turns intent into trusted schedule facts. The calendar is the visual evidence of what the Agent changed.

The front end must therefore make the Agent ever-present without demoting it into a tab. The design should feel calm, precise, and durable: black and white by default, color only for function, risk, status, and tags.

## Recommended Path

Use the disruptive path from the design plan:

- Agent is a global bottom composer, not a peer navigation destination.
- The opening surface is `Today`: a useful timeline plus immediate voice/text input.
- Navigation stays small: `Today` and `Calendar`.
- Low-risk new events may be auto-written with undo.
- Modifications, deletes, conflicts, cross-day events, and ambiguous time require confirmation.
- Agent feedback shows short state and reason, not verbose reasoning.

## Core Surfaces

- `Today`: current time, today's timeline, open slots, DDL risk layer, recent Agent action.
- `Calendar`: day/week/month modes with week as the default planning view.
- `Agent Composer`: global input layer with text field, press-and-hold voice button, and send action.
- `Proposal Card`: concise schedule mutation preview with confirm, edit, undo, or dismiss.
- `Sync Indicator`: small Git-backed state: clean, syncing, offline, conflict, failed.

## Visual System

- Surface: `#FAFAF8`
- Ink: `#141414`
- Muted text: `#6B6B6B`
- Accent: `#2563EB`
- Risk: `#D97706`
- Danger: `#DC2626`
- Success: `#059669`
- Card radius: 4px
- Button radius: 6px
- Sheet radius: 8px
- Touch targets: at least 44px
- Motion: 200ms ease-out for micro-interactions, 300ms ease-in-out for schedule movement.

## Prototype

The implemented high-fidelity mobile prototype is in:

```text
prototype/index.html
```

It is a dependency-free browser prototype for validating the front-end direction before building the native Compose app.
