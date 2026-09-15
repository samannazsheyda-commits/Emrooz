#!/usr/bin/env python3
"""Verify every launcher path renders the approved Emrooz artwork."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageOps, ImageStat


DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def normalized(path: Path, size: int = 256) -> Image.Image:
    with Image.open(path) as image:
        rgba = image.convert("RGBA")
        square = ImageOps.fit(rgba, (size, size), method=Image.Resampling.LANCZOS)
        background = Image.new("RGBA", square.size, "#F7F2EA")
        return Image.alpha_composite(background, square).convert("RGB")


def mean_channel_error(actual: Image.Image, expected: Image.Image) -> float:
    return sum(ImageStat.Stat(ImageChops.difference(actual, expected)).mean) / 3.0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--res", required=True, type=Path)
    args = parser.parse_args()

    expected = normalized(args.reference)
    failures: list[str] = []
    for directory, pixels in DENSITIES.items():
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            path = args.res / directory / name
            if not path.is_file():
                failures.append(f"missing {path}")
                continue
            with Image.open(path) as image:
                if image.size != (pixels, pixels):
                    failures.append(f"{path}: expected {pixels}x{pixels}, got {image.size}")
            error = mean_channel_error(normalized(path), expected)
            # A 48 px launcher loses a few edge pixels when normalized again for
            # comparison; unrelated artwork is an order of magnitude farther away.
            if error > 5.0:
                failures.append(f"{path}: approved-artwork error {error:.2f} > 5.00")

    adaptive = args.res / "drawable-nodpi" / "ic_launcher_foreground_png.png"
    if not adaptive.is_file():
        failures.append(f"missing {adaptive}")
    elif mean_channel_error(normalized(adaptive), expected) > 3.0:
        failures.append(f"{adaptive}: adaptive foreground is not approved artwork")

    if failures:
        raise SystemExit("Launcher verification failed:\n- " + "\n- ".join(failures))
    print("All legacy and adaptive launchers render the approved Emrooz artwork.")


if __name__ == "__main__":
    main()
