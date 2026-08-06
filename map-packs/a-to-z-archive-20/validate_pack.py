from __future__ import annotations

import json
import unicodedata
from pathlib import Path

from generate_map import build_pack


ROOT = Path(__file__).resolve().parent
PACK_PATH = ROOT / "map.jsonc"
EXPECTED_LETTERS = "ABCDEGJKLMNOPQRSTWYZ"
EXPECTED_PRIMARY_ANSWERS = {
    "A": "speller",
    "B": "1000000000100",
    "C": "주경야독",
    "D": "사랑과전쟁",
    "E": "점자",
    "G": "제우스",
    "J": "onepair",
    "K": "heart",
    "L": "sound",
    "M": "monkey",
    "N": "스코틀랜드",
    "O": "city",
    "P": "118",
    "Q": "qwerty",
    "R": "civil",
    "S": "해왕성",
    "T": "축소",
    "W": "mist",
    "Y": "rotavator",
    "Z": "뱀주인자리",
}
FORBIDDEN_INPUT_TOKENS = ("PRESSURE_PLATE", "BUTTON", "LEVER")


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


def main() -> None:
    pack = json.loads(PACK_PATH.read_text(encoding="utf-8"))
    assert pack == build_pack(), "map.jsonc is stale; run generate_map.py"
    assert pack["mazeId"] == "a-to-z-archive-20"
    assert pack["mapVersion"] == "2.1.0-a20"
    assert pack["party"] == {"minPlayers": 1, "maxPlayers": 4}
    assert len(pack["rooms"]) == 20

    serialized = PACK_PATH.read_text(encoding="utf-8")
    for token in FORBIDDEN_INPUT_TOKENS:
        assert token not in serialized, f"forbidden physical input token: {token}"

    world_bounds = pack["world"]["bounds"]
    rooms = pack["rooms"]
    fingerprints: set[str] = set()
    previous_difficulty = 0
    for index, room in enumerate(rooms):
        letter = EXPECTED_LETTERS[index]
        assert room["sequence"] == index + 1
        assert room["title"].startswith(f"{letter} · ")
        assert room["originalStage"] == ord(letter) - ord("A") + 1
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
        assert fingerprint not in fingerprints, f"duplicate floor diagram: {room['id']}"
        fingerprints.add(fingerprint)

        assert len(room["mechanics"]) == 1
        terminal = room["mechanics"][0]
        assert terminal["type"] == "LOGIC_ANSWER"
        assert terminal["answers"][0] == EXPECTED_PRIMARY_ANSWERS[letter]
        assert 2 <= len(terminal["pages"]) <= 8
        assert all(0 < len(page) <= 240 for page in terminal["pages"])
        assert len(room["hints"]) == 3
        assert [hint["tier"] for hint in room["hints"]] == [1, 2, 3]
        assert terminal["difficulty"] >= previous_difficulty
        previous_difficulty = terminal["difficulty"]
        assert 10 <= terminal["cooldownSeconds"] <= 18
        assert len(terminal["wrongAnswerSamples"]) >= 3

        accepted = {normalize(answer) for answer in terminal["answers"]}
        rejected = {normalize(answer) for answer in terminal["wrongAnswerSamples"]}
        assert accepted.isdisjoint(rejected)
        if terminal["difficulty"] >= 4:
            mandatory = normalize(terminal["question"] + " " + " ".join(terminal["pages"]))
            first_two_hints = normalize(room["hints"][0]["text"] + " " + room["hints"][1]["text"])
            for answer in accepted:
                assert answer not in mandatory, f"mandatory evidence leaks answer in {room['id']}"
                assert answer not in first_two_hints, f"early hint leaks answer in {room['id']}"

    for index, left in enumerate(rooms):
        for right in rooms[index + 1:]:
            assert not overlaps(left["buildBounds"], right["buildBounds"]), (
                f"room bounds overlap: {left['id']} / {right['id']}"
            )

    print("validated 20 rooms, 1-4 players, deterministic generation, no forbidden input blocks")
    print("validated late-room answer secrecy, room bounds, floor diagrams, and authored answer sets")


if __name__ == "__main__":
    main()
