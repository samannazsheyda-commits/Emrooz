#!/usr/bin/env bash
set -euo pipefail
cp "$GITHUB_WORKSPACE/ci/lastnight/MainActivity.kt" app/src/main/java/com/nameemrooz/journal/MainActivity.kt
python3 "$GITHUB_WORKSPACE/ci/lastnight/apply_lastnight.py"
SPLASH=app/src/main/res/drawable-nodpi/splash_emrooz_light.webp
test -f "$SPLASH"
convert "$SPLASH" -crop 760x760+160+577 +repage -resize 1024x1024 app/src/main/res/drawable-nodpi/app_icon_emrooz_final.png
for spec in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  d=${spec%%:*}; n=${spec##*:}
  convert app/src/main/res/drawable-nodpi/app_icon_emrooz_final.png -resize ${n}x${n} app/src/main/res/mipmap-${d}/ic_launcher.png
  cp app/src/main/res/mipmap-${d}/ic_launcher.png app/src/main/res/mipmap-${d}/ic_launcher_round.png
done
