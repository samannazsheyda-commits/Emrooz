#!/usr/bin/env python3
"""Rebuild Android assets from the approved, unmodified artwork. Requires Pillow.

Run from any directory: python tools/generate-branding.py
Outputs a portable icon ZIP and a visual proof sheet in ignored out/branding/.
"""
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile
from io import BytesIO
import json
import math
import os
import tempfile

from PIL import Image, ImageChops, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "branding/source"
RES = ROOT / "app/src/main/res"
OUT = ROOT / "out/branding"
OUT.mkdir(parents=True, exist_ok=True)
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
CREAM = (247, 242, 234)
NAVY = (13, 23, 38)


def save_image_atomic(image, target, **options):
    """An interrupted encoder must never truncate a resource AAPT may read."""
    buffer = BytesIO()
    image.save(buffer, format=Image.registered_extensions()[target.suffix], **options)
    encoded = buffer.getvalue()
    assert encoded, target
    with Image.open(BytesIO(encoded)) as candidate:
        candidate.verify()
    if target.exists() and target.read_bytes() == encoded:
        return
    with tempfile.NamedTemporaryFile(dir=OUT, delete=False) as temporary:
        temporary.write(encoded)
        temporary.flush()
        os.fsync(temporary.fileno())
        temporary_path = Path(temporary.name)
    try:
        os.replace(temporary_path, target)
    finally:
        temporary_path.unlink(missing_ok=True)


def artwork_bbox(image, background):
    difference = ImageChops.difference(image.convert("RGB"), Image.new("RGB", image.size, background))
    return difference.convert("L").point(lambda value: 255 if value > 24 else 0).getbbox()


icon = Image.open(SOURCE / "icon-512.png").convert("RGB")
assert icon.size == (512, 512)
archive_files = [(SOURCE / "icon-512.png", "icon-512.png")]
for density, size in DENSITIES.items():
    directory = RES / f"mipmap-{density}"
    directory.mkdir(exist_ok=True)
    # Resize the whole square once; do not independently resize a logo bounding box.
    resized = icon.resize((size, size), Image.Resampling.LANCZOS)
    save_image_atomic(resized, directory / "ic_launcher.png")
    # Round launcher only masks the background; the source logo keeps the same scale.
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    rounded = resized.convert("RGBA")
    rounded.putalpha(mask.resize((size, size), Image.Resampling.LANCZOS))
    save_image_atomic(rounded, directory / "ic_launcher_round.png")
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        archive_files.append((directory / name, f"mipmap-{density}/{name}"))

