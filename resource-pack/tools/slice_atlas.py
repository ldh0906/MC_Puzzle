from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw


ICON_NAMES = (
    "party",
    "start",
    "saves",
    "hint",
    "slot_1",
    "slot_2",
    "slot_3",
    "back",
    "hint_request",
    "hint_view_1",
    "hint_view_2",
    "hint_view_3",
    "approve",
    "reject",
    "confirm",
    "cancel",
)


def component_points(alpha: Image.Image) -> list[list[tuple[int, int]]]:
    pixels = alpha.load()
    width, height = alpha.size
    visited: set[tuple[int, int]] = set()
    components: list[list[tuple[int, int]]] = []
    for y in range(height):
        for x in range(width):
            if pixels[x, y] == 0 or (x, y) in visited:
                continue
            pending = [(x, y)]
            visited.add((x, y))
            component: list[tuple[int, int]] = []
            while pending:
                current_x, current_y = pending.pop()
                component.append((current_x, current_y))
                for next_y in range(max(0, current_y - 1), min(height, current_y + 2)):
                    for next_x in range(max(0, current_x - 1), min(width, current_x + 2)):
                        point = (next_x, next_y)
                        if pixels[next_x, next_y] != 0 and point not in visited:
                            visited.add(point)
                            pending.append(point)
            components.append(component)
    return components


def clean_cell(cell: Image.Image) -> Image.Image:
    cleaned = cell.copy()
    components = component_points(cleaned.getchannel("A"))
    if not components:
        return cleaned

    main = max(components, key=len)
    left = min(point[0] for point in main)
    top = min(point[1] for point in main)
    right = max(point[0] for point in main)
    bottom = max(point[1] for point in main)
    margin_x = round(cleaned.width * 0.09)
    margin_y = round(cleaned.height * 0.09)
    neighborhood = (left - margin_x, top - margin_y, right + margin_x, bottom + margin_y)

    pixels = cleaned.load()
    for component in components:
        component_left = min(point[0] for point in component)
        component_top = min(point[1] for point in component)
        component_right = max(point[0] for point in component)
        component_bottom = max(point[1] for point in component)
        intersects = not (
            component_right < neighborhood[0]
            or component_left > neighborhood[2]
            or component_bottom < neighborhood[1]
            or component_top > neighborhood[3]
        )
        if not intersects:
            for x, y in component:
                pixels[x, y] = (0, 0, 0, 0)

    # The atlas uses magenta as a temporary key. Convert any generated edge
    # highlights that leaned toward that key into the pack's violet palette.
    for y in range(cleaned.height):
        for x in range(cleaned.width):
            red, green, blue, alpha = pixels[x, y]
            if alpha and red > 185 and blue > 185 and green < 90:
                pixels[x, y] = (min(red, 150), max(green, 28), blue, alpha)
    return cleaned


def slice_icon(cell: Image.Image, size: int = 32, occupied: int = 26) -> Image.Image:
    cell = clean_cell(cell)
    alpha = cell.getchannel("A")
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("Atlas cell contains no visible pixels")

    icon = cell.crop(bounds)
    scale = min(occupied / icon.width, occupied / icon.height)
    width = max(1, round(icon.width * scale))
    height = max(1, round(icon.height * scale))
    icon = icon.resize((width, height), Image.Resampling.NEAREST)

    output = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    output.alpha_composite(icon, ((size - width) // 2, (size - height) // 2))
    return output


def create_pack_icon(start_icon: Image.Image, destination: Path) -> None:
    canvas = Image.new("RGBA", (128, 128), "#111820")
    draw = ImageDraw.Draw(canvas)
    for offset, color in ((0, "#1d2930"), (8, "#287c79"), (12, "#b28a3e"), (16, "#111820")):
        draw.rectangle((offset, offset, 127 - offset, 127 - offset), fill=color)
    enlarged = start_icon.resize((96, 96), Image.Resampling.NEAREST)
    canvas.alpha_composite(enlarged, (16, 16))
    canvas.save(destination, optimize=True)


def create_preview(icons: dict[str, Image.Image], destination: Path) -> None:
    scale = 4
    cell_size = 40 * scale
    preview = Image.new("RGB", (cell_size * 4, cell_size * 4), "#10171d")
    draw = ImageDraw.Draw(preview)
    for index, name in enumerate(ICON_NAMES):
        row, column = divmod(index, 4)
        left, top = column * cell_size, row * cell_size
        draw.rectangle((left, top, left + cell_size - 1, top + cell_size - 1), outline="#287c79", width=4)
        icon = icons[name].resize((32 * scale, 32 * scale), Image.Resampling.NEAREST)
        preview.paste(icon, (left + 4 * scale, top + 4 * scale), icon)
    preview.save(destination, optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Slice the generated MCPuzzle 4x4 icon atlas.")
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--textures", required=True, type=Path)
    parser.add_argument("--pack-icon", required=True, type=Path)
    parser.add_argument("--preview", required=True, type=Path)
    arguments = parser.parse_args()

    atlas = Image.open(arguments.input).convert("RGBA")
    arguments.textures.mkdir(parents=True, exist_ok=True)
    arguments.pack_icon.parent.mkdir(parents=True, exist_ok=True)
    arguments.preview.parent.mkdir(parents=True, exist_ok=True)

    icons: dict[str, Image.Image] = {}
    for index, name in enumerate(ICON_NAMES):
        row, column = divmod(index, 4)
        left = round(column * atlas.width / 4)
        right = round((column + 1) * atlas.width / 4)
        top = round(row * atlas.height / 4)
        bottom = round((row + 1) * atlas.height / 4)
        icons[name] = slice_icon(atlas.crop((left, top, right, bottom)))
        icons[name].save(arguments.textures / f"{name}.png", optimize=True)

    create_pack_icon(icons["start"], arguments.pack_icon)
    create_preview(icons, arguments.preview)
    print(f"Wrote {len(icons)} item textures, pack icon, and preview")


if __name__ == "__main__":
    main()
