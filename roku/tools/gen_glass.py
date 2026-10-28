"""Liquid-glass highlight 9-patches for the yellow controls (no PIL needed).

Roku can't blur live, so "glass" is a translucent tint plus this white overlay: a bright rim
that fades toward the bottom, a specular sheen across the top, and a faint refraction glow
along the bottom edge. Guides match the pack's pill_64.9.png and card_r28.9.png.
"""
import math
import os
import struct
import sys
import zlib


def write_png(path, w, h, rgba):
    rows = b"".join(b"\x00" + bytes(rgba[y * w * 4:(y + 1) * w * 4]) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def depth(px, py, s, r):
    """Signed distance inside an s x s rounded rect of radius r (positive = inside)."""
    c = s / 2
    qx, qy = abs(px - c) - (c - r), abs(py - c) - (c - r)
    out = math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r
    return -out


def glass(path, s, r, stretch, sheen_end):
    w = h = s + 2
    img = bytearray(w * h * 4)
    n = 4  # supersampling per axis
    for y in range(s):
        for x in range(s):
            acc = 0.0
            for sy in range(n):
                for sx in range(n):
                    px, py = x + (sx + 0.5) / n, y + (sy + 0.5) / n
                    d = depth(px, py, s, r)
                    if d <= 0:
                        continue
                    t = py / s
                    rim = max(0.0, 1 - abs(d - 1.0) / 1.3) * (0.85 - 0.45 * t)
                    sheen = 0.0
                    if py < sheen_end and d > 2.5:
                        sheen = 0.58 * (1 - py / sheen_end) ** 1.5
                    glow = 0.0
                    if t > 0.78 and d > 1.5:
                        glow = 0.24 * (t - 0.78) / 0.22
                    a = 1 - (1 - rim) * (1 - sheen) * (1 - glow)
                    acc += a
            a = acc / (n * n)
            o = ((y + 1) * w + (x + 1)) * 4
            img[o:o + 4] = bytes((255, 255, 255, int(round(a * 255))))
    # 9-patch guides: stretch on top/left, full content on bottom/right.
    for i in stretch:
        img[(0 * w + i) * 4:(0 * w + i) * 4 + 4] = bytes((0, 0, 0, 255))
        img[(i * w + 0) * 4:(i * w + 0) * 4 + 4] = bytes((0, 0, 0, 255))
    for i in range(1, s + 1):
        img[((h - 1) * w + i) * 4:((h - 1) * w + i) * 4 + 4] = bytes((0, 0, 0, 255))
        img[(i * w + (w - 1)) * 4:(i * w + (w - 1)) * 4 + 4] = bytes((0, 0, 0, 255))
    write_png(path, w, h, img)


out = sys.argv[1]
os.makedirs(out, exist_ok=True)
glass(os.path.join(out, "glass_pill_64.9.png"), 64, 32, [32], 30)
glass(os.path.join(out, "glass_card_r28.9.png"), 72, 28, range(29, 44), 28)
