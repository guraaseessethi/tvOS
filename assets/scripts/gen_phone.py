"""Generates the Lumen phone app (Android) screens as static markup.
Writes local previews (preview/phone_<id>.html) and canvas artboards (Phone-<Id>.dc.html)."""
import os, sys

CANVAS = sys.argv[1]
LOCAL = {
    'fr': '../up/fraunces.woff2', 'fri': '../up/fraunces-italic.woff2', 'mn': '../up/manrope.woff2',
    'starry': '../out/wall_starry.jpg', 'starry_blur': '../out/wall_starry_blur.jpg',
    't_starry': '../out/wall_starry_thumb.jpg', 't_sunflower': '../out/wall_sunflower_thumb.jpg',
    't_irises': '../out/wall_irises_thumb.jpg', 't_blossom': '../out/wall_blossom_thumb.jpg', 't_snow': '../out/wall_snow_thumb.jpg',
    **{f'tex_{k}': f'../tiles/tile_{k}.jpg' for k in ['rose', 'sky', 'mint', 'wheat', 'lilac', 'peach', 'aqua', 'cream']},
}
BLOB = {
    'fr': '/_blob/a9252d60d3c2e9bbb30de35805e446de', 'fri': '/_blob/dd8eb16197c47aefad5a294d92e65e08', 'mn': '/_blob/a5e5e381688e3c3f9e09a9a14439ac7f',
    'starry': '/_blob/349e92f729f8ca8fa244a07ca78f768f', 'starry_blur': '/_blob/8d200135142d7abd21e3adfa2965b94e',
    't_starry': '/_blob/6c91e16408e663c910fad58605020403', 't_sunflower': '/_blob/87404fdcc874a1284e7a5b3921668d00',
    't_irises': '/_blob/20a4328ab0aa091dd33544a5af1536c0', 't_blossom': '/_blob/c1da8e7aac040a4e4374e3e335876769', 't_snow': '/_blob/5aae94b1e290e96048168e2b96797d7b',
    'tex_rose': '/_blob/df5765f90437a1c0452efad2d85b7b2a', 'tex_sky': '/_blob/f73a0df923610c8efe6ef013d97f506f', 'tex_mint': '/_blob/26920f748561f7c3b081f49cce758b45',
    'tex_wheat': '/_blob/9e8afe29be318114d2fcc3357275ad1a', 'tex_lilac': '/_blob/b4da76cfbcbf7de184fe866e0831921f', 'tex_peach': '/_blob/28f7d854080ae156ed3533a2dd987de4',
    'tex_aqua': '/_blob/2317ec4dded3f7c33faa2772dbeccfd2', 'tex_cream': '/_blob/bd621778dea409e6b8e0a86a5e220c8d',
}
W, H = 412, 915
K, Y, P = '#1C1C1C', '#FFDE03', '#FBF7EC'
DEEP = '20,24,74'


