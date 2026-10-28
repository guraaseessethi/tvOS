#!/bin/sh
# Stages the channel with the untouched asset pack and zips it for sideloading.
set -e
cd "$(dirname "$0")"
rm -rf out/stage && mkdir -p out/stage
cp -R manifest source components out/stage/
cp -R ../assets/images ../assets/fonts out/stage/
cp -R extra/images/. out/stage/images/
# Lumen reopens every time the owner leaves an app, so the splash is the home painting itself:
# reopening looks like the painting appearing, not a logo card. (The pack's 3.6 MB PNG splash is dropped.)
cp extra/images/wall/wall_starry_home.jpg out/stage/images/brand/splash_fhd.jpg
rm out/stage/images/brand/splash_fhd.png
# Rounded painted cards (spec 3.3 pastels) that app logos sit on, 288x192 r34 like the tiles.
[ -x ../bridge/lumen-img ] || swiftc -O ../bridge/lumen-img.swift -o ../bridge/lumen-img
for p in rose sky mint wheat lilac peach aqua cream; do
  ../bridge/lumen-img card ../assets/scripts/tile_textures/tile_$p.jpg out/stage/images/tiles/card_$p.png 288 192 34
done
# Pack images the channel no longer uses: blur + scrims are baked into wall_<key>_home.jpg,
# and the sample glyph tiles are unused apart from tile_video (icon fallback).
rm -f out/stage/images/wall/*_blur.jpg out/stage/images/ui/blur_mask.png out/stage/images/ui/scrim_*.png
find out/stage/images/tiles -name 'tile_*.png' ! -name 'tile_video.png' -delete
rm -f out/lumen.zip
(cd out/stage && zip -qr -9 ../lumen.zip . -x '.*')
echo "Built $(pwd)/out/lumen.zip ($(du -h out/lumen.zip | cut -f1))"
