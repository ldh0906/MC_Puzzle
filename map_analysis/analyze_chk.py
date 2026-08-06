from __future__ import annotations

import argparse
import json
import struct
from collections import Counter
from pathlib import Path
from typing import Any


CONTROL_NAMES = {
    0x01: "<01>",
    0x02: "<02>",
    0x03: "<03>",
    0x04: "<04>",
    0x05: "<05>",
    0x06: "<06>",
    0x07: "<07>",
    0x08: "<08>",
    0x09: "\\t",
    0x0A: "\\n",
    0x0B: "<0B>",
    0x0C: "<0C>",
    0x0D: "\\r",
    0x0E: "<0E>",
    0x0F: "<0F>",
    0x10: "<10>",
    0x11: "<11>",
    0x12: "<12>",
    0x13: "<13>",
    0x14: "<14>",
    0x15: "<15>",
    0x16: "<16>",
    0x17: "<17>",
    0x18: "<18>",
    0x19: "<19>",
    0x1A: "<1A>",
    0x1B: "<1B>",
    0x1C: "<1C>",
    0x1D: "<1D>",
    0x1E: "<1E>",
    0x1F: "<1F>",
}


def decode_text(raw: bytes) -> str:
    for encoding in ("utf-8", "cp949"):
        try:
            text = raw.decode(encoding)
            break
        except UnicodeDecodeError:
            pass
    else:
        text = raw.decode("cp949", errors="replace")
    return "".join(CONTROL_NAMES.get(ord(ch), ch) for ch in text)


def parse_sections(data: bytes) -> list[dict[str, Any]]:
    sections: list[dict[str, Any]] = []
    offset = 0
    while offset + 8 <= len(data):
        name_bytes = data[offset : offset + 4]
        name = name_bytes.decode("latin1")
        size = struct.unpack_from("<i", data, offset + 4)[0]
        data_start = offset + 8
        data_end = data_start + size
        valid = size >= 0 and data_start <= data_end <= len(data)
        sections.append(
            {
                "name": name,
                "header_offset": offset,
                "data_offset": data_start,
                "size": size,
                "end_offset": data_end,
                "valid": valid,
            }
        )
        if not valid:
            break
        offset = data_end
    return sections


def latest_section(data: bytes, sections: list[dict[str, Any]], name: str) -> bytes:
    matches = [section for section in sections if section["name"] == name and section["valid"]]
    if not matches:
        return b""
    section = matches[-1]
    return data[section["data_offset"] : section["end_offset"]]


def parse_strx(section: bytes) -> list[bytes]:
    if len(section) < 4:
        return [b""]
    count = struct.unpack_from("<I", section, 0)[0]
    if 4 + count * 4 > len(section):
        raise ValueError(f"invalid STRx count {count} for section size {len(section)}")
    strings = [b""]
    for index in range(count):
        offset = struct.unpack_from("<I", section, 4 + index * 4)[0]
        if not 0 < offset < len(section):
            strings.append(b"")
            continue
        end = section.find(b"\0", offset)
        if end < 0:
            end = len(section)
        strings.append(section[offset:end])
    return strings


def string_at(strings: list[bytes], string_id: int) -> str:
    if 0 < string_id < len(strings):
        return decode_text(strings[string_id])
    return ""