def css(u):
    return f"""@font-face{{font-family:LFraunces;src:url({u['fr']}) format('woff2');font-weight:100 900;font-style:normal}}
@font-face{{font-family:LFraunces;src:url({u['fri']}) format('woff2');font-weight:100 900;font-style:italic}}
@font-face{{font-family:LManrope;src:url({u['mn']}) format('woff2');font-weight:200 800}}
body{{margin:0}}
.ph{{position:relative;width:{W}px;height:{H}px;overflow:hidden;font-family:LManrope,Manrope,system-ui,sans-serif;color:#fff;background:rgb({DEEP});-webkit-font-smoothing:antialiased}}
.fr{{font-family:LFraunces,Georgia,serif;font-variation-settings:"SOFT" 100,"WONK" 0}}
.eb{{font-size:12px;font-weight:800;letter-spacing:.16em;text-transform:uppercase}}
.frost{{background:rgba(255,255,255,.12);-webkit-backdrop-filter:blur(20px);backdrop-filter:blur(20px);box-shadow:inset 0 1px 0 rgba(255,255,255,.3),inset 0 0 0 1px rgba(255,255,255,.12)}}
.btn{{all:unset;box-sizing:border-box;cursor:pointer;display:flex;align-items:center;justify-content:center;font-family:LManrope,Manrope,system-ui,sans-serif}}
.rb{{width:56px;height:56px;border-radius:28px;color:#fff}}
.ui{{width:24px;height:24px;fill:none;stroke:currentColor;stroke-width:2.4;stroke-linecap:round;stroke-linejoin:round}}
.lbl{{font-size:12px;font-weight:700;opacity:.8;text-align:center;margin-top:6px}}
.tab{{display:flex;flex-direction:column;align-items:center;gap:4px;font-size:12px;font-weight:800;color:rgba(255,255,255,.7)}}
.tab.on{{color:{Y}}}
.t{{position:relative;width:100%;aspect-ratio:3/2;border-radius:18px;overflow:hidden;box-shadow:0 10px 24px -10px rgba(0,0,0,.5),inset 0 0 0 1px rgba(255,255,255,.4)}}
.t img{{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}}
.t .w{{position:absolute;inset:0;background:linear-gradient(160deg,rgba(255,255,255,.38),rgba(255,255,255,.12))}}
.t svg{{position:absolute;left:50%;top:50%;width:44px;height:44px;transform:translate(-50%,-50%)}}
.row{{display:flex;align-items:center}}
.sw{{width:48px;height:28px;border-radius:14px;position:relative;flex:none}}
.sw i{{position:absolute;top:3px;width:22px;height:22px;border-radius:11px;background:#fff}}
"""


def MARK(s, fill=Y, ink=K, ring=True):
    r = f'<circle cx="32" cy="32" r="17" fill="none" stroke="{ink}" stroke-width="4" stroke-dasharray="6 6.4" stroke-linecap="round" transform="rotate(-12 32 32)"/>' if ring else ''
    return f'<svg viewBox="0 0 64 64" width="{s}" height="{s}" aria-hidden="true"><circle cx="32" cy="32" r="30" fill="{fill}"/>{r}<circle cx="32" cy="32" r="7" fill="{ink}"/></svg>'


def ic(d, sz=24, color='currentColor', sw=2.4):
    return f'<svg viewBox="0 0 24 24" width="{sz}" height="{sz}" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="{d}"/></svg>'


I = {
    'power': 'M12 3v8M7 6.5a7 7 0 1 0 10 0',
    'back': 'M9 7L4 12l5 5M4 12h11a5 5 0 0 1 0 10h-2',
    'home': 'M4 11l8-7 8 7v9H4zM10 20v-5h4v5',
    'star': 'M12 4v16M4.5 8l15 8M4.5 16l15-8',
    'rew': 'M11 6L4 12l7 6zM20 6l-7 6 7 6z',
    'ff': 'M13 6l7 6-7 6zM4 6l7 6-7 6z',
    'play': 'M7 5v14l11-7zM20 5v14',
    'mute': 'M4 9h4l5-4v14l-5-4H4zM17 9l4 6M21 9l-4 6',
    'kbd': 'M3 6h18v12H3zM7 10h.01M11 10h.01M15 10h.01M7 14h10',
    'remote': 'M8 2h8v20H8zM12 7v.01M10 12h4M10 16h4',
    'grid': 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
    'gear': 'M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2',
    'up': 'M6 15l6-6 6 6', 'down': 'M6 9l6 6 6-6', 'left': 'M15 6l-6 6 6 6', 'right': 'M9 6l6 6-6 6',
    'check': 'M5 12l5 5 9-10', 'hdmi': 'M3 8h18v4l-3 4H6l-3-4zM7 11h10', 'tv': 'M3 7h18v12H3zM8 3l4 4 4-4',
    'mic': 'M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3M5 11a7 7 0 0 0 14 0M12 18v3',
    'wifi': 'M3 9a13 13 0 0 1 18 0M6.5 12.5a8 8 0 0 1 11 0M10 16a3 3 0 0 1 4 0M12 19.5v.01',
}
GLY = {  # 64-grid tile glyphs
    'balloon': f'<g fill="none" stroke="{K}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><path d="M32 10c-9 0-15 7-15 15 0 10 9 17 15 19 6-2 15-9 15-19 0-8-6-15-15-15z" fill="{Y}"/><path d="M32 44l-2.5 3h5zM32 47c-3 4 3 7 0 11" stroke-width="3.5"/></g>',
    'mic': f'<g fill="none" stroke="{K}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><rect x="23" y="8" width="18" height="30" rx="9" fill="{Y}"/><path d="M15 30a17 17 0 0 0 34 0M32 47v9M24 56h16"/></g>',
    'play': f'<path d="M24 17.5c0-2.6 2.9-4.2 5.1-2.8l17.4 11.7c2 1.3 2 4.2 0 5.5L29.1 43.6c-2.2 1.4-5.1-.2-5.1-2.8z" fill="{Y}" stroke="{K}" stroke-width="4" stroke-linejoin="round"/>',
    'tv': f'<g fill="none" stroke="{K}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><rect x="10" y="14" width="44" height="30" rx="7"/><path d="M24 52h16M32 44v8"/><path d="M32 21.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z" fill="{Y}" stroke-width="3"/></g>',
    'music': f'<g fill="none" stroke="{K}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><path d="M26 44V16l22-5v28M26 22l22-5"/><ellipse cx="20" cy="44" rx="7" ry="6" fill="{Y}"/><ellipse cx="42" cy="39" rx="7" ry="6" fill="{Y}"/></g>',
    'camera': f'<g fill="none" stroke="{K}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><rect x="8" y="20" width="36" height="28" rx="7"/><path d="M44 30l12-7v22l-12-7"/><circle cx="26" cy="34" r="7" fill="{Y}"/></g>',
    'ball': f'<g fill="none" stroke="{K}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><circle cx="32" cy="32" r="20" fill="{Y}"/><path d="M32 12c-6 6-6 34 0 40M12.5 28c8 4 31 4 39 0M14 42c8-3 28-3 36 0" stroke-width="3.5"/></g>',
    'photo': f'<g fill="none" stroke="{K}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="13" width="46" height="38" rx="8"/><path d="M11 45l13-14 10 10 6-6 13 12"/><circle cx="41" cy="24" r="5" fill="{Y}" stroke-width="3.5"/></g>',
}


