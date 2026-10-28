// Renders icon tiles, UI glyphs, Zeus/Lumen marks, channel posters and splash to PNG.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const ROOT = __dirname;
const PK = path.join(ROOT, 'pkg/lumen/images');
const font = (f) => 'file://' + path.join(ROOT, 'up', f);
const css = `@font-face{font-family:F;src:url(${font('fraunces.woff2')});font-weight:100 900}
@font-face{font-family:M;src:url(${font('manrope.woff2')});font-weight:200 800}
html,body{margin:0;background:transparent}`;
const K = '#1C1C1C', Y = '#FFDE03';
const S = `fill="none" stroke="${K}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"`;
const ICON = {
  play: `<path d="M24 17.5c0-2.6 2.9-4.2 5.1-2.8l17.4 11.7c2 1.3 2 4.2 0 5.5L29.1 43.6c-2.2 1.4-5.1-.2-5.1-2.8z" fill="${Y}" stroke="${K}" stroke-width="4" stroke-linejoin="round"/>`,
  tv: `<g ${S}><rect x="10" y="14" width="44" height="30" rx="7"/><path d="M24 52h16M32 44v8"/><path d="M32 21.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z" fill="${Y}" stroke-width="3"/></g>`,
  music: `<g ${S}><path d="M26 44V16l22-5v28M26 22l22-5"/><ellipse cx="20" cy="44" rx="7" ry="6" fill="${Y}"/><ellipse cx="42" cy="39" rx="7" ry="6" fill="${Y}"/></g>`,
  camera: `<g ${S}><rect x="8" y="20" width="36" height="28" rx="7"/><path d="M44 30l12-7v22l-12-7"/><circle cx="26" cy="34" r="7" fill="${Y}"/></g>`,
  ball: `<g ${S}><circle cx="32" cy="32" r="20" fill="${Y}"/><path d="M32 12c-6 6-6 34 0 40M12.5 28c8 4 31 4 39 0M14 42c8-3 28-3 36 0" stroke-width="3.5"/></g>`,
  photo: `<g ${S}><rect x="9" y="13" width="46" height="38" rx="8"/><path d="M11 45l13-14 10 10 6-6 13 12"/><circle cx="41" cy="24" r="5" fill="${Y}" stroke-width="3.5"/></g>`,
  balloon: `<g ${S}><path d="M32 10c-9 0-15 7-15 15 0 10 9 17 15 19 6-2 15-9 15-19 0-8-6-15-15-15z" fill="${Y}"/><path d="M32 44l-2.5 3h5zM32 47c-3 4 3 7 0 11" stroke-width="3.5"/><path d="M24.5 20a8 8 0 0 1 5-4" stroke-width="3"/></g>`,
  mic: `<g ${S}><rect x="23" y="8" width="18" height="30" rx="9" fill="${Y}"/><path d="M15 30a17 17 0 0 0 34 0M32 47v9M24 56h16"/></g>`,
};
const APPS = [['streaming1', 'rose', 'play'], ['streaming2', 'sky', 'tv'], ['music', 'mint', 'music'], ['video', 'wheat', 'camera'],
  ['sports', 'lilac', 'ball'], ['photos', 'peach', 'photo'], ['kids', 'aqua', 'balloon'], ['podcasts', 'cream', 'mic']];
