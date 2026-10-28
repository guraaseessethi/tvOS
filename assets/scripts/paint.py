"""Procedural Van Gogh style wallpapers for Lumen (bold pastel + ImpulsTrip yellow).

Each scene = a base colour field + a flow (angle) field, painted with thousands of
short, thick, curved strokes, then motif layers (stars, sun, cypress, irises...).
Outputs sharp + pre-blurred 1920x1080 JPGs and a 480x270 thumbnail per scene.
"""
import math, random, sys, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
os.makedirs(OUT, exist_ok=True)

YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)


def hexrgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def lerp(a, b, t):
    return a + (b - a) * t


def vgrad(stops):
    """vertical gradient from [(y, hex), ...] -> HxWx3"""
    ys = [s[0] for s in stops]
    cols = [hexrgb(s[1]) for s in stops]
    out = np.zeros((H, W, 3), np.float32)
    col = np.zeros((H, 3), np.float32)
    for y in range(H):
        for i in range(len(ys) - 1):
            if ys[i] <= y <= ys[i + 1]:
                t = (y - ys[i]) / max(1, ys[i + 1] - ys[i])
                col[y] = lerp(cols[i], cols[i + 1], t)
                break
        else:
            col[y] = cols[0] if y < ys[0] else cols[-1]
    out[:] = col[:, None, :]
    return out


def blend_blob(base, cx, cy, r, hexc, amt=1.0):
    d = np.sqrt((XX - cx) ** 2 + (YY - cy) ** 2)
    t = np.clip(1 - d / r, 0, 1) ** 1.6 * amt
    base += (hexrgb(hexc) - base) * t[..., None]


def vortex_field(vortices, base_angle=0.0, wave=(0.0, 300.0)):
    """vector sum of a base flow and tangential vortices -> angle array"""
    vx = np.cos(base_angle) * np.ones((H, W), np.float32)
    vy = np.sin(base_angle) * np.ones((H, W), np.float32)
    amp, wl = wave
    if amp:
        vy += amp * np.sin(XX / wl * 2 * math.pi + YY / (wl * 1.7))
    for (cx, cy, r, s) in vortices:
        dx, dy = XX - cx, YY - cy
        d2 = dx * dx + dy * dy
        f = np.exp(-d2 / (r * r)) * s
        dd = np.sqrt(d2) + 1e-3
        vx += (-dy / dd) * f
        vy += (dx / dd) * f
    return np.arctan2(vy, vx)