def parse_locations(section: bytes, strings: list[bytes]) -> list[dict[str, Any]]:
    result = []
    for index in range(len(section) // 20):
        left, top, right, bottom, string_id, flags = struct.unpack_from("<4IHH", section, index * 20)
        if left or top or right or bottom or string_id or flags:
            result.append(
                {
                    "id": index + 1,
                    "name_id": string_id,
                    "name": string_at(strings, string_id),
                    "left": left,
                    "top": top,
                    "right": right,
                    "bottom": bottom,
                    "flags": flags,
                }
            )
    return result


def parse_units(section: bytes) -> list[dict[str, Any]]:
    result = []
    for index in range(len(section) // 36):
        fields = struct.unpack_from("<I6H4BI2H2I", section, index * 36)
        (
            instance_id,
            x,
            y,
            unit_id,
            relation_type,
            special_flags,
            valid_flags,
            owner,
            hitpoints,
            shields,
            energy,
            resources,
            hanger,
            state_flags,
            related_unit,
        ) = (
            fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
            fields[6], fields[7], fields[8], fields[9], fields[10], fields[11],
            fields[12], fields[13], fields[15],
        )
        result.append(
            {
                "index": index,
                "instance_id": instance_id,
                "x": x,
                "y": y,
                "unit_id": unit_id,
                "owner": owner,
                "relation_type": relation_type,
                "special_flags": special_flags,
                "valid_flags": valid_flags,
                "hitpoints": hitpoints,
                "shields": shields,
                "energy": energy,
                "resources": resources,
                "hanger": hanger,
                "state_flags": state_flags,
                "related_unit": related_unit,
            }
        )
    return result


def find_string_references(trigger_section: bytes) -> Counter[int]:
    refs: Counter[int] = Counter()
    for trig_offset in range(0, len(trigger_section) - 2399, 2400):
        actions = trigger_section[trig_offset + 320 : trig_offset + 2368]
        for action_offset in range(0, 2048, 32):
            action = actions[action_offset : action_offset + 32]
            if len(action) < 32:
                continue
            string_id = struct.unpack_from("<I", action, 4)[0]
            wav_id = struct.unpack_from("<I", action, 8)[0]
            action_type = action[26]
            if action_type and string_id:
                refs[string_id] += 1
            if action_type and wav_id:
                refs[wav_id] += 1
    return refs


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("chk", type=Path)
    parser.add_argument("output_dir", type=Path)
    args = parser.parse_args()

    data = args.chk.read_bytes()
    output_dir = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    sections = parse_sections(data)
    strx = latest_section(data, sections, "STRx")
    strings = parse_strx(strx)
    locations = parse_locations(latest_section(data, sections, "MRGN"), strings)
    units = parse_units(latest_section(data, sections, "UNIT"))
    trigger_data = latest_section(data, sections, "TRIG")
    trigger_string_refs = find_string_references(trigger_data)

    dim = latest_section(data, sections, "DIM ")
    width, height = struct.unpack_from("<HH", dim, 0) if len(dim) >= 4 else (0, 0)
    sprp = latest_section(data, sections, "SPRP")
    map_name_id, description_id = struct.unpack_from("<HH", sprp, 0) if len(sprp) >= 4 else (0, 0)

    summary = {
        "chk_size": len(data),
        "parsed_end": sections[-1]["header_offset"] if sections and not sections[-1]["valid"] else sections[-1]["end_offset"],
        "map_width_tiles": width,
        "map_height_tiles": height,
        "map_name_id": map_name_id,
        "map_name": string_at(strings, map_name_id),
        "description_id": description_id,
        "description": string_at(strings, description_id),
        "string_count": len(strings) - 1,
        "nonempty_string_count": sum(bool(value) for value in strings[1:]),
        "unit_count": len(units),
        "location_count": len(locations),
        "trigger_count": len(trigger_data) // 2400,
        "sections": sections,
        "units_by_owner": dict(sorted(Counter(unit["owner"] for unit in units).items())),
        "units_by_type": dict(sorted(Counter(unit["unit_id"] for unit in units).items())),
    }

    (output_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "locations.json").write_text(json.dumps(locations, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "units.json").write_text(json.dumps(units, ensure_ascii=False, indent=2), encoding="utf-8")

    with (output_dir / "strings.tsv").open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("id\tlength\ttrigger_refs\ttext\n")
        for index, raw in enumerate(strings[1:], 1):
            text = decode_text(raw).replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")
            handle.write(f"{index}\t{len(raw)}\t{trigger_string_refs[index]}\t{text}\n")

    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