def tabs(on):
    def t(k, label, d):
        return f'<div class="tab{" on" if k == on else ""}">{ic(d, 24)}{label}</div>'
    return f'''<nav class="frost" style="position:absolute;left:16px;right:16px;bottom:16px;height:68px;border-radius:34px;background:rgba(10,12,40,.55);display:flex;justify-content:space-around;align-items:center">
  {t("remote", "Remote", I["remote"])}{t("apps", "Apps", I["grid"])}{t("zeus", "Zeus", I["mic"])}{t("setup", "Setup", I["gear"])}</nav>'''


def backdrop(u, h=330):
    return f'''<img src="{u['starry_blur']}" alt="" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:.55">
<img src="{u['starry']}" alt="" style="position:absolute;left:-60%;top:0;width:220%;height:{h}px;object-fit:cover;object-position:30% 20%;-webkit-mask-image:linear-gradient(180deg,#000 35%,transparent);mask-image:linear-gradient(180deg,#000 35%,transparent)">
<div style="position:absolute;inset:0;background:linear-gradient(180deg,rgba({DEEP},.35) 0%,rgba({DEEP},.2) 20%,rgba({DEEP},.7) 45%,rgba({DEEP},.92) 100%)"></div>'''


def header(u, title='Living Room TV', sub='On · Lumen open'):
    return f'''<div style="position:absolute;left:20px;right:20px;top:28px;display:flex;align-items:center;gap:12px">
  <div style="flex:1"><div class="eb" style="opacity:.85">Roku TV</div>
    <div class="fr" style="font-size:28px;font-weight:560;line-height:1.1;margin-top:2px">{title}</div>
    <div style="display:flex;align-items:center;gap:6px;font-size:13px;font-weight:700;margin-top:6px;opacity:.9"><span style="width:8px;height:8px;border-radius:4px;background:#7EE2A8"></span>{sub}</div></div>
  <button class="btn rb frost" aria-label="TV power" style="width:52px;height:52px;border-radius:26px;background:rgba(255,255,255,.14)">{ic(I["power"])}</button>
</div>'''