class Painter:
    def __init__(self, base, angle, seed):
        self.base = np.clip(base, 0, 255)
        self.angle = angle
        self.img = Image.fromarray(self.base.astype(np.uint8)).filter(ImageFilter.GaussianBlur(6))
        self.d = ImageDraw.Draw(self.img)
        self.rng = random.Random(seed)

    def stroke(self, x, y, col, length, width, angle_fn=None, curl=1.0):
        pts = [(x, y)]
        cx, cy = x, y
        steps = 4
        for _ in range(steps):
            ix = int(min(W - 1, max(0, cx)))
            iy = int(min(H - 1, max(0, cy)))
            a = self.angle[iy, ix] if angle_fn is None else angle_fn(cx, cy)
            cx += math.cos(a) * length / steps * curl
            cy += math.sin(a) * length / steps * curl
            pts.append((cx, cy))
        c = tuple(int(v) for v in col)
        self.d.line(pts, fill=c, width=width, joint="curve")
        r = width / 2
        for (px, py) in (pts[0], pts[-1]):
            self.d.ellipse([px - r, py - r, px + r, py + r], fill=c)
        # impasto highlight ridge
        hl = tuple(int(min(255, v * 1.12 + 18)) for v in col)
        self.d.line([(p[0] - 1, p[1] - 1.5) for p in pts[1:-1]], fill=hl, width=max(1, width // 4))

    def pass_(self, spacing, length, width, jitter=14, region=None, accents=None, accent_p=0.0,
              light=1.0):
        rng = self.rng
        ys = list(range(0, H + spacing, spacing))
        xs = list(range(0, W + spacing, spacing))
        pts = [(x + rng.uniform(-spacing / 2, spacing / 2), y + rng.uniform(-spacing / 2, spacing / 2))
               for y in ys for x in xs]
        rng.shuffle(pts)
        for (x, y) in pts:
            ix, iy = int(min(W - 1, max(0, x))), int(min(H - 1, max(0, y)))
            if region is not None and not region[iy, ix]:
                continue
            if accents and rng.random() < accent_p:
                col = hexrgb(rng.choice(accents))
            else:
                col = self.base[iy, ix] * light
            col = np.clip(col + rng.uniform(-jitter, jitter), 0, 255)
            self.stroke(x, y, col, length * rng.uniform(.7, 1.3), int(width * rng.uniform(.8, 1.2)))

    def ring_strokes(self, cx, cy, r0, r1, cols, width=9, dash=26, gap=0.35):
        """concentric dashed rings (stars, sun, moon halos)"""
        rng = self.rng
        r = r0
        i = 0
        while r < r1:
            circ = 2 * math.pi * r
            n = max(6, int(circ / dash))
            off = rng.random() * 2 * math.pi
            col = hexrgb(cols[i % len(cols)])
            for k in range(n):
                a0 = off + k * 2 * math.pi / n
                a1 = a0 + (2 * math.pi / n) * (1 - gap)
                seg = [(cx + math.cos(a0 + (a1 - a0) * t / 3) * r, cy + math.sin(a0 + (a1 - a0) * t / 3) * r)
                       for t in range(4)]
                c = tuple(int(v) for v in np.clip(col + rng.uniform(-12, 12), 0, 255))
                self.d.line(seg, fill=c, width=width, joint="curve")
            r += width * 1.05
            i += 1

    def disc(self, cx, cy, r, hexc, width=8):
        """fill a disc with short radial-ish strokes"""
        rng = self.rng
        col = hexrgb(hexc)
        for _ in range(int(r * r / 12)):
            a = rng.random() * 2 * math.pi
            d = math.sqrt(rng.random()) * r
            x, y = cx + math.cos(a) * d, cy + math.sin(a) * d
            ang = a + math.pi / 2
            L = rng.uniform(10, 20)
            c = tuple(int(v) for v in np.clip(col + rng.uniform(-14, 14), 0, 255))
            self.d.line([(x, y), (x + math.cos(ang) * L, y + math.sin(ang) * L)], fill=c, width=width)

    def save(self, name):
        self.img.save(f"{OUT}/wall_{name}.jpg", quality=84, optimize=True, progressive=True)
        self.img.filter(ImageFilter.GaussianBlur(48)).save(f"{OUT}/wall_{name}_blur.jpg", quality=72, optimize=True)
        self.img.resize((480, 270), Image.LANCZOS).save(f"{OUT}/wall_{name}_thumb.jpg", quality=84)


def cypress(p, x0, base_y, top_y, w, cols):
    rng = p.rng
    for _ in range(2600):
        t = rng.random()
        y = lerp(base_y, top_y, t)
        half = w * (1 - t) ** 0.7 * (0.55 + 0.45 * math.sin(t * 9))
        x = x0 + rng.uniform(-half, half) + 18 * math.sin(t * 7)
        col = hexrgb(rng.choice(cols)) + rng.uniform(-10, 10)
        a = -math.pi / 2 + 0.9 * math.sin(y / 40 + x / 30)
        p.stroke(x, y, col, rng.uniform(18, 34), rng.randint(7, 11), angle_fn=lambda cx, cy, a=a: a)


# ---------------------------------------------------------------- scenes
def starry():
    base = vgrad([(0, '#3E4FB0'), (300, '#6177DA'), (560, '#9AA9F0'), (640, '#B7B2F2'),
                  (700, '#5E72C8'), (860, '#4A57A8'), (1080, '#3B4590')])
    blend_blob(base, 820, 300, 420, '#8FB4F5', .55)
    blend_blob(base, 1300, 190, 260, '#A8E6E0', .35)
    ang = vortex_field([(780, 330, 260, 2.6), (1180, 250, 170, -2.2), (420, 420, 200, 1.6),
                        (1500, 470, 160, 1.4)], base_angle=0.0, wave=(0.5, 260))
    hills = YY > (640 + 50 * np.sin(XX / 180) + 30 * np.sin(XX / 63))
    ang = np.where(hills, 0.15 * np.sin(XX / 90 + YY / 50), ang)
    p = Painter(base, ang, 7)
    p.pass_(16, 42, 15, jitter=10)
    p.pass_(10, 30, 8, jitter=16, accents=['#A8E6E0', '#CFC6FF', '#7E93F2', '#FFF3A3'], accent_p=.12)
    p.pass_(11, 26, 7, region=hills, jitter=12, accents=['#7CC9A7', '#8FB8C8', '#6F7FC9'], accent_p=.3)
    for (x, y, r) in [(260, 130, 26), (540, 90, 30), (930, 160, 22), (1080, 70, 18), (720, 520, 22),
                      (1420, 120, 24), (360, 300, 18), (1700, 420, 20), (1240, 520, 16), (130, 470, 18)]:
        p.ring_strokes(x, y, r * 0.2, r * 2.6, ['#FFF7C2', '#FFDE03', '#FFE97A', '#BFD3FF'], width=7, dash=20)
        p.disc(x, y, r * 0.7, '#FFDE03', width=6)
    # crescent moon
    p.ring_strokes(1560, 150, 40, 150, ['#FFE97A', '#FFDE03', '#FFF3B0', '#F7C873'], width=9, dash=28)
    p.disc(1560, 150, 60, '#FFDE03', width=8)
    p.disc(1585, 135, 44, '#E9C23A', width=7)
    # village windows
    for _ in range(40):
        x = p.rng.uniform(700, 1400); y = p.rng.uniform(760, 840)
        p.d.rectangle([x, y, x + 9, y + 7], fill=(255, 222, 3))
    cypress(p, 1700, 1100, 90, 120, ['#2F6B6B', '#3F8A82', '#285858', '#56A79A', '#1F4F55'])
    p.save('starry')


def sunflower():
    base = vgrad([(0, '#7FD3E6'), (420, '#B9ECF2'), (600, '#DDF4EE'), (640, '#F6D978'), (820, '#F2C45A'),
                  (1080, '#E9A84E')])
    ang = vortex_field([(1400, 260, 300, 2.4), (500, 220, 220, -1.4), (900, 420, 160, 1.0)],
                       base_angle=0.05, wave=(0.4, 300))
    field = YY > (630 + 20 * np.sin(XX / 220))
    ang = np.where(field, -math.pi / 2 + 0.5 * np.sin(XX / 70 + YY / 90), ang)
    p = Painter(base, ang, 11)
    p.pass_(16, 42, 15, jitter=10)
    p.pass_(10, 28, 8, jitter=14, accents=['#FFFFFF', '#A8E6E0', '#FFF3B0'], accent_p=.15, region=~field)
    p.pass_(9, 30, 8, jitter=16, region=field,
            accents=['#FFDE03', '#9BD48A', '#F5B04C', '#FF9F9F', '#FFE97A'], accent_p=.32)
    p.ring_strokes(1400, 260, 30, 230, ['#FFDE03', '#FFE97A', '#FFF7C2', '#FFC07A', '#FFFFFF'], width=11, dash=34)
    p.disc(1400, 260, 70, '#FFDE03', width=9)
    # poppies
    for _ in range(90):
        x = p.rng.uniform(0, W); y = p.rng.uniform(720, 1080)
        p.disc(x, y, p.rng.uniform(6, 11), p.rng.choice(['#FF8F8F', '#FF7A9C']), width=5)
    # sunflowers bottom left
    for (x, y, r) in [(170, 930, 95), (390, 1010, 80), (90, 760, 60)]:
        for k in range(26):
            a = k / 26 * 2 * math.pi + p.rng.uniform(-.08, .08)
            for j in range(3):
                L = r * p.rng.uniform(.75, 1.05)
                c = tuple(int(v) for v in np.clip(hexrgb(p.rng.choice(['#FFDE03', '#FFC93C', '#FFE97A'])) +
                                                    p.rng.uniform(-8, 8), 0, 255))
                aa = a + (j - 1) * 0.05
                p.d.line([(x + math.cos(aa) * r * .4, y + math.sin(aa) * r * .4),
                          (x + math.cos(aa) * L, y + math.sin(aa) * L)], fill=c, width=10)
        p.disc(x, y, r * 0.42, '#8A5A3C', width=6)
        p.disc(x, y, r * 0.25, '#B5743E', width=5)
    p.save('sunflower')


def irises():
    base = vgrad([(0, '#B9B4EC'), (360, '#C9D4F2'), (520, '#BFE3D9'), (1080, '#9ED6B4')])
    ang = vortex_field([(600, 160, 260, 1.2), (1400, 220, 240, -1.2)], base_angle=0.35, wave=(0.2, 300))
    p = Painter(base, ang, 23)
    p.pass_(16, 42, 15, jitter=10)
    p.pass_(10, 28, 8, jitter=14, accents=['#E6E2FF', '#A9C4F5', '#FFFFFF'], accent_p=.14)
    rng = p.rng
    # rain
    for _ in range(700):
        x = rng.uniform(0, W); y = rng.uniform(0, 640)
        L = rng.uniform(30, 70)
        p.d.line([(x, y), (x - L * .35, y + L)], fill=(238, 242, 255), width=3)
    # orange soil
    for _ in range(900):
        x = rng.uniform(0, W); y = rng.uniform(980, 1090)
        c = hexrgb(rng.choice(['#F2A66B', '#F7BE84', '#E8925A']))
        p.stroke(x, y, c, 24, 9, angle_fn=lambda cx, cy: 0.1)
    # blades
    for _ in range(520):
        x = rng.uniform(-40, W + 40); y0 = rng.uniform(1000, 1100)
        h = rng.uniform(220, 520); bend = rng.uniform(-90, 90)
        col = tuple(int(v) for v in np.clip(hexrgb(rng.choice(['#5FB98E', '#8ED6A6', '#3E9C78', '#B2E5B8'])) +
                                           rng.uniform(-10, 10), 0, 255))
        pts = [(x + bend * (t / 6) ** 2, y0 - h * t / 6) for t in range(7)]
        p.d.line(pts, fill=col, width=rng.randint(9, 14), joint="curve")
    # blooms
    blooms = [(rng.uniform(40, W - 40), rng.uniform(560, 900)) for _ in range(38)]
    for i, (x, y) in enumerate(blooms):
        main = '#F4F1FF' if i == 7 else rng.choice(['#7B6CF0', '#8E7BFF', '#A58BFF', '#6C7CF2'])
        for k in range(7):
            a = -math.pi / 2 + (k - 3) * 0.5 + rng.uniform(-.15, .15)
            r = rng.uniform(34, 58)
            col = tuple(int(v) for v in np.clip(hexrgb(main) + rng.uniform(-14, 18), 0, 255))
            p.d.line([(x, y), (x + math.cos(a) * r, y + math.sin(a) * r)], fill=col, width=18)
            p.d.line([(x, y), (x + math.cos(a + math.pi) * r * .7, y + math.sin(a + math.pi) * r * .7 + 10)],
                     fill=col, width=16)
        p.disc(x, y, 9, '#FFDE03', width=5)
    p.save('irises')


def blossom():
    base = vgrad([(0, '#6FCBD3'), (540, '#A3E2E0'), (1080, '#7FD0D6')])
    blend_blob(base, 960, 540, 700, '#B8EBE6', .4)
    ang = vortex_field([(960, 540, 600, 0.6)], base_angle=0.6, wave=(0.6, 240))
    p = Painter(base, ang, 31)
    p.pass_(15, 40, 14, jitter=10)
    p.pass_(10, 26, 8, jitter=14, accents=['#C9F0EC', '#5FBAC6', '#E8FBF8'], accent_p=.18)
    rng = p.rng
    branch_cols = ['#4E5F5E', '#3E4C4C', '#6B7B6A', '#2F3D3E']

    def branch(x, y, a, length, width, depth):
        if depth == 0 or width < 3:
            return
        pts = [(x, y)]
        for _ in range(6):
            a += rng.uniform(-.28, .28)
            x += math.cos(a) * length / 6; y += math.sin(a) * length / 6
            pts.append((x, y))
        c = tuple(int(v) for v in hexrgb(rng.choice(branch_cols)))
        p.d.line(pts, fill=c, width=int(width), joint="curve")
        for (bx, by) in pts[2:]:
            if rng.random() < .45:
                flower(bx + rng.uniform(-18, 18), by + rng.uniform(-18, 18))
        for _ in range(2):
            branch(x, y, a + rng.uniform(-.8, .8), length * .7, width * .65, depth - 1)

    def flower(x, y):
        bud = rng.random() < .25
        for k in range(5 if not bud else 2):
            a = k / 5 * 2 * math.pi + rng.uniform(0, 1)
            r = 13 if not bud else 8
            col = (252, 250, 244) if not bud else (244, 166, 184)
            col = tuple(int(min(255, max(0, v + rng.uniform(-10, 6)))) for v in col)
            px, py = x + math.cos(a) * r * .8, y + math.sin(a) * r * .8
            p.d.ellipse([px - r, py - r * .8, px + r, py + r * .8], fill=col)
        if not bud:
            p.d.ellipse([x - 5, y - 5, x + 5, y + 5], fill=(247, 183, 200))
            p.d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(255, 222, 3))

    branch(-40, 1000, -0.9, 520, 34, 5)
    branch(1960, 820, -2.4, 560, 30, 5)
    branch(1300, -40, 1.9, 420, 22, 4)
    p.save('blossom')


def snow():
    base = vgrad([(0, '#C9C6F2'), (420, '#DCE6F8'), (600, '#E9E4F7'), (640, '#9FB4E3'), (700, '#EAF0FB'),
                  (1080, '#DDE6F7')])
    ang = vortex_field([(700, 260, 260, 1.8), (1250, 200, 200, -1.6)], base_angle=0.0, wave=(0.5, 280))
    ground = YY > (680 + 26 * np.sin(XX / 160))
    ang = np.where(ground, 0.2 * np.sin(XX / 110 + YY / 70), ang)
    p = Painter(base, ang, 41)
    p.pass_(16, 42, 15, jitter=8)
    p.pass_(10, 28, 8, jitter=12, accents=['#FFFFFF', '#B9C8F5', '#F2D9F2'], accent_p=.18)
    p.pass_(11, 30, 8, region=ground, jitter=10, accents=['#FFFFFF', '#C7D4F2', '#D9CCF2'], accent_p=.35)
    rng = p.rng
    p.ring_strokes(1350, 230, 20, 140, ['#FFF3B0', '#FFE97A', '#FFFFFF'], width=8, dash=26)
    p.disc(1350, 230, 40, '#FFE97A', width=7)
    for _ in range(26):
        x = rng.uniform(600, 1300); y = rng.uniform(700, 760)
        p.d.rectangle([x, y, x + 9, y + 8], fill=(255, 222, 3))
    cypress(p, 1720, 1100, 200, 100, ['#3F6E78', '#4E8A8C', '#335C66', '#6AA3A6'])
    for _ in range(900):
        x = rng.uniform(0, W); y = rng.uniform(0, H); r = rng.uniform(2, 5)
        p.d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255))
    p.save('snow')


for fn in sys.argv[2:] or ['starry', 'sunflower', 'irises', 'blossom', 'snow']:
    globals()[fn]()
    print('done', fn, flush=True)
