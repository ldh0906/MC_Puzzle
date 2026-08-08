from __future__ import annotations

import json
import unicodedata
from pathlib import Path

from generate_map import MAZES, build_packs, selected_puzzles


ROOT = Path(__file__).resolve().parent
EXPECTED_COUNTS = {"easy": 12, "normal": 12, "hard": 5}
EXPECTED_DIFFICULTIES = {"easy": {1, 2}, "normal": {3, 4}, "hard": {5}}


def normalize(value: str) -> str:
    return "".join(
        character.lower()
        for character in unicodedata.normalize("NFKC", value)
        if character.isalpha() or character.isdigit()
    )


def inside(point: dict, bounds: dict) -> bool:
    return all(bounds["min"][axis] <= point[axis] <= bounds["max"][axis] for axis in ("x", "y", "z"))


def overlaps(left: dict, right: dict) -> bool:
    return all(
        left["min"][axis] <= right["max"][axis]
        and right["min"][axis] <= left["max"][axis]
        for axis in ("x", "y", "z")
    )


def page_text(page: str | dict) -> str:
    return page if isinstance(page, str) else page["text"]


def sign_visual_width(line: str) -> int:
    return sum(2 if ord(character) > 127 else 1 for character in line)


def main() -> None:
    expected_packs = build_packs()
    seen_ids: set[str] = set()
    seen_titles: set[str] = set()
    seen_answers: set[str] = set()
    total_rooms = 0

    for level, metadata in MAZES.items():
        pack_path = ROOT / f"{level}.jsonc"
        serialized = pack_path.read_text(encoding="utf-8")
        pack = json.loads(serialized)
        assert pack == expected_packs[level], f"{level}.jsonc is stale; run generate_map.py"
        assert pack["mazeId"] == metadata["mazeId"]
        assert pack["mapVersion"] == metadata["mapVersion"]
        assert pack["party"] == {"minPlayers": 1, "maxPlayers": 4}
        assert len(pack["rooms"]) == EXPECTED_COUNTS[level]

        world_bounds = pack["world"]["bounds"]
        rooms = pack["rooms"]
        fingerprints: set[str] = set()
        expected = selected_puzzles(level)
        for index, room in enumerate(rooms):
            source_index, definition = expected[index]
            assert room["sequence"] == index + 1
            assert room["originalStage"] == source_index + 1
            assert room["title"] == definition["title"]
            assert " · " not in room["title"], "visible room titles must not use alphabet labels"
            assert room["title"] not in seen_titles, f"duplicate unique room name: {room['title']}"
            seen_titles.add(room["title"])
            assert room["id"] not in seen_ids, f"room appears in multiple mazes: {room['id']}"
            seen_ids.add(room["id"])
            assert inside(room["spawn"], room["playBounds"])
            assert inside(room["checkpoint"], room["playBounds"])
            assert inside(room["buildBounds"]["min"], world_bounds)
            assert inside(room["buildBounds"]["max"], world_bounds)

            visual = room["visual"]
            assert len(visual["cells"]) == visual["width"] * visual["height"] == 225
            assert inside(visual["origin"], room["buildBounds"])
            visual_max = {
                "x": visual["origin"]["x"] + visual["width"] * visual["scale"] - 1,
                "y": visual["origin"]["y"],
                "z": visual["origin"]["z"] + visual["height"] * visual["scale"] - 1,
            }
            assert inside(visual_max, room["buildBounds"])
            fingerprint = json.dumps(visual["cells"], separators=(",", ":"))
            assert fingerprint not in fingerprints, f"duplicate floor diagram in {level}: {room['id']}"
            fingerprints.add(fingerprint)

            assert len(room["mechanics"]) == (1 if level == "hard" else 2)
            terminal = room["mechanics"][0]
            assert terminal["type"] == "LOGIC_ANSWER"
            assert terminal["answers"] == definition["answers"]
            assert terminal["difficulty"] in EXPECTED_DIFFICULTIES[level]
            assert 1 <= len(terminal["pages"]) <= 8
            assert all(0 < len(page_text(page)) <= 240 for page in terminal["pages"])
            assert len(room["hints"]) == 3
            assert [hint["tier"] for hint in room["hints"]] == [1, 2, 3]
            assert 10 <= terminal["cooldownSeconds"] <= 18
            assert len(terminal["wrongAnswerSamples"]) >= 3

            accepted = {normalize(answer) for answer in terminal["answers"]}
            rejected = {normalize(answer) for answer in terminal["wrongAnswerSamples"]}
            assert accepted.isdisjoint(rejected)
            for answer in accepted:
                assert f"{room['id']}:{answer}" not in seen_answers
                seen_answers.add(f"{room['id']}:{answer}")
            if level == "hard":
                assert terminal.get("submissionMode", "CHAT") == "CHAT"
                assert "structure" not in room
            else:
                assert "structure" in room
                hybrid = (level == "easy" and room["sequence"] == 2) or (
                    level == "normal" and room["sequence"] in (2, 6)
                )
                assert terminal["submissionMode"] == ("CHAT" if hybrid else "DEVICE_ONLY")
                if hybrid:
                    assert terminal["requires"] == [room["mechanics"][1]["id"]]
                else:
                    assert terminal["requires"] == []
                structure = room["structure"]
                assert set(structure) == {"blocks", "cuboids", "signs"}
                for item in structure["blocks"]:
                    assert inside(item["position"], room["buildBounds"])
                for item in structure["signs"]:
                    assert inside(item["position"], room["buildBounds"])
                    assert pack["world"]["floorY"] + 1 <= item["position"]["y"] <= pack["world"]["floorY"] + 3
                    assert item["facing"] in {"NORTH", "SOUTH", "EAST", "WEST"}
                    assert 1 <= len(item["lines"]) <= 4
                    assert all(sign_visual_width(line) <= 18 for line in item["lines"])
                for item in structure["cuboids"]:
                    assert inside(item["bounds"]["min"], room["buildBounds"])
                    assert inside(item["bounds"]["max"], room["buildBounds"])

                device = room["mechanics"][1]
                if "controls" in device:
                    control_ids = {item["id"] for item in device["controls"]}
                    assert len(control_ids) == len(device["controls"])
                    for item in device["controls"]:
                        assert inside(item["position"], room["buildBounds"])
                        if "indicator" in item:
                            assert inside(item["indicator"], room["buildBounds"])
                if device["type"] == "ORDERED_INPUT":
                    assert all(step["control"] in control_ids for step in device["expected"])
                    visible = sum(bool(step.get("display", step["control"])) for step in device["expected"])
                    assert not device["groups"] or sum(device["groups"]) == visible
                elif device["type"] == "CHOICE_INPUT":
                    assert device["correctControl"] in control_ids
                elif device["type"] == "TOGGLE_INPUT":
                    assert set(device["expectedActive"]) <= control_ids
                elif device["type"] == "CLUE_REGIONS":
                    for region in device["regions"]:
                        assert inside(region["bounds"]["min"], room["buildBounds"])
                        assert inside(region["bounds"]["max"], room["buildBounds"])

        for index, left in enumerate(rooms):
            for right in rooms[index + 1:]:
                assert not overlaps(left["buildBounds"], right["buildBounds"]), (
                    f"room bounds overlap: {left['id']} / {right['id']}"
                )
        total_rooms += len(rooms)

    assert total_rooms == 29
    assert len(seen_ids) == len(seen_titles) == 29
    assert "여섯 점의 눈" not in seen_titles
    print("validated easy=12, normal=12, hard=5 (29 unique rooms total)")
    print("validated unique names, physical mechanics, submission gates, bounds, diagrams, and 1-4 players")


if __name__ == "__main__":
    main()