def remote(u):
    dp = lambda d, style: f'<button class="btn" aria-label="{d}" style="position:absolute;{style};width:72px;height:72px;color:{K}">{ic(I[d], 30, K, 3)}</button>'
    small = lambda k, label: f'<div style="display:flex;flex-direction:column;align-items:center"><button class="btn rb frost" aria-label="{label}">{ic(I[k])}</button><div class="lbl">{label}</div></div>'
    return f'''{backdrop(u)}{header(u)}
<div class="fr" style="position:absolute;left:20px;top:132px;font-size:15px;font-style:italic;font-weight:500;opacity:.9">Starry Night · 8° clear in Barrie</div>
<div style="position:absolute;left:50%;top:176px;margin-left:-140px;width:280px;height:280px;border-radius:140px;background:{P};box-shadow:0 24px 50px -16px rgba(0,0,0,.6),inset 0 2px 0 #fff">
  {dp("up", "left:104px;top:4px")}{dp("down", "left:104px;bottom:4px")}{dp("left", "left:4px;top:104px")}{dp("right", "right:4px;top:104px")}
  <button class="btn" aria-label="OK" style="position:absolute;left:84px;top:84px;width:112px;height:112px;border-radius:56px;background:{Y};box-shadow:0 0 0 3px {K},0 8px 20px -6px rgba(0,0,0,.4)">
    <svg viewBox="0 0 112 112" width="112" height="112" style="position:absolute;inset:0" aria-hidden="true"><circle cx="56" cy="56" r="40" fill="none" stroke="{K}" stroke-width="3" stroke-dasharray="7 8" stroke-linecap="round"/></svg>
    <span style="position:relative;font-size:20px;font-weight:800;color:{K};letter-spacing:.04em">OK</span></button>
</div>
<div style="position:absolute;left:28px;right:28px;top:482px;display:flex;justify-content:space-between">
  {small("back", "Back")}{small("home", "Home")}{small("star", "Options")}{small("kbd", "Type")}
</div>
<div style="position:absolute;left:28px;right:28px;top:578px;display:flex;align-items:center;gap:12px">
  <div class="frost" style="flex:1;height:56px;border-radius:28px;display:flex;align-items:center;justify-content:space-between;padding:0 6px">
    <button class="btn rb" aria-label="Rewind" style="width:48px;height:48px">{ic(I["rew"])}</button>
    <button class="btn" aria-label="Play or pause" style="width:56px;height:44px;border-radius:22px;background:#fff;color:{K}">{ic(I["play"], 22, K)}</button>
    <button class="btn rb" aria-label="Fast forward" style="width:48px;height:48px">{ic(I["ff"])}</button></div>
  <div class="frost" style="width:132px;height:56px;border-radius:28px;display:flex;align-items:center;justify-content:space-between;padding:0 6px;font-size:12px;font-weight:800">
    <button class="btn rb" aria-label="Volume down" style="width:44px;height:44px;font-size:24px;font-weight:600">−</button>VOL<button class="btn rb" aria-label="Volume up" style="width:44px;height:44px;font-size:24px;font-weight:600">+</button></div>
  <button class="btn rb frost" aria-label="Mute">{ic(I["mute"])}</button>
</div>
<button class="btn" style="position:absolute;left:20px;right:20px;top:662px;height:84px;border-radius:42px;background:{Y};color:{K};justify-content:flex-start;gap:14px;padding:0 10px;box-shadow:0 0 0 2px {K},0 16px 30px -10px rgba(0,0,0,.5)">
  {MARK(64, K, Y)}<div><div style="font-size:19px;font-weight:800">Hold to talk to Zeus</div><div style="font-size:13px;font-weight:700;opacity:.7;margin-top:2px">or say "Hey Zeus"</div></div>
  <span style="margin-left:auto;width:52px;height:52px;border-radius:26px;background:{K};color:{Y};display:flex;align-items:center;justify-content:center">{ic(I["mic"], 24, Y)}</span></button>
<div style="position:absolute;left:0;right:0;top:760px;text-align:center;font-size:12px;font-weight:700;opacity:.65">Swipe the pad to move · tap for OK</div>
{tabs("remote")}'''


