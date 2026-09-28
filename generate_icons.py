#!/usr/bin/env python3
"""
Builds Android launcher icons from assets/app-icon-source.png into android_icons/mipmap-*/,
which patch_android.py then copies into the generated project's res/ folder. Run automatically
by the build script; re-run any time you replace assets/app-icon-source.png.

Both ic_launcher.png and ic_launcher_round.png are the full artwork, unmasked and uncropped by
this script, scaled up to fill almost the entire canvas (no adaptive-icon foreground/background
split, which would otherwise let Android itself mask the art into a circle/squircle/teardrop
depending on the launcher). Some launchers still apply their own shape to a plain legacy icon
regardless \u2014 that's a launcher setting outside the app's control, not something Blocker's
build does.
"""
from pathlib import Path
from PIL import Image

here = Path(__file__).resolve().parent
src_path = here / "assets" / "app-icon-source.png"
out = here / "android_icons"

# (folder, pixel size)
DENSITIES = [
    ("mdpi", 48),
    ("hdpi", 72),
    ("xhdpi", 96),
    ("xxhdpi", 144),
    ("xxxhdpi", 192),
]

# Almost full bleed; a sliver of margin avoids resize/anti-aliasing artifacts at the exact edge.
FILL_RATIO = 0.98


def load_trimmed(path: Path) -> Image.Image:
    """Loads the source art and trims it to its non-transparent bounding box \u2014 the only
    cropping this script does, and only to remove blank padding around the artwork itself."""
    im = Image.open(path).convert("RGBA")
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im


def square_canvas(art: Image.Image, size: int) -> Image.Image:
    """Centers `art` on a transparent size x size canvas, preserving its aspect ratio exactly."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    scale = (size * FILL_RATIO) / max(art.size)
    w, h = max(1, round(art.size[0] * scale)), max(1, round(art.size[1] * scale))
    resized = art.resize((w, h), Image.LANCZOS)
    canvas.paste(resized, ((size - w) // 2, (size - h) // 2), resized)
    return canvas


def main():
    art = load_trimmed(src_path)

    for folder, px in DENSITIES:
        d = out / f"mipmap-{folder}"
        d.mkdir(parents=True, exist_ok=True)
        icon = square_canvas(art, px)
        icon.save(d / "ic_launcher.png")
        icon.save(d / "ic_launcher_round.png")  # identical, uncropped \u2014 see module docstring

    print(f"Generated icons in {out}")


if __name__ == "__main__":
    main()
