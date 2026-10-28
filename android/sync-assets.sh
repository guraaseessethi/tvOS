#!/bin/sh
# Copies the design pack (../assets) into the app's resources with Android-safe names.
# Run it again after the pack changes; the copies are committed so a plain build needs nothing else.
set -e
cd "$(dirname "$0")"
P=../assets
R=app/src/main/res
mkdir -p $R/font $R/drawable-nodpi
for f in $P/fonts/*.ttf; do
  n=$(basename "$f" .ttf | tr 'A-Z-' 'a-z_')
  cp "$f" "$R/font/$n.ttf"
done
for k in starry sunflower irises blossom snow; do
  cp $P/images/wall/wall_${k}.jpg $P/images/wall/wall_${k}_blur.jpg $P/images/wall/wall_${k}_thumb.jpg $R/drawable-nodpi/
done
for c in rose sky mint wheat lilac peach aqua cream; do
  cp $P/scripts/tile_textures/tile_${c}.jpg $R/drawable-nodpi/card_${c}.jpg
done
cp $P/images/brand/zeus_mark_120.png $P/images/brand/zeus_mark_cream_120.png $P/images/brand/lumen_logo_ink.png $R/drawable-nodpi/
echo "assets synced"