def zeus(u):
    rings = ''.join(f'<circle cx="100" cy="100" r="{r}" fill="none" stroke="{Y}" stroke-width="{w}" stroke-dasharray="{d}" stroke-linecap="round" opacity="{o}"/>'
                    for r, w, d, o in [(62, 5, '9 9', .95), (78, 4, '7 10', .6), (94, 3, '5 11', .3)])
    return f'''<div style="position:absolute;inset:0;background:{P}"></div>
<div style="position:absolute;left:0;right:0;top:0;height:250px;overflow:hidden">
  <img src="{u['starry']}" alt="" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover;object-position:40% 30%">
  <div style="position:absolute;inset:0;background:linear-gradient(180deg,rgba({DEEP},.25),rgba({DEEP},.85))"></div>
  <div style="position:absolute;left:20px;right:20px;top:28px;display:flex;align-items:center;justify-content:space-between">
    <div class="eb">Talking to Living Room TV</div>
    <span style="height:30px;border-radius:15px;padding:0 12px;display:flex;align-items:center;gap:6px;font-size:12px;font-weight:800;background:rgba(255,255,255,.18)"><span style="width:8px;height:8px;border-radius:4px;background:#7EE2A8"></span>Connected</span></div>
  <div style="position:absolute;left:0;right:0;top:56px;display:flex;justify-content:center">
    <div style="position:relative;width:200px;height:200px"><svg viewBox="0 0 200 200" width="200" height="200" style="position:absolute;inset:0" aria-hidden="true">{rings}</svg>
      <div style="position:absolute;left:52px;top:52px">{MARK(96)}</div></div></div>
</div>
<div style="position:absolute;left:20px;right:20px;top:270px;color:{K}">
  <div class="eb" style="color:rgba(28,28,28,.55)">Listening</div>
  <div class="fr" style="font-size:30px;font-weight:520;line-height:1.2;margin-top:8px">“Open Music on the TV and turn it off in half an hour<span style="color:rgba(28,28,28,.35)">…</span>”</div>
</div>
<div style="position:absolute;left:20px;right:20px;top:420px;display:flex;flex-direction:column;gap:10px;color:{K}">
  <div style="display:flex;gap:10px;align-items:flex-end">{MARK(32)}
    <div style="background:#fff;border-radius:22px 22px 22px 6px;padding:14px 16px;font-size:16px;font-weight:600;line-height:1.4;box-shadow:0 1px 0 rgba(28,28,28,.06);max-width:300px">Opening Music on the TV. It'll switch off at 10:52.</div></div>
  <div style="display:flex;flex-wrap:wrap;gap:8px;margin-left:42px">
    <span style="height:34px;border-radius:17px;padding:0 12px;display:flex;align-items:center;gap:6px;background:#fff;font-size:13px;font-weight:800;box-shadow:inset 0 0 0 1.5px rgba(28,28,28,.12)">{ic(I["check"], 16, "#1F9D5B", 3)}Music opened on TV</span>
    <span style="height:34px;border-radius:17px;padding:0 12px;display:flex;align-items:center;gap:6px;background:#fff;font-size:13px;font-weight:800;box-shadow:inset 0 0 0 1.5px rgba(28,28,28,.12)">{ic(I["check"], 16, "#1F9D5B", 3)}Sleep timer 10:52</span></div>
</div>
<div style="position:absolute;left:20px;right:20px;top:612px;color:{K}">
  <div class="eb" style="color:rgba(28,28,28,.55);margin-bottom:10px">Try</div>
  <div style="display:flex;flex-wrap:wrap;gap:8px">
    {''.join(f'<span style="height:40px;border-radius:20px;padding:0 14px;display:flex;align-items:center;font-size:14px;font-weight:700;border:2px solid {K}">{c}</span>' for c in ["Paint me something sunny", "Switch to PlayStation", "What should I watch?"])}
  </div>
</div>
<button class="btn" style="position:absolute;left:20px;right:20px;top:746px;height:64px;border-radius:32px;background:{K};color:{Y};gap:12px;font-size:17px;font-weight:800">
  <span style="display:flex;gap:4px;align-items:center">{''.join(f'<i style="display:block;width:4px;height:{h}px;border-radius:2px;background:{Y}"></i>' for h in [10, 22, 32, 18, 28, 12, 24, 16, 8])}</span>Release to send</button>
<nav style="position:absolute;left:16px;right:16px;bottom:16px;height:68px;border-radius:34px;background:rgba(28,28,28,.06);display:flex;justify-content:space-around;align-items:center;color:{K}">
  {''.join(f'<div class="tab" style="color:{Y if k == "zeus" else "rgba(28,28,28,.55)"}">{"<span style=\"display:flex;width:36px;height:24px;border-radius:12px;background:" + K + ";align-items:center;justify-content:center\">" + ic(d, 18, Y) + "</span>" if k == "zeus" else ic(d)}<span style="color:{K if k == "zeus" else "rgba(28,28,28,.55)"}">{l}</span></div>' for k, l, d in [("remote", "Remote", I["remote"]), ("apps", "Apps", I["grid"]), ("zeus", "Zeus", I["mic"]), ("setup", "Setup", I["gear"])])}</nav>'''


