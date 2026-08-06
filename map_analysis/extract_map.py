from __future__ import annotations

import hashlib
import sys
from pathlib import Path

from eudplib.bindings._rust import mpqapi


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: extract_map.py MAP.scx OUTPUT_DIR")

    map_path = Path(sys.argv[1]).resolve()
    output_dir = Path(sys.argv[2]).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    archive = mpqapi.MPQ.open(str(map_path))
    try:
        chk = archive.extract_file(r"staredit\scenario.chk")
        locale = 0
        if len(chk) <= 1200:
            mpqapi.MPQ.set_file_locale(0x409)
            chk = archive.extract_file(r"staredit\scenario.chk")
            locale = 0x409
            mpqapi.MPQ.set_file_locale(0)

        chk_path = output_dir / "scenario.chk"
        chk_path.write_bytes(chk)

        try:
            names = archive.get_file_names_from_listfile()
        except Exception:
            names = []

        print(f"map={map_path}")
        print(f"map_sha256={hashlib.sha256(map_path.read_bytes()).hexdigest()}")
        print(f"chk={chk_path}")
        print(f"chk_size={len(chk)}")
        print(f"chk_sha256={hashlib.sha256(chk).hexdigest()}")
        print(f"chk_locale=0x{locale:04X}")
        print(f"listfile_entries={len(names)}")
        for name in names:
            print(f"file={name}")
    finally:
        mpqapi.MPQ.set_file_locale(0)


if __name__ == "__main__":
    main()