# The approved icon has a cream matte. Remove only near-matte pixels, retaining
# the original copper and Persian lettering pixels. The same cream is behind it
# on Android, so the soft original edge remains visually unchanged.
foreground = icon.convert("RGBA")
pixels = foreground.load()
for y in range(icon.height):
    for x in range(icon.width):
        red, green, blue, _ = pixels[x, y]
        distance = max(abs(red - CREAM[0]), abs(green - CREAM[1]), abs(blue - CREAM[2]))
        alpha = min(255, max(0, (distance - 12) * 255 // 12))
        pixels[x, y] = red, green, blue, alpha
foreground = foreground.crop(foreground.getbbox())
# 108dp adaptive canvas at xxxhdpi. Fit into the central 66dp safe circle;
# this uniform scale is especially important for the tall bookmark+wordmark.
adaptive = Image.new("RGBA", (432, 432))
scale = 264 / math.hypot(foreground.width, foreground.height)
foreground = foreground.resize((int(foreground.width * scale), int(foreground.height * scale)), Image.Resampling.LANCZOS)
assert math.hypot(foreground.width, foreground.height) <= 264
adaptive.alpha_composite(foreground, ((432 - foreground.width) // 2, (432 - foreground.height) // 2))
drawable = RES / "drawable-nodpi"
save_image_atomic(adaptive, drawable / "ic_launcher_foreground_png.png")
archive_files.append((drawable / "ic_launcher_foreground_png.png", "adaptive-foreground-432.png"))

proof = Image.new("RGB", (1080, 1100), "#DDDDDD")
draw = ImageDraw.Draw(proof)
metrics = {"source_icon_art_bbox": artwork_bbox(icon, CREAM), "launcher_sizes": DENSITIES,
           "adaptive_art_size": foreground.size, "adaptive_safe_circle_diameter": 264}
for index, (name, background) in enumerate((("light", CREAM), ("dark", NAVY))):
    splash = Image.open(SOURCE / f"splash-{name}.png").convert("RGB")
    assert splash.size == (1080, 1920)
    assert splash.getpixel((0, 0)) == background
    # Android 12's system launch screen also needs an unwarped logo. Its
    # theme-qualified PNG follows the OS theme before saved app settings load.
    system_logo = splash.convert("RGBA")
    system_pixels = system_logo.load()
    for y in range(splash.height):
        for x in range(splash.width):
            red, green, blue, _ = system_pixels[x, y]
            distance = max(abs(red - background[0]), abs(green - background[1]), abs(blue - background[2]))
            alpha = min(255, max(0, (distance - 12) * 255 // 12))
            system_pixels[x, y] = red, green, blue, alpha
    system_logo = system_logo.crop(system_logo.getbbox())
    system_scale = 264 / math.hypot(system_logo.width, system_logo.height)
    system_logo = system_logo.resize((int(system_logo.width * system_scale), int(system_logo.height * system_scale)), Image.Resampling.LANCZOS)
    system_canvas = Image.new("RGBA", (432, 432))
    system_canvas.alpha_composite(system_logo, ((432 - system_logo.width) // 2, (432 - system_logo.height) // 2))
    system_directory = RES / ("drawable-nodpi" if name == "light" else "drawable-night-nodpi")
    system_directory.mkdir(exist_ok=True)
    save_image_atomic(system_canvas, system_directory / "splash_system_logo.png")
    # Lossless encoding retains every source pixel, including the original proportions.
    save_image_atomic(splash, drawable / f"splash_emrooz_{name}.webp", lossless=True, method=6)
    assert ImageChops.difference(splash, Image.open(drawable / f"splash_emrooz_{name}.webp").convert("RGB")).getbbox() is None
    for device_index, viewport in enumerate(((360, 640), (360, 800))):
        fitted = ImageOps.contain(splash, viewport, Image.Resampling.LANCZOS)
        screen = Image.new("RGB", viewport, background)
        screen.paste(fitted, ((viewport[0] - fitted.width) // 2, (viewport[1] - fitted.height) // 2))
        # Preview at 75%; both axes use the very same scale.
        preview = screen.resize((270, viewport[1] * 3 // 4), Image.Resampling.LANCZOS)
        x = (index * 2 + device_index) * 270
        draw.text((x + 8, 10), f"{name} {viewport[0]}x{viewport[1]} / Fit", fill="black")
        proof.paste(preview, (x, 36))
        box = artwork_bbox(screen, background)
        ratio = (box[2] - box[0]) / (box[3] - box[1])
        source_box = artwork_bbox(splash, background)
        source_ratio = (source_box[2] - source_box[0]) / (source_box[3] - source_box[1])
        assert abs(ratio - source_ratio) < 0.012, (name, viewport, ratio, source_ratio)
        metrics[f"{name}_{viewport[0]}x{viewport[1]}"] = {"art_bbox": box, "art_ratio": ratio, "source_ratio": source_ratio}

draw.text((20, 685), "Approved source icon / regenerated density icons / adaptive circle preview", fill="black")
proof.paste(icon.resize((256, 256), Image.Resampling.LANCZOS), (20, 720))
x = 300
for density, size in DENSITIES.items():
    resource = Image.open(RES / f"mipmap-{density}/ic_launcher.png")
    assert resource.size == (size, size)
    draw.text((x, 725), f"{density} {size}px", fill="black")
    proof.paste(resource, (x, 750))
    x += size + 28
adaptive_preview = Image.new("RGBA", adaptive.size, CREAM + (255,))
adaptive_preview.alpha_composite(adaptive)
# Android's common circular viewport covers the middle 72dp of the 108dp canvas.
adaptive_preview = adaptive_preview.crop((72, 72, 360, 360)).resize((144, 144), Image.Resampling.LANCZOS)
circle = Image.new("L", (144, 144))
ImageDraw.Draw(circle).ellipse((0, 0, 143, 143), fill=255)
proof.paste(adaptive_preview.convert("RGB"), (300, 945), circle)
save_image_atomic(proof, OUT / "branding-proof.png")
(OUT / "validation.json").write_text(json.dumps(metrics, indent=2) + "\n")
with ZipFile(OUT / "emrooz-original-icons.zip", "w", ZIP_DEFLATED) as archive:
    for path, name in archive_files:
        archive.write(path, name)
print(json.dumps(metrics, indent=2))
print(f"Assets regenerated; proof and icon ZIP: {OUT}")
