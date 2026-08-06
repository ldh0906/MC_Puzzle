from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
import struct
import sys
import wave
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from io import BytesIO
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / ".analysis_tools"
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

from eudplib.bindings._rust import mpqapi  # noqa: E402
from eudplib.core.rawtrigger.strdict.sprite import DefSpriteDict  # noqa: E402
from eudplib.core.rawtrigger.strdict.tech import DefTechDict  # noqa: E402
from eudplib.core.rawtrigger.strdict.trg import DefUnitDict  # noqa: E402
from eudplib.core.rawtrigger.strdict.upgrade import DefUpgradeDict  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402


def invert_first(values: dict[str, int]) -> dict[int, str]:
    result: dict[int, str] = {}
    for name, value in values.items():
        result.setdefault(value, name)
    return result


UNIT_NAMES = invert_first(DefUnitDict)
SPRITE_NAMES = invert_first(DefSpriteDict)
TECH_NAMES = invert_first(DefTechDict)
UPGRADE_NAMES = invert_first(DefUpgradeDict)

PLAYER_NAMES = {i: f"Player {i + 1}" for i in range(12)}
PLAYER_NAMES.update(
    {
        13: "Current Player",
        14: "Foes",
        15: "Allies",
        16: "Neutral Players",
        17: "All Players",
        18: "Force 1",
        19: "Force 2",
        20: "Force 3",
        21: "Force 4",
        26: "Non-Allied Victory Players",
    }
)

OWNER_NAMES = {
    0: "Inactive",
    1: "Computer (game)",
    2: "Occupied by Human Player",
    3: "Rescue Passive",
    4: "Unused",
    5: "Computer",
    6: "Human (Open Slot)",
    7: "Neutral",
    8: "Closed Slot",
}
RACE_NAMES = {
    0: "Zerg",
    1: "Terran",
    2: "Protoss",
    3: "Independent",
    4: "Neutral",
    5: "User Select",
    6: "Random",
    7: "Inactive",
}

CONDITION_NAMES = {
    0: "No Condition",
    1: "Countdown Timer",
    2: "Command",
    3: "Bring",
    4: "Accumulate",
    5: "Kills",
    6: "Command the Most",
    7: "Commands the Most At",
    8: "Most Kills",
    9: "Highest Score",
    10: "Most Resources",
    11: "Switch",
    12: "Elapsed Time",
    13: "Mission Briefing Marker",
    14: "Opponents",
    15: "Deaths",
    16: "Command the Least",
    17: "Command the Least At",
    18: "Least Kills",
    19: "Lowest Score",
    20: "Least Resources",
    21: "Score",
    22: "Always",
    23: "Never",
}

ACTION_NAMES = {
    0: "No Action",
    1: "Victory",
    2: "Defeat",
    3: "Preserve Trigger",
    4: "Wait",
    5: "Pause Game",
    6: "Unpause Game",
    7: "Transmission",
    8: "Play WAV",
    9: "Display Text Message",
    10: "Center View",
    11: "Create Unit with Properties",
    12: "Set Mission Objectives",
    13: "Set Switch",
    14: "Set Countdown Timer",
    15: "Run AI Script",
    16: "Run AI Script At Location",
    17: "Leader Board Control",
    18: "Leader Board Control At Location",
    19: "Leader Board Resources",
    20: "Leader Board Kills",
    21: "Leader Board Points",
    22: "Kill Unit",
    23: "Kill Unit At Location",
    24: "Remove Unit",
    25: "Remove Unit At Location",
    26: "Set Resources",
    27: "Set Score",
    28: "Minimap Ping",
    29: "Talking Portrait",
    30: "Mute Unit Speech",
    31: "Unmute Unit Speech",
    32: "Leader Board Computer Players",
    33: "Leader Board Goal Control",
    34: "Leader Board Goal Control At Location",
    35: "Leader Board Goal Resources",
    36: "Leader Board Goal Kills",
    37: "Leader Board Goal Points",
    38: "Move Location",
    39: "Move Unit",
    40: "Leader Board Greed",
    41: "Set Next Scenario",
    42: "Set Doodad State",
    43: "Set Invincibility",
    44: "Create Unit",
    45: "Set Deaths",
    46: "Order",
    47: "Comment",
    48: "Give Units to Player",
    49: "Modify Unit Hit Points",
    50: "Modify Unit Energy",
    51: "Modify Unit Shield Points",
    52: "Modify Unit Resource Amount",
    53: "Modify Unit Hangar Count",
    54: "Pause Timer",
    55: "Unpause Timer",
    56: "Draw",
    57: "Set Alliance Status",
    58: "Disable Debug Mode",
    59: "Enable Debug Mode",
}

COMPARISONS = {0: "At least", 1: "At most", 10: "Exactly"}
MODIFIERS = {7: "Set to", 8: "Add", 9: "Subtract"}
RESOURCE_NAMES = {0: "Minerals", 1: "Gas", 2: "Minerals and Gas"}
SCORE_NAMES = {
    0: "Total",
    1: "Units",
    2: "Buildings",
    3: "Units and Buildings",
    4: "Kills",
    5: "Razings",
    6: "Kills and Razings",
    7: "Custom",
}
SWITCH_CONDITION_STATES = {2: "Set", 3: "Cleared"}
SWITCH_ACTION_STATES = {4: "Set", 5: "Clear", 6: "Toggle", 11: "Randomize"}
PROPERTY_STATES = {4: "Enable", 5: "Disable", 6: "Toggle"}
ORDERS = {0: "Move", 1: "Patrol", 2: "Attack"}
ALLIANCE_STATES = {0: "Enemy", 1: "Ally", 2: "Allied Victory"}

KNOWN_SECTIONS = {
    b"VER ",
    b"VCOD",
    b"OWNR",
    b"ERA ",
    b"DIM ",
    b"SIDE",
    b"MTXM",
    b"PUNI",
    b"UPGR",
    b"PUPx",
    b"PTEC",
    b"PTEx",
    b"UNIT",
    b"ISOM",
    b"TILE",
    b"DD2 ",
    b"THG2",
    b"MASK",
    b"STR ",
    b"STRx",
    b"UPRP",
    b"UPUS",
    b"MRGN",
    b"TRIG",
    b"MBRF",
    b"SPRP",
    b"FORC",
    b"WAV ",
    b"UNIS",
    b"UNIx",
    b"UPGS",
    b"UPGx",
    b"TECS",
    b"TECx",
    b"SWNM",
    b"COLR",
    b"CRGB",
}


@dataclass
class Section:
    index: int
    offset: int
    name_hex: str
    name_ascii: str
    size: int
    sha256: str
    known: bool
    data: bytes


@dataclass
class Condition:
    location_id: int
    group: int
    quantity: int
    unit_id: int
    comparison: int
    condition_id: int
    argument_type: int
    flags: int
    mask_flag: int


@dataclass
class Action:
    location_id: int
    text_string_id: int
    wav_string_id: int
    time: int
    first_group: int
    second_group: int
    argument_type: int
    action_id: int
    modifier: int
    flags: int
    padding: int
    mask_flag: int


@dataclass
class Trigger:
    index: int
    conditions: list[Condition]
    actions: list[Action]
    execution_flags: int
    owners: list[int]
    current_action: int