def apps(u):
    A = [('Streaming 1', 'rose', 'play'), ('Streaming 2', 'sky', 'tv'), ('Music', 'mint', 'music'), ('Video', 'wheat', 'camera'), ('Sports', 'lilac', 'ball'), ('Photos', 'peach', 'photo'), ('Kids', 'aqua', 'balloon'), ('Podcasts', 'cream', 'mic')]
    grid = ''.join(f'<div><div class="t"><img src="{u["tex_" + c]}" alt=""><span class="w"></span><svg viewBox="0 0 64 64">{GLY[g]}</svg></div><div style="font-size:13px;font-weight:800;margin-top:7px">{n}</div></div>' for n, c, g in A) + f'<div><div style="width:100%;aspect-ratio:3/2;border-radius:18px;border:2px dashed rgba(255,255,255,.55);box-sizing:border-box;display:flex;align-items:center;justify-content:center;font-size:30px;font-weight:300">+</div><div style="font-size:13px;font-weight:800;margin-top:7px">Add app</div></div>'
    pills = ''.join(f'<span class="frost" style="height:44px;border-radius:22px;padding:0 14px;display:flex;align-items:center;gap:8px;font-size:14px;font-weight:700">{ic(I[i], 18)}{n}</span>' for n, i in [('PlayStation', 'hdmi'), ('Apple TV', 'hdmi'), ('Live TV', 'tv')])
    paint = ''.join(f'<div style="flex:none;width:96px"><div style="width:96px;height:56px;border-radius:14px;overflow:hidden;{"box-shadow:0 0 0 3px " + Y + ",0 0 0 5px " + K if k == "starry" else "box-shadow:inset 0 0 0 1px rgba(255,255,255,.3)"}"><img src="{u["t_" + k]}" alt="" style="width:100%;height:100%;object-fit:cover;display:block"></div><div class="fr" style="font-size:12px;font-style:italic;margin-top:6px;opacity:.9">{n}</div></div>'
                    for k, n in [('starry', 'Starry Night'), ('sunflower', 'Sunflowers'), ('irises', 'Irises'), ('blossom', 'Blossom'), ('snow', 'Snowy Evening')])
    return f'''{backdrop(u, 260)}
<div style="position:absolute;left:20px;right:20px;top:28px"><div class="eb" style="opacity:.85">Living Room TV</div><div class="fr" style="font-size:34px;font-weight:560;margin-top:4px">Apps &amp; inputs</div>
  <div style="font-size:13px;font-weight:700;opacity:.8;margin-top:4px">Tap to open on the TV</div></div>
<div style="position:absolute;left:20px;right:20px;top:132px;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px 12px">{grid}</div>
<div style="position:absolute;left:20px;right:20px;top:484px"><div class="eb" style="opacity:.7;margin-bottom:10px">Inputs</div><div style="display:flex;gap:8px;flex-wrap:wrap">{pills}</div></div>
<div style="position:absolute;left:20px;right:0;top:590px"><div class="eb" style="opacity:.7;margin-bottom:10px">Repaint the TV</div><div style="display:flex;gap:10px;overflow:hidden">{paint}</div>
  <div style="font-size:12px;font-weight:700;opacity:.65;margin-top:10px">Following the weather · tap a painting to override until sunset</div></div>
{tabs("apps")}'''