const UI = {
  gear: '<circle cx="32" cy="32" r="7"/><path d="M32 8v7M32 49v7M8 32h7M49 32h7M15 15l5 5M44 44l5 5M15 49l5-5M44 20l5-5"/>',
  sleep: '<path d="M44 40A18 18 0 0 1 24 14a18 18 0 1 0 20 26z"/>',
  picture: '<path d="M8 14h48v36H8zM8 42l14-12 10 9 8-7 16 13"/>',
  edit: '<path d="M40 12l12 12-26 26H14V38z"/>',
  power: '<path d="M32 10v18M20 18a18 18 0 1 0 24 0"/>',
  hdmi: '<path d="M10 22h44v10l-7 8H17l-7-8zM20 30h24"/>',
  livetv: '<path d="M10 20h44v30H10zM24 10l8 9 8-9"/>',
  close: '<path d="M20 20l24 24M44 20L20 44"/>',
  back: '<path d="M38 14L20 32l18 18"/>',
  plus: '<path d="M32 14v36M14 32h36"/>',
  check: '<path d="M16 33l10 10 22-22"/>',
  wifi: '<path d="M10 26a31 31 0 0 1 44 0M18 35a19 19 0 0 1 28 0M26 44a8 8 0 0 1 12 0"/><circle cx="32" cy="51" r="1.5"/>',
  wx_clear_night: '<path d="M44 40A18 18 0 0 1 24 14a18 18 0 1 0 20 26z"/>',
  wx_sun: '<circle cx="32" cy="32" r="10"/><path d="M32 8v6M32 50v6M8 32h6M50 32h6M15 15l4 4M45 45l4 4M15 49l4-4M45 19l4-4"/>',
  wx_rain: '<path d="M20 38h26a10 10 0 0 0 0-20 14 14 0 0 0-27 4 8 8 0 0 0 1 16zM24 46l-3 7M34 46l-3 7M44 46l-3 7"/>',
  wx_cloud: '<path d="M18 46h28a11 11 0 0 0 0-22 15 15 0 0 0-29 4.5A8.8 8.8 0 0 0 18 46z"/>',
  wx_snow: '<path d="M32 8v48M11 20l42 24M11 44l42-24M26 12l6 5 6-5M26 52l6-5 6 5"/>',
};
const MARK = (fill, ink) => `<svg viewBox="0 0 64 64" width="100%" height="100%"><circle cx="32" cy="32" r="30" fill="${fill}"/><circle cx="32" cy="32" r="17" fill="none" stroke="${ink}" stroke-width="4" stroke-dasharray="6 6.4" stroke-linecap="round" transform="rotate(-12 32 32)"/><circle cx="32" cy="32" r="7" fill="${ink}"/></svg>`;

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  async function shot(html, w, h, out, transparent = true) {
    await p.setViewportSize({ width: w, height: h });
    const tmp = path.join(ROOT, '_render.html');
    fs.writeFileSync(tmp, `<!doctype html><html><head><style>${css}</style></head><body>${html}</body></html>`);
    await p.goto('file://' + tmp, { waitUntil: 'load' });
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(80);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    await p.screenshot({ path: out, omitBackground: transparent, clip: { x: 0, y: 0, width: w, height: h } });
  }
  for (const [n, tex, ic] of APPS) {
    for (const [w, h, sfx] of [[288, 192, ''], [317, 211, '_focus']]) {
      const r = Math.round(34 * w / 288), isz = Math.round(92 * w / 288);
      await shot(`<div style="position:relative;width:${w}px;height:${h}px;border-radius:${r}px;overflow:hidden;box-shadow:inset 0 0 0 1px rgba(255,255,255,.4)">
        <img src="file://${ROOT}/tiles/tile_${tex}.jpg" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover">
        <div style="position:absolute;inset:0;background:linear-gradient(160deg,rgba(255,255,255,.38),rgba(255,255,255,.12))"></div>
        <div style="position:absolute;inset:0;border-radius:${r}px;box-shadow:inset 0 0 0 1px rgba(255,255,255,.45)"></div>
        <svg viewBox="0 0 64 64" style="position:absolute;left:50%;top:50%;width:${isz}px;height:${isz}px;transform:translate(-50%,-50%)">${ICON[ic]}</svg></div>`,
        w, h, `${PK}/tiles/tile_${n}${sfx}.png`);
    }
  }
  for (const [n, d] of Object.entries(UI)) {
    await shot(`<svg viewBox="0 0 64 64" width="64" height="64" fill="none" stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`, 64, 64, `${PK}/ui/ic_${n}.png`);
  }
  for (const s of [48, 60, 72, 120, 168]) {
    await shot(`<div style="width:${s}px;height:${s}px">${MARK('#FFDE03', '#1C1C1C')}</div>`, s, s, `${PK}/brand/zeus_mark_${s}.png`);
  }
  await shot(`<div style="width:120px;height:120px">${MARK('#FBF7EC', '#1C1C1C')}</div>`, 120, 120, `${PK}/brand/zeus_mark_cream_120.png`);
  const bg = `background:radial-gradient(80% 90% at 20% 10%,#2c3590 0%,rgba(44,53,144,0) 60%),radial-gradient(70% 80% at 90% 100%,#1a1f5c 0%,rgba(26,31,92,0) 70%),#141844`;
  const logo = (W, H, mark, word, sub) => `<div style="width:${W}px;height:${H}px;${bg};position:relative;overflow:hidden;display:flex;align-items:center;justify-content:center;gap:${Math.round(mark * .22)}px;color:#fff">
    <img src="file://${ROOT}/out/wall_starry.jpg" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:.55">
    <div style="position:absolute;inset:0;background:radial-gradient(60% 70% at 50% 50%,rgba(20,24,74,.75),rgba(20,24,74,.2))"></div>
    <div style="position:relative;width:${mark}px;height:${mark}px">${MARK('#FFDE03', '#1C1C1C')}</div>
    <div style="position:relative"><div style="font-family:F;font-variation-settings:'SOFT' 100,'opsz' 144;font-weight:520;font-size:${word}px;letter-spacing:-.03em;line-height:.9">Lumen</div>
    ${sub ? `<div style="font-family:M;font-weight:700;font-size:${sub}px;opacity:.8;margin-top:${sub * .5}px;letter-spacing:.04em">A calmer home for your TV</div>` : ''}</div></div>`;
  await shot(logo(540, 405, 120, 92, 0), 540, 405, `${PK}/brand/poster_fhd_540x405.png`, false);
  await shot(logo(290, 218, 64, 50, 0), 290, 218, `${PK}/brand/poster_hd_290x218.png`, false);
  await shot(logo(1920, 1080, 220, 168, 34), 1920, 1080, `${PK}/brand/splash_fhd.png`, false);
  await shot(`<div style="display:flex;align-items:center;gap:36px;padding:20px;color:#1C1C1C"><div style="width:168px;height:168px">${MARK('#FFDE03', '#1C1C1C')}</div><div style="font-family:F;font-variation-settings:'SOFT' 100,'opsz' 144;font-weight:520;font-size:150px;letter-spacing:-.03em;line-height:.9">Lumen</div></div>`, 720, 210, `${PK}/brand/lumen_logo_ink.png`);
  await b.close();
  console.log('rendered');
})();
