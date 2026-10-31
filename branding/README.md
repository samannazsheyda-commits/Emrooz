# Approved Emrooz artwork

`source/` contains the unmodified, approved copper bookmark and Persian wordmark:
the 512×512 launcher icon and 1080×1920 light/dark splash posters.

Run `python tools/generate-branding.py` with Pillow installed to reproduce all
Android PNG/WebP assets and `out/branding/emrooz-original-icons.zip`.
The ZIP includes the 512px original and mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi launchers
at 48/72/96/144/192px, plus the transparent adaptive foreground.

Launcher icons use one uniform scale of the entire original square. Adaptive
artwork is uniformly fit into the central 66dp safe circle of a 108dp canvas,
including the lower corners of the Persian wordmark under circular masks.
The splash retains every original pixel using lossless WebP, with Compose `Fit`
and matching cream/navy backgrounds for screen shapes other than 9:16.

`out/branding/branding-proof.png` compares short/tall phone viewports and icon
densities; `validation.json` records source and rendered artwork proportions.

Android 12+ uses matching light/night system splash PNGs and backgrounds from
the approved posters. The OS splash follows the device theme; once settings
load, the in-app splash follows the user's saved Day/Night choice.