def setup(u):
    def row(title, sub, on):
        tr = f'<span class="sw" style="background:{Y if on else "rgba(28,28,28,.18)"}"><i style="{"right:3px;box-shadow:0 0 0 1.5px " + K if on else "left:3px"}"></i></span>'
        return f'<div style="display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid rgba(28,28,28,.08)"><div style="flex:1"><div style="font-size:15px;font-weight:800">{title}</div><div style="font-size:13px;font-weight:600;color:rgba(28,28,28,.6);margin-top:2px;line-height:1.35">{sub}</div></div>{tr}</div>'
    def chk(t, ok=True):
        return f'<div style="display:flex;align-items:center;gap:10px;font-size:13px;font-weight:700;padding:5px 0"><span style="width:22px;height:22px;border-radius:11px;background:{"#1F9D5B" if ok else "#E8A23A"};display:flex;align-items:center;justify-content:center">{ic(I["check"] if ok else "M12 7v6M12 17v.01", 14, "#fff", 3)}</span>{t}</div>'
    return f'''{backdrop(u, 240)}
<div style="position:absolute;left:20px;right:20px;top:28px"><div class="eb" style="opacity:.85">Lumen for Roku</div><div class="fr" style="font-size:34px;font-weight:560;margin-top:4px">Setup</div></div>
<div style="position:absolute;left:12px;right:12px;top:112px;bottom:96px;border-radius:30px;background:{P};color:{K};padding:20px;box-sizing:border-box;overflow:hidden">
  <div style="display:flex;align-items:center;gap:12px;padding:12px;border-radius:20px;background:#fff;box-shadow:inset 0 0 0 1px rgba(28,28,28,.06)">
    <span style="width:44px;height:44px;border-radius:14px;background:rgb({DEEP});color:#fff;display:flex;align-items:center;justify-content:center">{ic(I["tv"], 22, "#fff")}</span>
    <div style="flex:1"><div style="font-size:15px;font-weight:800">Living Room Roku TV</div><div style="font-size:12px;font-weight:600;color:rgba(28,28,28,.6);margin-top:2px">192.168.1.42 · found on your Wi-Fi</div></div>
    <span style="font-size:12px;font-weight:800;color:#1F9D5B">Connected</span></div>
  {row("Open Lumen when the TV turns on", "Watches for the TV waking up on home Wi-Fi and opens Lumen in about 2 seconds.", True)}
  {row("Hey Zeus", "Listen for the wake word while the remote is open.", False)}
  {row("Remote on lock screen", "Keeps a small remote and Zeus button in your notifications.", True)}
  <div class="eb" style="color:rgba(28,28,28,.55);margin:16px 0 6px">Checks</div>
  {chk("Roku: Control by mobile apps is on")}
  {chk("Roku: Fast TV start is on")}
  {chk("Lumen is installed on the TV")}
  {chk("Battery: allow background use", False)}
  <div style="margin-top:12px;padding:12px 14px;border-radius:16px;background:{Y};font-size:13px;font-weight:700;line-height:1.4">Last auto-open: today at 7:02 pm, 1.8 s after the TV turned on.</div>
</div>
{tabs("setup")}'''


SCREENS = [('remote', 'Remote', remote), ('zeus', 'Zeus', zeus), ('apps', 'Apps', apps), ('setup', 'Setup', setup)]
os.makedirs('preview', exist_ok=True)
for sid, name, fn in SCREENS:
    body = fn(LOCAL)
    open(f'preview/phone_{sid}.html', 'w').write(f'<!doctype html><html lang="en"><head><meta charset="utf-8"><style>{css(LOCAL)}</style></head><body><div class="ph">{body}</div></body></html>')
    dc = f'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Lumen phone app, {name}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<style>
{css(BLOB)}
</style>
</helmet>
<div class="ph">
{fn(BLOB)}
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{W},"height":{H}}}}}'>
class Component extends DCLogic {{
  renderVals() {{ return {{}}; }}
}}
</script>
</body>
</html>
'''
    open(f'{CANVAS}/Phone-{name}.dc.html', 'w').write(dc)
print('ok')
