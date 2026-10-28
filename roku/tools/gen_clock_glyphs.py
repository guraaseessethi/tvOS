"""White 64px glyphs for the timer and alarm pills (same style as the pack's ic_*.png: 4px-ish round strokes)."""
import math
import os
import sys
from PIL import Image, ImageDraw

S = 8            # draw at 8x, then downsample for smooth edges
W = 64 * S
LINE = int(4.5 * S)
WHITE = (255, 255, 255, 255)


def arc_pts(cx, cy, r, a0, a1, n=64):
    return [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
            for a in [a0 + (a1 - a0) * i / n for i in range(n + 1)]]


def stroke(d, pts):
    d.line(pts, fill=WHITE, width=LINE, joint="curve")
    r = LINE / 2
    for x, y in (pts[0], pts[-1]):
        d.ellipse((x - r, y - r, x + r, y + r), fill=WHITE)


def timer():
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx, cy, r = 32 * S, 36 * S, 20 * S
    stroke(d, arc_pts(cx, cy, r, 0, 360, 96))
    stroke(d, [(cx, cy), (cx, cy - 11 * S)])          # hand
    stroke(d, [(cx, cy), (cx + 7 * S, cy + 5 * S)])
    stroke(d, [(26 * S, 8 * S), (38 * S, 8 * S)])       # crown
    stroke(d, [(cx, 8 * S), (cx, 15 * S)])
    return im


def alarm():
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Bell: dome + flared rim, clapper, two "ringing" arcs.
    body = arc_pts(32 * S, 30 * S, 15 * S, 180, 360, 64)
    pts = [(14 * S, 46 * S), (17 * S, 42 * S), (17 * S, 30 * S)] + body + [(47 * S, 30 * S), (47 * S, 42 * S), (50 * S, 46 * S), (14 * S, 46 * S)]
    stroke(d, pts)
    stroke(d, [(32 * S, 10 * S), (32 * S, 15 * S)])
    stroke(d, arc_pts(32 * S, 48 * S, 5 * S, 20, 160, 24))
    stroke(d, arc_pts(32 * S, 30 * S, 25 * S, 200, 235, 16))
    stroke(d, arc_pts(32 * S, 30 * S, 25 * S, 305, 340, 16))
    return im


out = sys.argv[1] if len(sys.argv) > 1 else "extra/images/ui"
for name, im in (("ic_timer", timer()), ("ic_alarm", alarm())):
    im.resize((64, 64), Image.LANCZOS).save(os.path.join(out, name + ".png"))
print("glyphs written")
