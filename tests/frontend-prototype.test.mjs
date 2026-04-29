import assert from "node:assert/strict";
import { readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;
const prototypePath = join(root, "prototype", "index.html");

function readPrototype() {
  const stats = statSync(prototypePath);
  assert.ok(stats.size > 12_000, "prototype should be a substantive high-fidelity mobile UI");
  return readFileSync(prototypePath, "utf8");
}

const html = readPrototype();

const requiredCopy = [
  "Today",
  "Calendar",
  "Hold to talk",
  "Agent is writing",
  "Auto-written",
  "Undo",
  "Sync clean",
  "Risk layer",
  "No Agent tab",
];

for (const text of requiredCopy) {
  assert.ok(html.includes(text), `prototype should include '${text}'`);
}

const requiredSelectors = [
  "agent-composer",
  "today-timeline",
  "proposal-card",
  "event-block",
  "sync-indicator",
  "risk-meter",
  "week-strip",
  "voice-button",
];

for (const selector of requiredSelectors) {
  assert.ok(html.includes(selector), `prototype should include ${selector}`);
}

const requiredTokens = [
  "--surface: #FAFAF8",
  "--ink: #141414",
  "--muted: #6B6B6B",
  "--accent: #2563EB",
  "--risk: #D97706",
  "--danger: #DC2626",
  "--success: #059669",
  "min-height: 44px",
  "300ms ease-in-out",
];

for (const token of requiredTokens) {
  assert.ok(html.includes(token), `prototype should include design token '${token}'`);
}

assert.ok(!html.includes(">Agent<"), "Agent must not be implemented as a top-level tab label");
assert.ok(html.includes("data-mode=\"today\""), "Today should be the primary opening surface");