class ProtectedStringTable:
    def __init__(self, data: bytes):
        self.data = data
        self.declared_count = struct.unpack_from("<H", data, 0)[0] if len(data) >= 2 else 0

    def raw(self, string_id: int) -> bytes:
        if string_id <= 0 or 2 * string_id + 2 > len(self.data):
            return b""
        offset = struct.unpack_from("<H", self.data, 2 * string_id)[0]
        if offset >= len(self.data):
            return b""
        end = self.data.find(b"\0", offset)
        if end < 0:
            end = len(self.data)
        return self.data[offset:end]

    def text(self, string_id: int) -> str:
        return self.raw(string_id).decode("cp949", errors="replace")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def section_label(name: bytes) -> str:
    try:
        value = name.decode("ascii")
        if all(32 <= ord(ch) <= 126 for ch in value):
            return value
    except UnicodeDecodeError:
        pass
    return "0x" + name.hex()


def parse_sections(chk: bytes) -> list[Section]:
    result: list[Section] = []
    offset = 0
    index = 1
    while offset + 8 <= len(chk):
        name = chk[offset : offset + 4]
        size = struct.unpack_from("<I", chk, offset + 4)[0]
        data = chk[offset + 8 : offset + 8 + size]
        if len(data) != size:
            raise ValueError(f"Section {index} is truncated")
        result.append(
            Section(
                index=index,
                offset=offset,
                name_hex=name.hex(),
                name_ascii=section_label(name),
                size=size,
                sha256=sha256(data),
                known=name in KNOWN_SECTIONS,
                data=data,
            )
        )
        offset += 8 + size
        index += 1
    if offset != len(chk):
        raise ValueError(f"CHK has {len(chk) - offset} trailing bytes")
    return result


def first_section(sections: list[Section], name: str) -> bytes:
    for section in sections:
        if section.name_ascii == name:
            return section.data
    raise KeyError(name)


def all_sections(sections: list[Section], name: str) -> list[bytes]:
    return [section.data for section in sections if section.name_ascii == name]


def clean_sc_text(text: str, collapse: bool = False) -> str:
    text = text.replace("\x12", "").replace("\x13", "")
    text = "".join(ch for ch in text if ch in "\r\n\t" or ord(ch) >= 32)
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = [line.rstrip() for line in text.split("\n")]
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    result = "\n".join(lines)
    if collapse:
        result = re.sub(r"\s+", " ", result).strip()
    return result


