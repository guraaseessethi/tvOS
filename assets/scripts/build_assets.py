"""Build the Roku-ready Lumen asset pack: static TTFs, 9-patches, scrims, rings, marks."""
import os, shutil, math
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from PIL import Image, ImageDraw, ImageFilter

PK = 'pkg/lumen'
for d in ['fonts', 'images/wall', 'images/tiles', 'images/ui', 'images/brand', 'scripts', 'reference']:
    os.makedirs(f'{PK}/{d}', exist_ok=True)

# ---------- fonts: static instances (Roku has no variable-font axis control)
def inst(src, out, loc, name):
    f = TTFont(src)
    s = instantiateVariableFont(f, loc, inplace=False)
    s.flavor = None
    for rec in s['name'].names:
        if rec.nameID in (1, 4, 16):
            rec.string = name
        if rec.nameID == 6:
            rec.string = name.replace(' ', '-')
    s.save(f'{PK}/fonts/{out}')

inst('up/fraunces.woff2', 'Fraunces-Display.ttf', {'opsz': 144, 'wght': 420, 'SOFT': 100, 'WONK': 0}, 'Lumen Fraunces Display')
inst('up/fraunces.woff2', 'Fraunces-Title.ttf', {'opsz': 72, 'wght': 560, 'SOFT': 100, 'WONK': 0}, 'Lumen Fraunces Title')
inst('up/fraunces-italic.woff2', 'Fraunces-Italic.ttf', {'opsz': 36, 'wght': 500, 'SOFT': 100, 'WONK': 0}, 'Lumen Fraunces Italic')
for w, n in [(600, 'SemiBold'), (700, 'Bold'), (800, 'ExtraBold')]:
    inst('up/manrope.woff2', f'Manrope-{n}.ttf', {'wght': w}, f'Lumen Manrope {n}')

# ---------- wallpapers
for f in os.listdir('out'):
    shutil.copy(f'out/{f}', f'{PK}/images/wall/{f}')

SS = 4  # supersample


def rrect_mask(w, h, r):
    m = Image.new('L', (w * SS, h * SS), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * SS - 1, h * SS - 1], r * SS, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def ninepatch(img, sx, sy, cx=None, cy=None, name=''):
    """Wrap RGBA img in a 1px Android/Roku 9-patch border. sx/sy = (start,end) stretch, cx/cy = content."""
    w, h = img.size
    out = Image.new('RGBA', (w + 2, h + 2), (0, 0, 0, 0))
    out.paste(img, (1, 1))
    d = ImageDraw.Draw(out)
    K = (0, 0, 0, 255)
    d.line([(1 + sx[0], 0), (sx[1], 0)], fill=K)
    d.line([(0, 1 + sy[0]), (0, sy[1])], fill=K)
    cx = cx or (0, w - 1)
    cy = cy or (0, h - 1)
    d.line([(1 + cx[0], h + 1), (1 + cx[1], h + 1)], fill=K)
    d.line([(w + 1, 1 + cy[0]), (w + 1, 1 + cy[1])], fill=K)
    out.save(f'{PK}/images/ui/{name}.9.png')


