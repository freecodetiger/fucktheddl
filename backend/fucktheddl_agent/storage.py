from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from fucktheddl_agent.schemas import Proposal


@dataclass(frozen=True)
class ApplyResult:
    commitment_id: str
    commitment_type: str
    file_path: Path
    commit_hash: str


class ScheduleStore:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.runtime_dir = root / ".runtime" / "proposals"

    def save_proposal(self, proposal: Proposal) -> None:
        self.runtime_dir.mkdir(parents=True, exist_ok=True)
        self._write_json(self.runtime_dir / f"{proposal.id}.json", proposal.model_dump(mode="json"))

    def load_proposal(self, proposal_id: str) -> Proposal | None:
        path = self.runtime_dir / f"{proposal_id}.json"
        if not path.exists():
            return None
        return Proposal.model_validate(json.loads(path.read_text(encoding="utf-8")))

    def apply_proposal(self, proposal: Proposal, source_text: str = "") -> ApplyResult:
        if proposal.commitment_type == "delete" and proposal.delete_patch:
            result = self.undo_commitment(proposal.delete_patch.target_id)
            if result is None:
                raise ValueError("Target commitment was not found")
            return result

        if proposal.commitment_type == "update" and proposal.update_patch:
            return self.update_commitment(proposal)

        if proposal.commitment_type == "schedule" and proposal.schedule_patch:
            record = self._schedule_record(proposal, source_text)
            month = record["start"][:7]
            file_path = self.root / "schedules" / f"{month}.json"
            collection = self._load_collection(file_path)
            collection = [item for item in collection if item["id"] != record["id"]]
            collection.append(record)
            collection.sort(key=lambda item: item.get("start", ""))
            self._write_json(file_path, collection)
            commit_hash = self._commit(file_path, f"schedule: add {record['title']}")
            return ApplyResult(record["id"], "schedule", file_path, commit_hash)

        if proposal.commitment_type == "todo" and proposal.todo_patch:
            record = self._todo_record(proposal, source_text)
            month = record["due"][:7]
            file_path = self.root / "todos" / f"{month}.json"
            collection = self._load_collection(file_path)
            collection = [item for item in collection if item["id"] != record["id"]]
            collection.append(record)
            collection.sort(key=lambda item: (item.get("due", ""), item.get("title", "")))
            self._write_json(file_path, collection)
            commit_hash = self._commit(file_path, f"todo: add {record['title']}")
            return ApplyResult(record["id"], "todo", file_path, commit_hash)

        raise ValueError("Proposal is not applicable")

    def update_commitment(self, proposal: Proposal) -> ApplyResult:
        patch = proposal.update_patch
        assert patch is not None
        for directory, commitment_type in (("schedules", "schedule"), ("todos", "todo")):
            for file_path in (self.root / directory).glob("*.json"):
                collection = self._load_collection(file_path)
                for index, item in enumerate(collection):
                    if item.get("id") != patch.target_id:
                        continue
                    if commitment_type == "schedule" and patch.schedule_patch:
                        replacement = self._schedule_record(
                            Proposal(
                                id=proposal.id,
                                commitment_type="schedule",
                                title=patch.schedule_patch.title,
                                summary=proposal.summary,
                                impact=proposal.impact,
                                requires_confirmation=True,
                                schedule_patch=patch.schedule_patch,
                                todo_patch=None,
                            ),
                            source_text=item.get("source_text", ""),
                        )
                    elif commitment_type == "todo" and patch.todo_patch:
                        replacement = self._todo_record(
                            Proposal(
                                id=proposal.id,
                                commitment_type="todo",
                                title=patch.todo_patch.title,
                                summary=proposal.summary,
                                impact=proposal.impact,
                                requires_confirmation=True,
                                schedule_patch=None,
                                todo_patch=patch.todo_patch,
                            ),
                            source_text=item.get("source_text", ""),
                        )
                    else:
                        raise ValueError("Update patch type does not match target")
                    replacement["id"] = item["id"]
                    replacement["created_at"] = item.get("created_at", replacement["created_at"])
                    collection[index] = replacement
                    collection.sort(key=lambda current: current.get("start", current.get("due", "")))
                    self._write_json(file_path, collection)
                    commit_hash = self._commit(file_path, f"update: {patch.target_title}")
                    return ApplyResult(item["id"], commitment_type, file_path, commit_hash)
        raise ValueError("Target commitment was not found")

    def undo_commitment(self, commitment_id: str) -> ApplyResult | None:
        for directory, commitment_type in (("schedules", "schedule"), ("todos", "todo")):
            for file_path in (self.root / directory).glob("*.json"):
                collection = self._load_collection(file_path)
                changed = False
                for item in collection:
                    if item.get("id") == commitment_id:
                        item["status"] = "cancelled"
                        changed = True
                if changed:
                    self._write_json(file_path, collection)
                    commit_hash = self._commit(file_path, f"Undo {commitment_id}")
                    return ApplyResult(commitment_id, commitment_type, file_path, commit_hash)
        return None

    def list_commitments(self) -> dict[str, list[dict[str, Any]]]:
        events = []
        for file_path in (self.root / "schedules").glob("*.json"):
            events.extend(
                item
                for item in self._load_collection(file_path)
                if item.get("status") == "confirmed"
            )
        events.sort(key=lambda item: item.get("start", ""))

        todos = []
        for file_path in (self.root / "todos").glob("*.json"):
            todos.extend(
                item
                for item in self._load_collection(file_path)
                if item.get("status") in {"active", "done"}
            )
        todos.sort(key=lambda item: (item.get("due", ""), item.get("title", "")))
        return {"events": events, "todos": todos}

    def _schedule_record(self, proposal: Proposal, source_text: str) -> dict[str, Any]:
        patch = proposal.schedule_patch
        assert patch is not None
        now = "2026-04-29T00:00:00+08:00"
        slug = _slug(patch.title)
        return {
            "id": f"evt_{patch.start[:10].replace('-', '')}_{patch.start[11:19].replace(':', '')}_{slug}",
            "title": patch.title,
            "start": patch.start,
            "end": patch.end,
            "timezone": patch.timezone,
            "status": "confirmed",
            "location": patch.location,
            "notes": patch.notes,
            "tags": patch.tags,
            "reminders": [reminder.model_dump() for reminder in patch.reminders],
            "source_text": source_text or proposal.title,
            "created_at": now,
            "updated_at": now,
        }

    def _todo_record(self, proposal: Proposal, source_text: str) -> dict[str, Any]:
        patch = proposal.todo_patch
        assert patch is not None
        now = "2026-04-29T00:00:00+08:00"
        slug = _slug(patch.title)
        return {
            "id": f"todo_{patch.due.replace('-', '')}_{slug}",
            "title": patch.title,
            "due": patch.due,
            "timezone": patch.timezone,
            "status": "active",
            "priority": patch.priority,
            "notes": patch.notes,
            "tags": patch.tags,
            "source_text": source_text or proposal.title,
            "created_at": now,
            "updated_at": now,
        }

    def _load_collection(self, file_path: Path) -> list[dict[str, Any]]:
        if not file_path.exists():
            return []
        return json.loads(file_path.read_text(encoding="utf-8"))

    def _write_json(self, file_path: Path, data: Any) -> None:
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text(
            json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def _commit(self, file_path: Path, message: str) -> str:
        self._ensure_git_repo()
        subprocess.run(["git", "-C", str(self.root), "add", str(file_path)], check=True)
        subprocess.run(["git", "-C", str(self.root), "commit", "-m", message], check=True)
        return subprocess.check_output(
            ["git", "-C", str(self.root), "rev-parse", "--short", "HEAD"],
            text=True,
        ).strip()

    def _ensure_git_repo(self) -> None:
        if not (self.root / ".git").exists():
            subprocess.run(["git", "-C", str(self.root), "init"], check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "-C", str(self.root), "config", "user.email", "agent@example.local"], check=True)
            subprocess.run(["git", "-C", str(self.root), "config", "user.name", "fucktheddl agent"], check=True)


def _slug(text: str) -> str:
    cleaned = "".join(ch.lower() if ch.isalnum() else "_" for ch in text.strip())
    compact = "_".join(part for part in cleaned.split("_") if part)
    return compact[:32] or "item"
