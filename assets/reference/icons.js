// Lumen icon set: 64 grid, 4px #1C1C1C stroke, round caps, one ImpulsTrip-yellow "light" per icon.
const Y = '#FFDE03', K = '#1C1C1C';
const S = `fill="none" stroke="${K}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"`;
const F = `fill="${Y}" stroke="${K}" stroke-width="4" stroke-linejoin="round"`;
window.ICONS = {
  play: `<path d="M24 17.5c0-2.6 2.9-4.2 5.1-2.8l17.4 11.7c2 1.3 2 4.2 0 5.5L29.1 43.6c-2.2 1.4-5.1-.2-5.1-2.8z" ${F}/>`,
  tv: `<rect x="10" y="14" width="44" height="30" rx="7" ${S}/><path d="M24 52h16M32 44v8" ${S}/><path d="M32 21.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z" ${F} stroke-width="3"/>`,
  music: `<path d="M26 44V16l22-5v28" ${S}/><path d="M26 22l22-5" ${S}/><ellipse cx="20" cy="44" rx="7" ry="6" ${F}/><ellipse cx="42" cy="39" rx="7" ry="6" ${F}/>`,
  camera: `<rect x="8" y="20" width="36" height="28" rx="7" ${S}/><path d="M44 30l12-7v22l-12-7" ${S}/><circle cx="26" cy="34" r="7" ${F}/>`,
  ball: `<circle cx="32" cy="32" r="20" ${F}/><path d="M32 12c-6 6-6 34 0 40M12.5 28c8 4 31 4 39 0M14 42c8-3 28-3 36 0" ${S} stroke-width="3.5"/>`,
  photo: `<rect x="9" y="13" width="46" height="38" rx="8" ${S}/><path d="M11 45l13-14 10 10 6-6 13 12" ${S}/><circle cx="41" cy="24" r="5" ${F} stroke-width="3.5"/>`,
  balloon: `<path d="M32 10c-9 0-15 7-15 15 0 10 9 17 15 19 6-2 15-9 15-19 0-8-6-15-15-15z" ${F}/><path d="M32 44l-2.5 3h5zM32 47c-3 4 3 7 0 11" ${S} stroke-width="3.5"/><path d="M24.5 20a8 8 0 0 1 5-4" ${S} stroke-width="3"/>`,
  mic: `<rect x="23" y="8" width="18" height="30" rx="9" ${F}/><path d="M15 30a17 17 0 0 0 34 0M32 47v9M24 56h16" ${S}/>`,
  // UI glyphs (currentColor)
  gear: `<circle cx="32" cy="32" r="7" fill="none" stroke="currentColor" stroke-width="4"/><path d="M32 8v7M32 49v7M8 32h7M49 32h7M15 15l5 5M44 44l5 5M15 49l5-5M44 20l5-5" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>`,
};
window.MARK = (size, ring = K) => `<svg viewBox="0 0 64 64" width="${size}" height="${size}" aria-hidden="true"><circle cx="32" cy="32" r="30" fill="${Y}"/><circle cx="32" cy="32" r="17" fill="none" stroke="${ring}" stroke-width="4" stroke-dasharray="6 6.4" stroke-linecap="round" transform="rotate(-12 32 32)"/><circle cx="32" cy="32" r="7" fill="${ring}"/></svg>`;