def md_cell(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", "<br>")


def loc_name(location_id: int) -> str:
    if location_id == 0:
        return "No Location"
    if location_id == 64:
        return "Anywhere (L64)"
    return f"Location {location_id}"


def player_name(player_id: int) -> str:
    return PLAYER_NAMES.get(player_id, f"Group {player_id}")


def unit_name(unit_id: int) -> str:
    return UNIT_NAMES.get(unit_id, f"Unit {unit_id}")


def parse_conditions(blob: bytes, offset: int = 0) -> list[Condition]:
    result: list[Condition] = []
    for index in range(16):
        values = struct.unpack_from("<IIIHBBBBH", blob, offset + index * 20)
        if values[5] != 0:
            result.append(Condition(*values))
    return result


def parse_actions(blob: bytes, offset: int = 320) -> list[Action]:
    result: list[Action] = []
    for index in range(64):
        values = struct.unpack_from("<IIIIIIHBBBBH", blob, offset + index * 32)
        if values[7] != 0:
            result.append(Action(*values))
    return result


def parse_triggers(data: bytes) -> list[Trigger]:
    if len(data) % 2400:
        raise ValueError("TRIG length is not a multiple of 2400")
    result: list[Trigger] = []
    for index in range(len(data) // 2400):
        blob = data[index * 2400 : (index + 1) * 2400]
        flags = struct.unpack_from("<I", blob, 2368)[0]
        owners = [i for i, enabled in enumerate(blob[2372:2399]) if enabled]
        result.append(
            Trigger(
                index=index + 1,
                conditions=parse_conditions(blob),
                actions=parse_actions(blob),
                execution_flags=flags,
                owners=owners,
                current_action=blob[2399],
            )
        )
    return result


def render_condition(condition: Condition) -> str:
    c = condition
    comparison = COMPARISONS.get(c.comparison, f"Comparison {c.comparison}")
    player = player_name(c.group)
    unit = unit_name(c.unit_id)
    location = loc_name(c.location_id)
    if c.condition_id == 1:
        return f"Countdown Timer is {comparison} {c.quantity} seconds"
    if c.condition_id == 2:
        return f"{player} commands {comparison} {c.quantity} × {unit}"
    if c.condition_id == 3:
        return f"{player} brings {comparison} {c.quantity} × {unit} to {location}"
    if c.condition_id == 4:
        resource = RESOURCE_NAMES.get(c.argument_type, f"Resource {c.argument_type}")
        return f"{player} accumulates {comparison} {c.quantity} {resource}"
    if c.condition_id == 5:
        return f"{player} has {comparison} {c.quantity} kills of {unit}"
    if c.condition_id in (6, 8, 16, 18):
        return f"{CONDITION_NAMES[c.condition_id]}: {unit}"
    if c.condition_id in (7, 17):
        return f"{CONDITION_NAMES[c.condition_id]}: {unit} at {location}"
    if c.condition_id in (9, 19):
        return f"{CONDITION_NAMES[c.condition_id]}: {SCORE_NAMES.get(c.argument_type, c.argument_type)}"
    if c.condition_id in (10, 20):
        return f"{CONDITION_NAMES[c.condition_id]}: {RESOURCE_NAMES.get(c.argument_type, c.argument_type)}"
    if c.condition_id == 11:
        state = SWITCH_CONDITION_STATES.get(c.comparison, f"State {c.comparison}")
        return f"Switch {c.argument_type + 1} is {state}"
    if c.condition_id == 12:
        return f"Elapsed Time is {comparison} {c.quantity} seconds"
    if c.condition_id == 14:
        return f"{player} has {comparison} {c.quantity} opponents"
    if c.condition_id == 15:
        base = f"Deaths({player}, {comparison} {c.quantity}, {unit})"
        if c.mask_flag == 0x4353:
            base += f" with mask 0x{c.location_id:08X}"
        return base
    if c.condition_id == 21:
        score = SCORE_NAMES.get(c.argument_type, f"Score {c.argument_type}")
        return f"{player} has {comparison} {c.quantity} {score} score"
    return CONDITION_NAMES.get(c.condition_id, f"Condition {c.condition_id}")


def ai_script(value: int) -> str:
    raw = struct.pack("<I", value)
    try:
        text = raw.decode("ascii")
        if all(32 <= ord(ch) <= 126 for ch in text):
            return repr(text)
    except UnicodeDecodeError:
        pass
    return f"0x{value:08X}"


def render_action(action: Action, strings: ProtectedStringTable) -> str:
    a = action
    name = ACTION_NAMES.get(a.action_id, f"Action {a.action_id}")
    player = player_name(a.first_group)
    unit = unit_name(a.argument_type)
    location = loc_name(a.location_id)
    amount = "All" if a.modifier == 0 else str(a.modifier)
    modifier = MODIFIERS.get(a.modifier, f"Modifier {a.modifier}")
    text = clean_sc_text(strings.text(a.text_string_id), collapse=True)
    wav = clean_sc_text(strings.text(a.wav_string_id), collapse=True)
    if a.action_id in (1, 2, 3, 5, 6, 30, 31, 54, 55, 56, 58, 59):
        return name
    if a.action_id == 4:
        return f"Wait {a.time} ms"
    if a.action_id == 7:
        return f"Transmission from {unit} at {location}: {text!r}; WAV={wav!r}; time={a.time} ms"
    if a.action_id == 8:
        return f"Play WAV {wav!r}"
    if a.action_id in (9, 12, 47):
        return f"{name}: {text!r}"
    if a.action_id in (10, 28):
        return f"{name} at {location}"
    if a.action_id == 11:
        return f"Create {a.modifier} × {unit} for {player} at {location} with CUWP slot {a.second_group}"
    if a.action_id == 13:
        state = SWITCH_ACTION_STATES.get(a.modifier, f"State {a.modifier}")
        return f"Set Switch {a.second_group + 1}: {state}"
    if a.action_id == 14:
        return f"Set Countdown Timer: {modifier} {a.time} seconds"
    if a.action_id == 15:
        return f"Run AI script {ai_script(a.second_group)}"
    if a.action_id == 16:
        return f"Run AI script {ai_script(a.second_group)} at {location}"
    if a.action_id in (17, 20, 33, 36):
        return f"{name}: {unit}; label={text!r}; goal={a.second_group}"
    if a.action_id in (18, 34):
        return f"{name}: {unit} at {location}; label={text!r}; goal={a.second_group}"
    if a.action_id in (19, 35):
        resource = RESOURCE_NAMES.get(a.argument_type, a.argument_type)
        return f"{name}: {resource}; label={text!r}; goal={a.second_group}"
    if a.action_id in (21, 37):
        score = SCORE_NAMES.get(a.argument_type, a.argument_type)
        return f"{name}: {score}; label={text!r}; goal={a.second_group}"
    if a.action_id == 22:
        return f"Kill all {unit} owned by {player}"
    if a.action_id == 23:
        return f"Kill {amount} × {unit} owned by {player} at {location}"
    if a.action_id == 24:
        return f"Remove all {unit} owned by {player}"
    if a.action_id == 25:
        return f"Remove {amount} × {unit} owned by {player} at {location}"
    if a.action_id == 26:
        resource = RESOURCE_NAMES.get(a.argument_type, a.argument_type)
        return f"Set Resources({player}, {modifier} {a.second_group} {resource})"
    if a.action_id == 27:
        score = SCORE_NAMES.get(a.argument_type, a.argument_type)
        return f"Set Score({player}, {modifier} {a.second_group} {score})"
    if a.action_id == 29:
        return f"Talking Portrait: {unit} for {a.time} ms"
    if a.action_id == 32:
        return f"Leader Board Computer Players: {PROPERTY_STATES.get(a.modifier, a.modifier)}"
    if a.action_id == 38:
        return f"Move {location} to {unit} owned by {player} at {loc_name(a.second_group)}"
    if a.action_id == 39:
        return f"Move {amount} × {unit} owned by {player}: {location} → {loc_name(a.second_group)}"
    if a.action_id == 40:
        return f"Leader Board Greed: {a.second_group}; label={text!r}"
    if a.action_id == 41:
        return f"Set Next Scenario: {text!r}"
    if a.action_id in (42, 43):
        state = PROPERTY_STATES.get(a.modifier, a.modifier)
        return f"{name}: {state} for {unit} owned by {player} at {location}"
    if a.action_id == 44:
        return f"Create {a.modifier} × {unit} for {player} at {location}"
    if a.action_id == 45:
        base = f"Set Deaths({player}, {modifier} {a.second_group}, {unit})"
        if a.mask_flag == 0x4353:
            base += f" with mask 0x{a.location_id:08X}"
        return base
    if a.action_id == 46:
        order = ORDERS.get(a.modifier, f"Order {a.modifier}")
        return f"Order {unit} owned by {player} at {location}: {order} to {loc_name(a.second_group)}"
    if a.action_id == 48:
        return f"Give {amount} × {unit} at {location}: {player} → {player_name(a.second_group)}"
    if a.action_id in (49, 50, 51):
        return f"{name}: {amount} × {unit} owned by {player} at {location} → {a.second_group}%"
    if a.action_id in (52, 53):
        return f"{name}: {amount} × {unit} owned by {player} at {location} → {a.second_group}"
    if a.action_id == 57:
        return f"Set Alliance: {player} treats Player {a.argument_type + 1} as {ALLIANCE_STATES.get(a.modifier, a.modifier)}"
    return (
        f"{name}(location={a.location_id}, text={a.text_string_id}, wav={a.wav_string_id}, "
        f"time={a.time}, group1={a.first_group}, group2={a.second_group}, "
        f"argument={a.argument_type}, modifier={a.modifier})"
    )


def parse_units(data: bytes) -> list[dict[str, Any]]:
    if len(data) % 36:
        raise ValueError("UNIT length is not a multiple of 36")
    result = []
    fmt = "<I6H4BI2H2I"
    for index in range(len(data) // 36):
        values = struct.unpack_from(fmt, data, index * 36)
        (
            serial,
            x,
            y,
            unit_id,
            relation_class,
            valid_state_flags,
            valid_property_flags,
            owner,
            hp,
            shields,
            energy,
            resources,
            hangar,
            state_flags,
            unused,
            related_serial,
        ) = values
        result.append(
            {
                "index": index + 1,
                "serial": serial,
                "x": x,
                "y": y,
                "unit_id": unit_id,
                "unit_name": unit_name(unit_id),
                "owner_id": owner,
                "owner_name": player_name(owner),
                "hitpoints_percent": hp,
                "shields_percent": shields,
                "energy_percent": energy,
                "resources": resources,
                "hangar_count": hangar,
                "state_flags": state_flags,
                "valid_state_flags": valid_state_flags,
                "valid_property_flags": valid_property_flags,
                "relation_class": relation_class,
                "related_serial": related_serial,
                "unused": unused,
            }
        )
    return result


def parse_locations(data: bytes, strings: ProtectedStringTable) -> list[dict[str, Any]]:
    if len(data) % 20:
        raise ValueError("MRGN length is not a multiple of 20")
    result = []
    for index in range(len(data) // 20):
        left, top, right, bottom, string_id, flags = struct.unpack_from("<4I2H", data, index * 20)
        raw_name = clean_sc_text(strings.text(string_id), collapse=True)
        valid_name_reference = 0 < string_id and 2 * string_id + 2 <= len(strings.data)
        result.append(
            {
                "id": index + 1,
                "left": left,
                "top": top,
                "right": right,
                "bottom": bottom,
                "width": abs(right - left),
                "height": abs(bottom - top),
                "string_id": string_id,
                "decoded_name": raw_name,
                "name_reference_in_section": valid_name_reference,
                "elevation_flags": flags,
                "empty_rectangle": left == top == right == bottom == 0,
            }
        )
    return result


def parse_sprites(data: bytes) -> list[dict[str, Any]]:
    result = []
    for index in range(len(data) // 10):
        sprite_id, x, y, owner, unused, flags = struct.unpack_from("<HHHBBH", data, index * 10)
        result.append(
            {
                "index": index + 1,
                "sprite_id": sprite_id,
                "sprite_name": SPRITE_NAMES.get(sprite_id, f"Sprite {sprite_id}"),
                "x": x,
                "y": y,
                "owner_id": owner,
                "owner_name": player_name(owner),
                "unused": unused,
                "flags": flags,
            }
        )
    return result


def parse_unit_properties(data: bytes) -> list[dict[str, Any]]:
    result = []
    for index in range(len(data) // 20):
        special_valid, property_valid, owner, hp, shield, energy, resources, hangar, flags, padding = struct.unpack_from(
            "<HHBBBBIHHI", data, index * 20
        )
        result.append(
            {
                "slot": index + 1,
                "valid_special_properties_flags": special_valid,
                "valid_unit_properties_flags": property_valid,
                "owner": owner,
                "hitpoints_percent": hp,
                "shield_percent": shield,
                "energy_percent": energy,
                "resources": resources,
                "hangar_count": hangar,
                "flags": flags,
                "padding": padding,
            }
        )
    return result


def parse_custom_units(data: bytes, strings: ProtectedStringTable) -> list[dict[str, Any]]:
    count = 228
    if len(data) != 4168:
        raise ValueError(f"Unexpected UNIx length {len(data)}")
    uses_defaults = list(data[:count])
    offset = count
    hitpoints = struct.unpack_from(f"<{count}I", data, offset)
    offset += 4 * count
    shields = struct.unpack_from(f"<{count}H", data, offset)
    offset += 2 * count
    armor = data[offset : offset + count]
    offset += count
    build_time = struct.unpack_from(f"<{count}H", data, offset)
    offset += 2 * count
    minerals = struct.unpack_from(f"<{count}H", data, offset)
    offset += 2 * count
    gas = struct.unpack_from(f"<{count}H", data, offset)
    offset += 2 * count
    string_ids = struct.unpack_from(f"<{count}H", data, offset)
    offset += 2 * count
    base_weapon_damage = struct.unpack_from("<130H", data, offset)
    offset += 260
    weapon_upgrade_damage = struct.unpack_from("<130H", data, offset)
    result = []
    for unit_id in range(count):
        if uses_defaults[unit_id] != 0:
            continue
        result.append(
            {
                "unit_id": unit_id,
                "default_name": unit_name(unit_id),
                "custom_name_string_id": string_ids[unit_id],
                "custom_name": clean_sc_text(strings.text(string_ids[unit_id]), collapse=True),
                "hitpoints_raw_256ths": hitpoints[unit_id],
                "hitpoints": hitpoints[unit_id] / 256,
                "shields": shields[unit_id],
                "armor": armor[unit_id],
                "build_time_ticks": build_time[unit_id],
                "mineral_cost": minerals[unit_id],
                "gas_cost": gas[unit_id],
            }
        )
    result.append(
        {
            "_weapon_arrays": {
                "base_damage": list(base_weapon_damage),
                "upgrade_bonus": list(weapon_upgrade_damage),
            }
        }
    )
    return result


def parse_upgrades(data: bytes) -> dict[str, Any]:
    count = 61
    offset = 62
    arrays: list[tuple[int, ...]] = []
    for _ in range(6):
        arrays.append(struct.unpack_from(f"<{count}H", data, offset))
        offset += 2 * count
    rows = []
    for upgrade_id in range(count):
        rows.append(
            {
                "upgrade_id": upgrade_id,
                "upgrade_name": UPGRADE_NAMES.get(upgrade_id, f"Upgrade {upgrade_id}"),
                "uses_default": bool(data[upgrade_id]),
                "base_mineral_cost": arrays[0][upgrade_id],
                "mineral_cost_factor": arrays[1][upgrade_id],
                "base_gas_cost": arrays[2][upgrade_id],
                "gas_cost_factor": arrays[3][upgrade_id],
                "base_research_time": arrays[4][upgrade_id],
                "research_time_factor": arrays[5][upgrade_id],
            }
        )
    return {"rows": rows, "trailing_default_byte": data[61]}


def parse_tech(data: bytes) -> list[dict[str, Any]]:
    count = 44
    offset = count
    arrays: list[tuple[int, ...]] = []
    for _ in range(4):
        arrays.append(struct.unpack_from(f"<{count}H", data, offset))
        offset += 2 * count
    return [
        {
            "tech_id": tech_id,
            "tech_name": TECH_NAMES.get(tech_id, f"Tech {tech_id}"),
            "uses_default": bool(data[tech_id]),
            "mineral_cost": arrays[0][tech_id],
            "gas_cost": arrays[1][tech_id],
            "research_time": arrays[2][tech_id],
            "energy_cost": arrays[3][tech_id],
        }
        for tech_id in range(count)
    ]


def parse_restrictions(sections: list[Section]) -> dict[str, Any]:
    puni = first_section(sections, "PUNI")
    ptex = first_section(sections, "PTEx")
    pupx = first_section(sections, "PUPx")
    return {
        "PUNI": {
            "player_unit_availability": list(puni[: 12 * 228]),
            "global_unit_availability": list(puni[12 * 228 : 13 * 228]),
            "player_uses_defaults": list(puni[13 * 228 :]),
        },
        "PTEx": {
            "player_tech_availability": list(ptex[: 12 * 44]),
            "player_tech_researched": list(ptex[12 * 44 : 24 * 44]),
            "global_tech_availability": list(ptex[24 * 44 : 25 * 44]),
            "global_tech_researched": list(ptex[25 * 44 : 26 * 44]),
            "player_uses_defaults": list(ptex[26 * 44 :]),
        },
        "PUPx": {
            "player_max_upgrade_levels": list(pupx[: 12 * 61]),
            "player_start_upgrade_levels": list(pupx[12 * 61 : 24 * 61]),
            "global_max_upgrade_levels": list(pupx[24 * 61 : 25 * 61]),
            "global_start_upgrade_levels": list(pupx[25 * 61 : 26 * 61]),
            "player_uses_defaults": list(pupx[26 * 61 :]),
        },
    }


def stage_room(stage: int) -> dict[str, int]:
    row = (stage - 1) // 10
    within = (stage - 1) % 10
    col = within if row % 2 == 0 else 9 - within
    left = 64 + 576 * col
    top = 64 + 576 * row
    return {
        "row": row + 1,
        "column": col + 1,
        "left": left,
        "top": top,
        "right": left + 480,
        "bottom": top + 480,
    }


def point_in_room(x: int, y: int, room: dict[str, int]) -> bool:
    return room["left"] <= x < room["right"] and room["top"] <= y < room["bottom"]


def stage_from_point(x: float, y: float) -> int | None:
    for stage in range(1, 51):
        if point_in_room(int(x), int(y), stage_room(stage)):
            return stage
    return None


def stage_summaries(
    triggers: list[Trigger],
    strings: ProtectedStringTable,
    units: list[dict[str, Any]],
    custom_unit_rows: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    numeric_by_death_unit: dict[int, int] = {}
    for trigger in triggers:
        exact_minerals = [
            c.quantity
            for c in trigger.conditions
            if c.condition_id == 4 and c.group == 13 and c.comparison == 10 and c.argument_type == 0
        ]
        death_units = [
            a.argument_type
            for a in trigger.actions
            if a.action_id == 45 and a.first_group == 6 and a.modifier == 8 and a.second_group == 1
        ]
        if exact_minerals and death_units:
            for death_unit in death_units:
                numeric_by_death_unit[death_unit] = exact_minerals[0]

    custom_names = {
        row["unit_id"]: row["custom_name"]
        for row in custom_unit_rows
        if "unit_id" in row and row.get("custom_name")
    }
    result: list[dict[str, Any]] = []
    for trigger in triggers:
        stage = None
        completion_text = ""
        for action in trigger.actions:
            text = clean_sc_text(strings.text(action.text_string_id), collapse=True)
            match = re.search(r"스테이지\s*(\d+)\s*클리어", text)
            if match:
                stage = int(match.group(1))
                completion_text = text
                break
        if stage is None:
            continue
        room = stage_room(stage)
        numeric_answer = None
        for condition in trigger.conditions:
            if condition.condition_id == 15 and condition.group == 6:
                if condition.unit_id in numeric_by_death_unit:
                    numeric_answer = numeric_by_death_unit[condition.unit_id]
        room_units = [unit for unit in units if point_in_room(unit["x"], unit["y"], room)]
        room_unit_counts = Counter(unit["unit_name"] for unit in room_units)
        clue_names = []
        for unit in room_units:
            custom = custom_names.get(unit["unit_id"], "")
            if custom and custom.strip() and custom not in clue_names:
                clue_names.append(custom)
        explanation = re.sub(r"^.*?클리어!\s*", "", completion_text).strip()
        result.append(
            {
                "stage": stage,
                "completion_trigger": trigger.index,
                "room": room,
                "numeric_answer": numeric_answer,
                "requirements": [render_condition(c) for c in trigger.conditions],
                "completion_explanation": explanation,
                "room_custom_names": clue_names,
                "room_unit_counts": dict(room_unit_counts),
            }
        )
    return sorted(result, key=lambda row: row["stage"])


def collect_referenced_strings(
    sections: list[Section], triggers: list[Trigger], strings: ProtectedStringTable, custom_units: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    refs: dict[int, set[str]] = defaultdict(set)
    sprp = first_section(sections, "SPRP")
    for label, string_id in zip(("map_title", "map_description"), struct.unpack("<HH", sprp)):
        refs[string_id].add(label)
    forc = first_section(sections, "FORC")
    for force_index, string_id in enumerate(struct.unpack_from("<4H", forc, 8), 1):
        refs[string_id].add(f"force_{force_index}_name")
    for row in custom_units:
        if "unit_id" in row:
            refs[row["custom_name_string_id"]].add(f"custom_unit_{row['unit_id']}_name")
    for trigger in triggers:
        for action in trigger.actions:
            if action.text_string_id:
                refs[action.text_string_id].add(f"trigger_{trigger.index}_text")
            if action.wav_string_id:
                refs[action.wav_string_id].add(f"trigger_{trigger.index}_wav")
    mbrf = first_section(sections, "MBRF")
    for action in parse_actions(mbrf):
        if action.text_string_id:
            refs[action.text_string_id].add("mission_briefing_text")
        if action.wav_string_id:
            refs[action.wav_string_id].add("mission_briefing_wav")
    result = []
    for string_id in sorted(refs):
        raw = strings.raw(string_id)
        result.append(
            {
                "string_id": string_id,
                "sources": sorted(refs[string_id]),
                "decoded_cp949": strings.text(string_id),
                "clean_text": clean_sc_text(strings.text(string_id)),
                "raw_hex": raw.hex(),
                "sha256": sha256(raw),
            }
        )
    return result


def write_csv(path: Path, rows: Iterable[dict[str, Any]], fieldnames: list[str]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def render_room_layout(path: Path) -> None:
    width, height = 1320, 710
    image = Image.new("RGB", (width, height), (18, 21, 28))
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default()
    margin_x, margin_y = 50, 55
    cell_w, cell_h = 118, 105
    draw.text((margin_x, 15), "50-room progression (snake order)", fill=(240, 242, 247), font=font)
    # Draw connections first so the room cards cover the part of each line that
    # would otherwise cross their labels.
    for row in range(5):
        stages = list(range(row * 10 + 1, row * 10 + 11))
        for a, b in zip(stages, stages[1:]):
            ra, rb = stage_room(a), stage_room(b)
            ax = margin_x + (ra["column"] - 1) * cell_w + 46
            ay = margin_y + (ra["row"] - 1) * cell_h + 38
            bx = margin_x + (rb["column"] - 1) * cell_w + 46
            by = margin_y + (rb["row"] - 1) * cell_h + 38
            if bx > ax:
                ax += 46
                bx -= 46
            else:
                ax -= 46
                bx += 46
            draw.line((ax, ay, bx, by), fill=(190, 198, 212), width=3)
    for stage in (10, 20, 30, 40):
        ra, rb = stage_room(stage), stage_room(stage + 1)
        ax = margin_x + (ra["column"] - 1) * cell_w + 46
        ay = margin_y + (ra["row"] - 1) * cell_h + 76
        bx = margin_x + (rb["column"] - 1) * cell_w + 46
        by = margin_y + (rb["row"] - 1) * cell_h
        draw.line((ax, ay, bx, by), fill=(190, 198, 212), width=3)
    for stage in range(1, 51):
        room = stage_room(stage)
        col, row = room["column"] - 1, room["row"] - 1
        x0 = margin_x + col * cell_w
        y0 = margin_y + row * cell_h
        x1, y1 = x0 + 92, y0 + 76
        hue = (stage * 0.07) % 1.0
        color = tuple(int(90 + 130 * channel) for channel in hsv_to_rgb(hue, 0.55, 0.9))
        draw.rounded_rectangle((x0, y0, x1, y1), radius=9, fill=color, outline=(235, 238, 245), width=2)
        label = str(stage)
        bbox = draw.textbbox((0, 0), label, font=font)
        draw.text(((x0 + x1 - (bbox[2] - bbox[0])) / 2, (y0 + y1 - (bbox[3] - bbox[1])) / 2), label, fill=(8, 10, 14), font=font)
    draw.text((margin_x, 600), "Rows 1-5 occupy the upper 90 map tiles; the lower area is used for control/calculator logic.", fill=(190, 198, 212), font=font)
    image.save(path)


def hsv_to_rgb(h: float, s: float, v: float) -> tuple[float, float, float]:
    i = int(h * 6)
    f = h * 6 - i
    p = v * (1 - s)
    q = v * (1 - f * s)
    t = v * (1 - (1 - f) * s)
    return ((v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q))[i % 6]


def render_terrain(path: Path, tiles: list[int], map_width: int, map_height: int, units: list[dict[str, Any]]) -> None:
    scale = 4
    image = Image.new("RGB", (map_width, map_height))
    pixels = image.load()
    for y in range(map_height):
        for x in range(map_width):
            tile = tiles[y * map_width + x]
            if tile == 0:
                color = (14, 16, 20)
            else:
                group = tile >> 4
                variant = tile & 0xF
                h = ((group * 0.61803398875) % 1.0)
                r, g, b = hsv_to_rgb(h, 0.52, 0.48 + 0.025 * variant)
                color = (int(r * 255), int(g * 255), int(b * 255))
            pixels[x, y] = color
    image = image.resize((map_width * scale, map_height * scale), Image.Resampling.NEAREST)
    draw = ImageDraw.Draw(image)
    owner_colors = {
        0: (255, 55, 55),
        1: (80, 110, 255),
        2: (60, 220, 220),
        3: (185, 80, 230),
        4: (255, 185, 80),
        5: (110, 220, 255),
        6: (230, 230, 230),
        7: (250, 235, 80),
        11: (90, 230, 110),
    }
    for unit in units:
        x = int(unit["x"] / 32 * scale)
        y = int(unit["y"] / 32 * scale)
        color = owner_colors.get(unit["owner_id"], (255, 255, 255))
        radius = 2 if unit["unit_id"] != 214 else 4
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=color, outline=(0, 0, 0))
    for stage in range(1, 51):
        room = stage_room(stage)
        x0, y0 = room["left"] // 32 * scale, room["top"] // 32 * scale
        x1, y1 = room["right"] // 32 * scale, room["bottom"] // 32 * scale
        draw.rectangle((x0, y0, x1, y1), outline=(245, 245, 245), width=1)
        draw.text((x0 + 2, y0 + 2), str(stage), fill=(255, 255, 255), font=ImageFont.load_default())
    image.save(path)


def audio_metadata(data: bytes) -> dict[str, Any]:
    with wave.open(BytesIO(data), "rb") as wav:
        return {
            "channels": wav.getnchannels(),
            "sample_width_bytes": wav.getsampwidth(),
            "sample_rate_hz": wav.getframerate(),
            "frames": wav.getnframes(),
            "duration_seconds": wav.getnframes() / wav.getframerate(),
            "compression": wav.getcomptype(),
        }


def write_trigger_report(path: Path, triggers: list[Trigger], strings: ProtectedStringTable) -> None:
    lines = [
        "# 전체 트리거 덤프",
        "",
        "280개 `TRIG` 레코드를 실행 순서대로 해석한 결과입니다. 로케이션 이름은 보호 때문에 손상되어 번호로 표기합니다.",
        "",
    ]
    for trigger in triggers:
        owner_text = ", ".join(player_name(owner) for owner in trigger.owners) or "None"
        lines.extend(
            [
                f"## Trigger {trigger.index}",
                "",
                f"- Owners: {owner_text}",
                f"- Execution flags: `0x{trigger.execution_flags:08X}`",
                f"- Current action index: {trigger.current_action}",
                "",
                "Conditions:",
                "",
            ]
        )
        if trigger.conditions:
            lines.extend(f"- {render_condition(condition)}" for condition in trigger.conditions)
        else:
            lines.append("- (none)")
        lines.extend(["", "Actions:", ""])
        if trigger.actions:
            lines.extend(f"- {render_action(action, strings)}" for action in trigger.actions)
        else:
            lines.append("- (none)")
        lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def build_report(
    source: Path,
    chk: bytes,
    sections: list[Section],
    strings: ProtectedStringTable,
    triggers: list[Trigger],
    units: list[dict[str, Any]],
    sprites: list[dict[str, Any]],
    locations: list[dict[str, Any]],
    custom_units: list[dict[str, Any]],
    stages: list[dict[str, Any]],
    audio: list[dict[str, Any]],
    map_width: int,
    map_height: int,
    terrain_sections: list[bytes],
) -> str:
    sprp = struct.unpack("<HH", first_section(sections, "SPRP"))
    title = clean_sc_text(strings.text(sprp[0]), collapse=True)
    description = clean_sc_text(strings.text(sprp[1]))
    owners = list(first_section(sections, "OWNR"))
    races = list(first_section(sections, "SIDE"))
    colors = list(first_section(sections, "COLR"))
    forc = first_section(sections, "FORC")
    force_membership = list(forc[:8])
    force_name_ids = struct.unpack_from("<4H", forc, 8)
    force_flags = list(forc[16:20])
    condition_counts = Counter(c.condition_id for t in triggers for c in t.conditions)
    action_counts = Counter(a.action_id for t in triggers for a in t.actions)
    trigger_owner_counts = Counter(tuple(t.owners) for t in triggers)
    unit_owner_counts = Counter(unit["owner_id"] for unit in units)
    unit_type_counts = Counter((unit["unit_id"], unit["unit_name"]) for unit in units)
    random_sections = [s for s in sections if not s.known]
    valid_terrain_sections = [d for d in terrain_sections if len(d) == map_width * map_height * 2]
    mask_counts = Counter(first_section(sections, "MASK"))
    custom_rows = [row for row in custom_units if "unit_id" in row]
    numeric_stages = [row for row in stages if row["numeric_answer"] is not None]
    location_nonempty = sum(not row["empty_rectangle"] for row in locations)

    lines = [
        "# 미궁[50개의 방] 1.0 전체 구조 분석",
        "",
        "> 주의: 아래 스테이지 표에는 정답과 클리어 판정이 포함되어 있습니다.",
        "",
        "## 핵심 결론",
        "",
        f"- 맵 제목: `{title}`",
        f"- 제작자 표기: `CoOlLuCk-_-`",
        f"- 크기/타일셋: {map_width}×{map_height} 타일, Installation(ERA 하위 3비트 = 4)",
        f"- 진행 구조: 상단의 10열×5행, 총 50개 방을 뱀형 동선으로 순차 진행",
        f"- 콘텐츠 규모: 배치 유닛 {len(units)}개, 스프라이트 {len(sprites)}개, 좌표가 있는 로케이션 {location_nonempty}개, 트리거 {len(triggers)}개",
        f"- 게임 로직: 스테이지 완료 트리거 50개를 모두 식별했고, 숫자 입력형 정답 {len(numeric_stages)}개를 내부 판정값에서 복원",
        f"- 승리 조건: Switch 51(0-based 원시 번호 50)과 내부 진행 카운터 조건을 만족하면 Trigger 57에서 승리",
        f"- 보안/호환성: EUD 메모리 마스크(`SC`)를 쓰는 조건·액션은 없으며, 일반 Death counter는 내부 상태 변수로만 사용",
        "",
        "![50개 방 진행 순서](room_layout.png)",
        "",
        "![지형·배치 개요](terrain_overview.png)",
        "",
        "## 맵 설명",
        "",
        description or "(설명 없음)",
        "",
        "## 보호 및 난독화",
        "",
        f"- MPQ에는 `(listfile)`이 없고, 확인된 6개 파일 블록이 모두 암호화되어 있습니다.",
        f"- `scenario.chk`에는 총 {len(sections)}개 섹션이 있으며, 그중 {len(random_sections)}개는 무작위 4바이트 이름의 무시용 섹션입니다.",
        f"- 문자열 섹션은 실제 크기 {len(strings.data):,}바이트인데 선언 개수는 {strings.declared_count:,}개입니다. 참조된 ID만 직접 따라가야 문자열이 정상 복원됩니다.",
        f"- `MTXM`은 {len(terrain_sections)}회 등장하며 크기는 {', '.join(f'{len(d):,}' for d in terrain_sections)}바이트입니다. DIM과 정확히 맞는 {len(valid_terrain_sections)}개 섹션만 유효하고 나머지는 검증 실패를 노린 보호용 중복입니다.",
        "- 255개 로케이션의 이름 ID는 난수로 오염됐지만 좌표와 트리거의 로케이션 번호는 정상입니다. 따라서 보고서에서는 `Location N`으로 표시합니다.",
        "- 음수 점프 섹션이나 잘린 섹션은 없고, 핵심 트리거/유닛 데이터는 완전히 순회됩니다.",
        "",
        "## 플레이어와 세력",
        "",
        "| 슬롯 | 컨트롤러 | 종족 | Force | 색상 ID | 배치 유닛 |",
        "|---:|---|---|---:|---:|---:|",
    ]
    for player in range(12):
        force = force_membership[player] + 1 if player < 8 else "-"
        color = colors[player] if player < len(colors) else "-"
        lines.append(
            f"| P{player + 1} | {OWNER_NAMES.get(owners[player], owners[player])} | {RACE_NAMES.get(races[player], races[player])} | {force} | {color} | {unit_owner_counts[player]} |"
        )
    lines.extend(["", "세력 설정:", ""])
    for index in range(4):
        flag = force_flags[index]
        properties = []
        if flag & 1:
            properties.append("Random start")
        if flag & 2:
            properties.append("Allies")
        if flag & 4:
            properties.append("Allied victory")
        if flag & 8:
            properties.append("Shared vision")
        lines.append(
            f"- Force {index + 1}: {clean_sc_text(strings.text(force_name_ids[index]), collapse=True)!r}; flags `0x{flag:02X}` ({', '.join(properties) or 'none'})"
        )
    lines.extend(
        [
            "",
            "## 스테이지별 실제 클리어 판정",
            "",
            "숫자형은 맵이 비교하는 광물값을 정답으로 표시했습니다. 그 외는 완료 트리거의 모든 조건을 그대로 요약했습니다.",
            "",
            "| Stage | 방 좌표(행,열) | 숫자 정답 | 실제 판정 조건 | 클리어 후 설명 |",
            "|---:|---:|---:|---|---|",
        ]
    )
    for row in stages:
        numeric = "-" if row["numeric_answer"] is None else str(row["numeric_answer"])
        room = row["room"]
        requirements = "; ".join(row["requirements"])
        lines.append(
            f"| {row['stage']} | {room['row']},{room['column']} | {numeric} | {md_cell(requirements)} | {md_cell(row['completion_explanation'])} |"
        )
    lines.extend(
        [
            "",
            "숫자 입력형 정답만 따로 모으면:",
            "",
            "- " + ", ".join(f"{row['stage']}번={row['numeric_answer']}" for row in numeric_stages),
            "",
            "## 트리거 통계",
            "",
            f"- 총 조건 {sum(condition_counts.values()):,}개, 총 액션 {sum(action_counts.values()):,}개",
            f"- Preserve Trigger 액션: {action_counts[3]}개",
            f"- 텍스트 출력: {action_counts[9]}개, WAV 재생: {action_counts[8]}개, Wait: {action_counts[4]}개",
            f"- 유닛 이동: {action_counts[39]}개, 로케이션 내 유닛 제거/처치: {action_counts[23] + action_counts[25]}개",
            "- 트리거 실행 플래그는 280개 모두 0이며, 보존 여부는 Preserve Trigger 액션으로 제어합니다.",
            "",
            "주요 조건 유형:",
            "",
        ]
    )
    for condition_id, count in condition_counts.most_common():
        lines.append(f"- {CONDITION_NAMES.get(condition_id, condition_id)}: {count}")
    lines.extend(["", "주요 액션 유형:", ""])
    for action_id, count in action_counts.most_common():
        lines.append(f"- {ACTION_NAMES.get(action_id, action_id)}: {count}")
    lines.extend(["", "트리거 소유자 조합:", ""])
    for owners_key, count in trigger_owner_counts.most_common():
        label = ", ".join(player_name(owner) for owner in owners_key)
        lines.append(f"- {label}: {count}")

    lines.extend(
        [
            "",
            "## 유닛·오브젝트",
            "",
            f"- 배치 유닛: {len(units)}개",
            f"- 커스텀 유닛 설정: {len(custom_rows)}종",
            f"- 스프라이트/두대드(THG2): {len(sprites)}개",
            f"- CUWP 유닛 속성 슬롯: 64개",
            "- PUNI 해석상 전 플레이어에게 228개 유닛 타입이 모두 허용되어 있습니다.",
            "- UPGx/TECx의 비용·시간은 모두 기본값 사용 플래그가 켜져 있습니다. 실제 퍼즐 표현은 커스텀 유닛 이름/체력과 트리거가 담당합니다.",
            "",
            "배치 수가 많은 유닛:",
            "",
            "| 유닛 | 수 |",
            "|---|---:|",
        ]
    )
    for (_, name), count in unit_type_counts.most_common(20):
        lines.append(f"| {name} | {count} |")
    lines.extend(
        [
            "",
            "대표적인 커스텀 이름:",
            "",
            "| 원본 유닛 | 커스텀 이름 | HP | Mineral/Gas |",
            "|---|---|---:|---:|",
        ]
    )
    interesting = [row for row in custom_rows if row["custom_name"].strip()][:30]
    for row in interesting:
        lines.append(
            f"| {row['default_name']} | {md_cell(row['custom_name'])} | {row['hitpoints']:g} | {row['mineral_cost']}/{row['gas_cost']} |"
        )

    lines.extend(
        [
            "",
            "## 지형·시야·로케이션",
            "",
            f"- 유효 지형: {map_width * map_height:,}개 메가타일, 고유 타일값 {len(set(struct.unpack(f'<{map_width * map_height}H', valid_terrain_sections[0])))}종",
            f"- 초기 MASK 값: {dict(mask_counts)}. 모든 타일이 `0xFF`로 설정돼 전 플레이어 기준 미탐색 상태에서 시작합니다.",
            f"- 좌표가 있는 로케이션: {location_nonempty}개; 빈 예비 슬롯: {len(locations) - location_nonempty}개",
            "- Location 64는 전체 맵(0,0–6144,6144)의 Anywhere입니다.",
            "- 방 내부는 480×480px(15×15타일), 방 간격은 96px(3타일)이며 진행은 행마다 좌우 방향이 바뀝니다.",
            "",
            "## 미션 브리핑",
            "",
            "- 제목과 제작자, ‘힌트는 표시된 게 전부’라는 안내를 표시합니다.",
            "- P1–P4의 저글링 초상화를 표시하고 `BGM.wav`를 재생한 뒤 10초 대기합니다.",
            "",
            "## 내장 오디오",
            "",
            "| 파일 | 크기 | 포맷 | 길이 | SHA-256 |",
            "|---|---:|---|---:|---|",
        ]
    )
    for row in audio:
        lines.append(
            f"| {row['archive_name']} | {row['size_bytes']:,} B | {row['sample_rate_hz']} Hz, {row['channels']}ch, {row['sample_width_bytes'] * 8}-bit PCM | {row['duration_seconds']:.3f}s | `{row['sha256']}` |"
        )
    lines.extend(
        [
            "",
            "## 산출물 안내",
            "",
            "- `triggers_full.md`: 280개 트리거의 조건/액션 전체 해석",
            "- `map_data.json`: 타일, MASK, 모든 트리거 원시 필드, 유닛, 스프라이트, 로케이션, 설정을 포함한 구조화 데이터",
            "- `units.csv`, `locations.csv`, `custom_unit_settings.csv`, `strings_referenced.csv`: 표 형식 원자료",
            "- `scenario.chk`: 복호화·압축 해제된 원본 시나리오 데이터",
            "- `audio/`: 내장 WAV 5개",
            "- `terrain_overview.png`: 유효 MTXM 타일을 의사색으로 표시하고 배치 유닛/방 번호를 겹친 구조도",
            "",
            "## 무결성",
            "",
            f"- 원본 SCX SHA-256: `{sha256(source.read_bytes())}`",
            f"- 추출 scenario.chk SHA-256: `{sha256(chk)}`",
            f"- 원본 크기: {source.stat().st_size:,}바이트; CHK 크기: {len(chk):,}바이트",
            "- 원본 SCX는 수정하지 않았습니다.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Analyze a protected StarCraft SCX map")
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    audio_dir = output / "audio"
    audio_dir.mkdir(exist_ok=True)

    archive = mpqapi.MPQ.open(str(source))
    chk = archive.extract_file(r"staredit\scenario.chk")
    (output / "scenario.chk").write_bytes(chk)
    sections = parse_sections(chk)
    width, height = struct.unpack("<HH", first_section(sections, "DIM "))
    string_data = first_section(sections, "STR ")
    strings = ProtectedStringTable(string_data)
    triggers = parse_triggers(first_section(sections, "TRIG"))
    units = parse_units(first_section(sections, "UNIT"))
    locations = parse_locations(first_section(sections, "MRGN"), strings)
    sprites = parse_sprites(first_section(sections, "THG2"))
    unit_properties = parse_unit_properties(first_section(sections, "UPRP"))
    custom_units = parse_custom_units(first_section(sections, "UNIx"), strings)
    upgrades = parse_upgrades(first_section(sections, "UPGx"))
    tech = parse_tech(first_section(sections, "TECx"))
    restrictions = parse_restrictions(sections)
    stages = stage_summaries(triggers, strings, units, custom_units)
    referenced_strings = collect_referenced_strings(sections, triggers, strings, custom_units)

    terrain_sections = all_sections(sections, "MTXM")
    valid_terrain = next(data for data in terrain_sections if len(data) == width * height * 2)
    tiles = list(struct.unpack(f"<{width * height}H", valid_terrain))
    mask = list(first_section(sections, "MASK"))

    audio_names = [
        r"staredit\wav\BGM.wav",
        r"staredit\wav\typing.wav",
        r"staredit\wav\Victory1.wav",
        r"staredit\wav\JoHap.wav",
        r"staredit\wav\BGM3.wav",
    ]
    audio_rows = []
    for archive_name in audio_names:
        data = archive.extract_file(archive_name)
        filename = archive_name.rsplit("\\", 1)[-1]
        (audio_dir / filename).write_bytes(data)
        row = {
            "archive_name": archive_name,
            "filename": filename,
            "size_bytes": len(data),
            "sha256": sha256(data),
        }
        row.update(audio_metadata(data))
        audio_rows.append(row)

    parsed_sections_json = [
        {key: value for key, value in asdict(section).items() if key != "data"} for section in sections
    ]
    json_data = {
        "source": {
            "path": str(source),
            "size_bytes": source.stat().st_size,
            "sha256": sha256(source.read_bytes()),
            "scenario_chk_size_bytes": len(chk),
            "scenario_chk_sha256": sha256(chk),
        },
        "map": {
            "width_tiles": width,
            "height_tiles": height,
            "era_raw": struct.unpack("<H", first_section(sections, "ERA "))[0],
            "tileset_low_3_bits": struct.unpack("<H", first_section(sections, "ERA "))[0] & 7,
            "version_raw": struct.unpack("<H", first_section(sections, "VER "))[0],
            "string_declared_count": strings.declared_count,
        },
        "sections": parsed_sections_json,
        "tiles": tiles,
        "fog_mask": mask,
        "players": {
            "controllers": list(first_section(sections, "OWNR")),
            "races": list(first_section(sections, "SIDE")),
            "colors": list(first_section(sections, "COLR")),
            "force_raw": list(first_section(sections, "FORC")),
        },
        "units": units,
        "sprites": sprites,
        "locations": locations,
        "unit_properties": unit_properties,
        "custom_units": custom_units,
        "upgrades": upgrades,
        "tech": tech,
        "restrictions": restrictions,
        "triggers": [asdict(trigger) for trigger in triggers],
        "stages": stages,
        "referenced_strings": referenced_strings,
        "audio": audio_rows,
    }
    (output / "map_data.json").write_text(
        json.dumps(json_data, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    write_csv(
        output / "units.csv",
        units,
        [
            "index",
            "serial",
            "x",
            "y",
            "unit_id",
            "unit_name",
            "owner_id",
            "owner_name",
            "hitpoints_percent",
            "shields_percent",
            "energy_percent",
            "resources",
            "hangar_count",
            "state_flags",
            "valid_state_flags",
            "valid_property_flags",
            "relation_class",
            "related_serial",
            "unused",
        ],
    )
    write_csv(
        output / "locations.csv",
        locations,
        [
            "id",
            "left",
            "top",
            "right",
            "bottom",
            "width",
            "height",
            "string_id",
            "decoded_name",
            "name_reference_in_section",
            "elevation_flags",
            "empty_rectangle",
        ],
    )
    custom_rows = [row for row in custom_units if "unit_id" in row]
    write_csv(
        output / "custom_unit_settings.csv",
        custom_rows,
        [
            "unit_id",
            "default_name",
            "custom_name_string_id",
            "custom_name",
            "hitpoints_raw_256ths",
            "hitpoints",
            "shields",
            "armor",
            "build_time_ticks",
            "mineral_cost",
            "gas_cost",
        ],
    )
    string_csv_rows = [
        {
            **row,
            "sources": "; ".join(row["sources"]),
        }
        for row in referenced_strings
    ]
    write_csv(
        output / "strings_referenced.csv",
        string_csv_rows,
        ["string_id", "sources", "decoded_cp949", "clean_text", "raw_hex", "sha256"],
    )

    render_room_layout(output / "room_layout.png")
    render_terrain(output / "terrain_overview.png", tiles, width, height, units)
    write_trigger_report(output / "triggers_full.md", triggers, strings)
    report = build_report(
        source,
        chk,
        sections,
        strings,
        triggers,
        units,
        sprites,
        locations,
        custom_units,
        stages,
        audio_rows,
        width,
        height,
        terrain_sections,
    )
    (output / "analysis_report.md").write_text(report, encoding="utf-8")

    manifest = []
    for path in sorted(
        p for p in output.rglob("*") if p.is_file() and p.name != "manifest.json"
    ):
        data = path.read_bytes()
        manifest.append(
            {
                "path": str(path.relative_to(output)).replace("\\", "/"),
                "size_bytes": len(data),
                "sha256": sha256(data),
            }
        )
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(
        json.dumps(
            {
                "output": str(output),
                "sections": len(sections),
                "random_sections": sum(not section.known for section in sections),
                "triggers": len(triggers),
                "units": len(units),
                "sprites": len(sprites),
                "locations": len(locations),
                "stages": len(stages),
                "audio_files": len(audio_rows),
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
