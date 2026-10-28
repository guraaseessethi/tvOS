"""Generates small white-alpha gradient PNGs the pack doesn't include (no PIL needed)."""
import struct, sys, zlib, os

def png(path, w, h, alpha_at):
    rows = b""
    for y in range(h):
        row = b"\x00"
        for x in range(w):
            row += bytes((255, 255, 255, alpha_at(x, y)))
        rows += row
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
# Row right-edge fade (spec 6.5): alpha 0 -> 80% left to right, drawn at 360x1080 with blendColor = deep.
png(os.path.join(out, "fade_right.png"), 64, 4, lambda x, y: round(204 * x / 63))
# Solid white pixel for tinted fills that need no rounding.
png(os.path.join(out, "white.png"), 4, 4, lambda x, y: 255)