def solid_round(w, h, r, rgba=(255, 255, 255, 255)):
    im = Image.new('RGBA', (w, h), rgba)
    im.putalpha(Image.eval(rrect_mask(w, h, r), lambda a: a * rgba[3] // 255))
    return im


# white shapes, tinted at runtime with blendColor (frost colour per painting)
ninepatch(solid_round(64, 64, 32), (31, 32), (31, 32), name='pill_64')              # pills + round buttons
ninepatch(solid_round(88, 88, 44), (43, 44), (43, 44), name='pill_88')              # Zeus suggestion strip
ninepatch(solid_round(96, 96, 44), (44, 51), (44, 51), (28, 67), (28, 67), name='sheet_r44')  # Zeus sheet
ninepatch(solid_round(64, 64, 28), (28, 35), (28, 35), (22, 41), (18, 45), name='bubble_r28')
ninepatch(solid_round(72, 72, 28), (28, 43), (28, 43), name='card_r28')
# hairline inner stroke for frosted pills (white 14%) + top highlight
st = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
dd = ImageDraw.Draw(st)
big = Image.new('RGBA', (64 * SS, 64 * SS), (0, 0, 0, 0))
ImageDraw.Draw(big).rounded_rectangle([0, 0, 64 * SS - 1, 64 * SS - 1], 32 * SS, outline=(255, 255, 255, 40), width=SS)
ImageDraw.Draw(big).arc([SS, SS, 64 * SS - SS, 64 * SS - SS], 200, 340, fill=(255, 255, 255, 95), width=SS)
ninepatch(big.resize((64, 64), Image.LANCZOS), (31, 32), (31, 32), name='pill_64_stroke')

# focus ring for tiles: 5px yellow + 2px ink outside, around a 288x192 r34 tile
TW, TH, TR = 288, 192, 34
pad = 8
rw, rh = TW + pad * 2, TH + pad * 2
big = Image.new('RGBA', (rw * SS, rh * SS), (0, 0, 0, 0))
g = ImageDraw.Draw(big)
g.rounded_rectangle([0, 0, rw * SS - 1, rh * SS - 1], (TR + pad) * SS, fill=(28, 28, 28, 230))
g.rounded_rectangle([2 * SS, 2 * SS, (rw - 2) * SS - 1, (rh - 2) * SS - 1], (TR + pad - 2) * SS, fill=(255, 222, 3, 255))
g.rounded_rectangle([pad * SS, pad * SS, (rw - pad) * SS - 1, (rh - pad) * SS - 1], TR * SS, fill=(0, 0, 0, 0))
big.resize((rw, rh), Image.LANCZOS).save(f'{PK}/images/ui/focus_ring_tile.png')
# focus glow (yellow, soft)
gw, gh = TW + 240, TH + 240
glow = Image.new('RGBA', (gw, gh), (0, 0, 0, 0))
ImageDraw.Draw(glow).rounded_rectangle([120, 120, 120 + TW, 120 + TH], TR, fill=(255, 222, 3, 120))
glow.filter(ImageFilter.GaussianBlur(40)).save(f'{PK}/images/ui/focus_glow_tile.png')
# tile drop shadow
sh = Image.new('RGBA', (TW + 120, TH + 140), (0, 0, 0, 0))
ImageDraw.Draw(sh).rounded_rectangle([60, 78, 60 + TW, 78 + TH - 16], TR, fill=(0, 0, 0, 115))
sh.filter(ImageFilter.GaussianBlur(20)).save(f'{PK}/images/ui/tile_shadow.png')

# scrims (white + alpha; tint with blendColor = painting deep colour)
W, H = 1920, 1080
col = Image.new('L', (1, H))
for y in range(H):
    t = y / H
    a = 0 if t < .40 else (.42 * (t - .40) / .20 if t < .60 else .42 + (.78 - .42) * (t - .60) / .40)
    col.putpixel((0, y), int(255 * a))
a = col.resize((W, H))
im = Image.new('RGBA', (W, H), (255, 255, 255, 255)); im.putalpha(a)
im.resize((960, 540), Image.LANCZOS).save(f'{PK}/images/ui/scrim_bottom.png')  # draw at 1920x1080
rad = Image.new('L', (W, H), 0)
px = rad.load()
for y in range(0, H, 2):
    for x in range(0, W, 2):
        d = math.hypot(x / 1200, y / 700)
        v = int(255 * .7 * max(0, 1 - d / .7) ** 1.2)
        px[x, y] = v;
rad = rad.resize((W // 2, H // 2), Image.NEAREST).filter(ImageFilter.GaussianBlur(6))
im = Image.new('RGBA', rad.size, (255, 255, 255, 255)); im.putalpha(rad)
im.save(f'{PK}/images/ui/scrim_topleft.png')  # draw at 1920x1080
# bottom blur mask: alpha ramp 46% -> 64% for the blurred wallpaper copy
col = Image.new('L', (1, H))
for y in range(H):
    t = y / H
    col.putpixel((0, y), int(255 * min(1, max(0, (t - .46) / .18))))
m = col.resize((8, H))
im = Image.new('RGBA', (8, H), (255, 255, 255, 255)); im.putalpha(m)
im.save(f'{PK}/images/ui/blur_mask.png')
print('assets ok')
