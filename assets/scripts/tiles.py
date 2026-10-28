"""Painted pastel tile backgrounds (impasto swirl texture) for Lumen app icons. 2x size."""
import math, random, sys, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
W, H = 560, 380

TILES = {
    'rose':  ['#F7C6CF', '#F3B3C0', '#FBDDE3', '#EFA3B3'],
    'sky':   ['#BCD6FF', '#A9C8FB', '#D6E6FF', '#98BAF5'],
    'mint':  ['#BFEAD7', '#A8E0C7', '#D8F4E8', '#94D6BA'],
    'wheat': ['#FFE2A0', '#FFD67E', '#FFEDC4', '#FCCB66'],
    'lilac': ['#D6CBFF', '#C6B8FB', '#E6DEFF', '#B6A6F5'],
    'peach': ['#FFD0B8', '#FFC0A0', '#FFE0D0', '#FBB08C'],
    'aqua':  ['#BDE9F0', '#A6E0EA', '#D6F3F7', '#92D6E2'],
    'cream': ['#FBF3DC', '#F6EACB', '#FFF9EA', '#EFE1BC'],
}


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def paint(name, cols, seed):
    rng = random.Random(seed)
    img = Image.new('RGB', (W, H), hexrgb(cols[0]))
    d = ImageDraw.Draw(img)
    cx, cy = rng.uniform(.3, .7) * W, rng.uniform(.3, .7) * H

    def ang(x, y):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy) + 1
        swirl = math.exp(-(r / 220) ** 2) * 1.6
        base = 0.25 * math.sin(y / 60 + x / 140)
        return math.atan2(dy, dx) + math.pi / 2 * swirl + base * (1 - swirl)

    for layer, (sp, L, wd, jit) in enumerate([(14, 46, 16, 8), (9, 30, 9, 12)]):
        pts = [(x + rng.uniform(-sp, sp), y + rng.uniform(-sp, sp))
               for y in range(-20, H + 20, sp) for x in range(-20, W + 20, sp)]
        rng.shuffle(pts)
        for (x, y) in pts:
            c = hexrgb(rng.choice(cols if layer else cols[:3]))
            c = tuple(int(max(0, min(255, v + rng.uniform(-jit, jit)))) for v in c)
            seg = [(x, y)]
            px, py = x, y
            for _ in range(4):
                a = ang(px, py)
                px += math.cos(a) * L / 4
                py += math.sin(a) * L / 4
                seg.append((px, py))
            d.line(seg, fill=c, width=wd, joint='curve')
            hl = tuple(min(255, int(v * 1.05 + 14)) for v in c)
            d.line(seg[1:-1], fill=hl, width=max(2, wd // 4))
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    img.save(f'{OUT}/tile_{name}.jpg', quality=86)


for i, (n, c) in enumerate(TILES.items()):
    paint(n, c, 100 + i)
    print('tile', n)
