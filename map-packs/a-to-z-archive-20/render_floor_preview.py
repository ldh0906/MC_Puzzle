from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
COLORS = {
    "BLACK_CONCRETE": "#101419",
    "DEEPSLATE_TILES": "#34373d",
    "CYAN_CONCRETE": "#169c9c",
    "GOLD_BLOCK": "#f5c542",
    "PURPUR_BLOCK": "#a86eb5",
    "RED_CONCRETE": "#b52b2b",
    "WHITE_CONCRETE": "#e7e7e7",
    "BLUE_CONCRETE": "#2c4f9e",
    "LIME_CONCRETE": "#6fbe2d",
    "ORANGE_CONCRETE": "#e87919",
    "MAGENTA_CONCRETE": "#b83cac",
    "YELLOW_CONCRETE": "#e3c82f",
}


def main() -> None:
    pack = json.loads((ROOT / "map.jsonc").read_text(encoding="utf-8"))
    columns, rows = 4, 5
    cell, padding, heading = 10, 12, 28
    diagram = 15 * cell
    panel_width = diagram + padding * 2
    panel_height = diagram + heading + padding * 2
    image = Image.new("RGB", (columns * panel_width, rows * panel_height), "#171a1f")
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default(size=16)

    for index, room in enumerate(pack["rooms"]):
        panel_x = (index % columns) * panel_width
        panel_y = (index // columns) * panel_height
        draw.rounded_rectangle(
            (panel_x + 4, panel_y + 4, panel_x + panel_width - 4, panel_y + panel_height - 4),
            radius=8, fill="#22272e", outline="#4c5664", width=2,
        )
        letter = room["title"].split(" ", 1)[0]
        draw.text((panel_x + padding, panel_y + padding), f"{room['sequence']:02d}  {letter}",
                  fill="#f4f1de", font=font)
        visual = room["visual"]
        palette = {entry["tile"]: entry["material"] for entry in visual["palette"]}
        origin_x = panel_x + padding
        origin_y = panel_y + padding + heading
        for row in range(visual["height"]):
            for column in range(visual["width"]):
                tile = visual["cells"][row * visual["width"] + column]
                material = palette[tile]
                color = COLORS[material]
                x = origin_x + column * cell
                y = origin_y + row * cell
                draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill=color)
        draw.rectangle((origin_x, origin_y, origin_x + diagram - 1, origin_y + diagram - 1),
                       outline="#77808d", width=1)

    destination = ROOT / "floor-preview.png"
    image.save(destination)
    print(f"wrote {destination} ({image.width}x{image.height})")


if __name__ == "__main__":
    main()
