# Lumen build plan

Source of truth: `LUMEN_DESIGN_SPEC.md` (section numbers below refer to it). Assets in `assets/` are used untouched; build scripts copy them into each platform.

Target hardware found on the home network (27 Oct 2026):
- **Hisense 50" Roku TV**, model G218X ("6Series-50"), Roku OS 16.0.4, at `192.168.2.33`, Wi-Fi. ECP queries answer; installed apps: Netflix, Prime Video, YouTube, Spotify, Backdrops, plus HDMI inputs named "PlayStation 5".
- Phone: a spare Android phone that stays home on the same Wi-Fi (Pixel 9a class, Android 16+).
- Mac build host: Java 23, Android SDK (platforms 34–36, build-tools 36), adb, Node 20. No Gradle install (use the wrapper).

---

## 1. Repo layout

```
lumen/
  roku/        manifest, source/, components/, build.sh (stages assets/images + assets/fonts, zips to roku/out/lumen.zip)
  android/     Gradle wrapper project, app/ (Kotlin + Compose)
  backend/     supabase/functions/zeus/, supabase/migrations/
  assets/      the design pack, untouched
  PLAN.md  PROGRESS.md  LUMEN_DESIGN_SPEC.md  CLAUDE_CODE_PROMPT.md
```

## 2. Roku channel (spec 2, 6–8, 10–14)

```
source/main.brs                    roSGScreen + roInput port; passes launch args and /input events to the scene (15.6)
components/common/Theme.brs        ALL constants: colours, 5 themes + deep colours, pastels, fonts, layout numbers, pastel hash
components/common/Fonts.brs        font node factory (Fraunces/Manrope static TTFs)
components/common/Toast.xml        6.8
components/MainScene.xml/.brs      layer stack 6.1, focus model 11, key routing, overlay stack
components/wall/WallpaperLayer     6.1 layers 1–4: sharp + MaskGroup blur (clippingRect fallback) + deep scrims; 2 pairs for crossfade (M5)
components/home/ClockBlock         6.2 date eyebrow, clock (+2px deep shadow label), weather line (M5)
components/home/TopControls        6.3 Ask Zeus pill + gear
components/home/AppRow + AppTile   6.5 framed ECP icons, pastel hash, ring/glow/shadow, focus anims, row slide, edge fade, Add app tile
components/home/BottomBar          6.6 input pills, divider, 4 quick actions, streaming-stick reflow
components/home/PaintingCredit     6.7
components/home/SuggestionStrip    6.4 (M7)
components/screens/LaunchOverlay   13.1 + 13.2 (launching + error, Try again / Dismiss)
components/ecp/EcpTask             Task: query apps/device-info, launch, keypress against http://<own-ip>:8060
components/weather/WeatherTask     8.1–8.2 (M5)
components/theme/ThemeManager      8.3 crossfade + ColorFieldInterpolator (M5)
components/zeus/*                  ZeusPanel, ZeusTask, PhoneBridge, ActionRunner (M6)
components/screens/*               OptionsSheet, EditHome, AppPicker, Settings, Ambient, Onboarding (M7)
```

Focus model: `MainScene` owns focus explicitly (zone + index per zone, each zone remembers its last item) and pushes a `focused` boolean into each component; overlays sit on a stack and get keys first. This guarantees "exactly one focused element" without fighting SceneGraph's focus chain.

Pastel hash (must be identical on the phone): `h = 0; for each char c in appId: h = (h * 31 + ord(c)) mod 1000003`; pastel = `["rose","sky","mint","wheat","lilac","peach","aqua","cream"][h mod 8]`.

## 3. Android app (spec 15)

Single-activity Compose app, package `app.lumen`, layout exactly as spec 15.7. Custom theme tokens in `ui/theme/`, fonts in `res/font`, wall thumbs/frames/zeus mark copied by a Gradle `copyLumenAssets` task from `assets/`. `TvWatcherService` (foreground, `connectedDevice`) implements 15.4; `EcpClient` (OkHttp + XmlPullParser) implements 15.3; `SsdpDiscovery` with MulticastLock.

## 4. Backend (spec 9)

**Owner decision (28 Oct): Firebase instead of Supabase.**
- Auth: email/password and Google.
- Firestore: `users/{uid}` and `leaderboard/{game}/scores/{uid}`, with rules in `firebase/firestore.rules`.
- Zeus: Firebase AI Logic (Gemini), called from the phone. The TV's Zeus goes through the phone's bridge `/zeus`.

The Supabase plan below is kept for reference.

Supabase Edge Function `zeus` (Deno): validates the 9.2 request, builds the system prompt from 7.3 + 9.2 essentials, calls the Claude Messages API with `claude-haiku-5-5`, `max_tokens: 300`, one forced tool `respond` whose JSON schema is exactly `{reply, actions[], chips[]}` with the 9.2 action enum. Server-side filtering drops actions with unknown types or IDs not in `context`. Rate limit 30 req / 10 min per bearer token in a `zeus_rate` table. Secret `ANTHROPIC_API_KEY` set with `supabase secrets set`; never in clients.

## 5. Build order

| Milestone | Scope | Spec |
|---|---|---|
| M1 Roku | manifest, wallpaper layers, scrims, clock block, starry hard-coded | 6.1, 6.2, 14 |
| M2 Roku | ECP app row in frames, focus treatment + anims, launching + error overlay, top controls, bottom bar, toast, credit | 6.3, 6.5–6.8, 11, 12, 13.1–13.2 |
| M3 Android | SSDP, EcpClient, Remote tab pixel-matched | 15.2, 15.3, 15.5 |
| M4 Android | TvWatcherService auto-open, boot/Wi-Fi triggers, notification, Setup tab, onboarding checks | 15.4, 15.5 |
| M5 both | weather + 5-theme engine, 900 ms repaint, phone theme parity, Apps tab, `?theme=` sync | 8, 15.5, 15.6 |
| M6 all | `zeus` edge function; phone Zeus tab + local intents + ActionRunner + sleep timer; TV Zeus panel + PhoneBridge | 7, 9, 15.5, 15.6 |
| M7 | suggestion strip habits, Edit Home, App picker, Settings, Ambient, TV onboarding; QS tile, widget, wake word | 6.4, 7.3, 13, 15.5 |

## 6. Things in the spec that won't work as written, and the fix

**Roku**
1. **No `outBack` easing on Roku.** `Animation.easeFunction` has quad/cubic/quartic/quintic/expo/bounce only. Fix: express the overshoot as interpolator keys (`key [0, .6, 1]`, `keyValue [1.0, 1.13, 1.10]`) with `outCubic`.
2. **`/input` to the channel needs a manifest flag.** Without `supports_input_launch=1` Roku drops `POST /input` for the channel. Added to the manifest in M1.
3. **CONFIRMED BLOCKER: a channel can't use ECP on its own TV.** On the Hisense (Roku OS 16.0.4) every request from Lumen to its own IP, `127.0.0.1` or `localhost` returns 403, with any User-Agent, even with `ecp-setting-mode: permissive`. The same requests from the Mac succeed. Fix: the **Lumen bridge** (section 7). Lumen never talks ECP directly; it talks to a bridge on another device that relays a fixed ECP subset.
4. **Spec 6.3 contradicts itself** on the gear position. Resolved: gear flush at right edge x=1824; Ask Zeus to its left with a 14 px gap.
5. **No dashed 9-patch for the Add app tile** in the pack, and no horizontal fade for the row's right edge (6.5). Fix: Add app uses the cream frame over a paper fill with the ink plus glyph; `roku/tools/gen_fades.py` (pure-Python PNG writer, no PIL) generates `fade_right.png`. Both are additions, not changes to the pack.
6. **Focus ring geometry:** the ring is 8 px outside the tile, so it lives inside the tile's scaled group and scales with it (the spec's `_focus` asset only exists for Lumen glyph tiles).
7. **Roku home detection (15.4):** on Roku TVs the active app after power-on can also be a `tvin` input (power on to last input). The watcher treats `tvinput.*` as "user chose something else" and does not hijack.

**Android**
8. **SSID / "home Wi-Fi" detection needs location permission** on Android 10+. Fix: don't read the SSID. "Home" = on an unmetered Wi-Fi network *and* the saved TV (matched by `serial-number`) answers ECP. No location permission needed.
9. **Plain HTTP to the TV is blocked by default** (cleartext). Fix: `network_security_config.xml` permitting cleartext; the backend is HTTPS regardless. (IP-literal domain-configs aren't supported, so it's a base-config allowance.)
10. **Exact alarms** need `SCHEDULE_EXACT_ALARM`, which the user must grant on Android 14+. Fix: the running foreground service owns the sleep timer (coroutine + `AlarmManager.setAndAllowWhileIdle` backup); request exact-alarm access only if the user enables timers longer than the service is guaranteed alive.
11. **Local-network runtime permission** on newer Android: requested in onboarding step 2 right before SSDP, with a one-line reason.
12. **Boot-started foreground services** are restricted on Android 15+ for some types; `connectedDevice` is still allowed from `BOOT_COMPLETED`. If that changes, fallback is starting on first unlock (`USER_PRESENT`).

**Backend**
13. Needs a Supabase project and an Anthropic API key from the owner. Everything up to M6 works without them.

**Found on the real TV (M2 testing)**
14. Labels with no `width` aren't drawn on this TV. Every Label gets an explicit width (and height where it's large).
15. `MaskGroup` didn't apply `blur_mask.png`, so the whole painting was blurred. The blur fade is built from 13 clipped strips of the same blur texture at rising opacity (same 497→691 px ramp).
16. A full-height deep gradient for the row's right-edge fade showed as a box over the painting. Tiles past x=1560 fade by their own opacity instead.
17. Colour fields read back as integers, not strings. `WithAlpha` accepts both.
18. Chrome's file upload limit is 10 MB; the pack's 3.6 MB PNG splash is converted to JPG at build time (zip 7.5 MB).

## 7. Lumen bridge

Lumen → bridge (HTTP on the LAN) → TV ECP. The bridge mirrors ECP paths so the channel code stays ECP-shaped:

| Bridge endpoint | Relays to |
|---|---|
| `GET /query/apps`, `/query/device-info`, `/query/active-app` | same ECP path |
| `GET /query/icon/<id>` | same (Posters load icons straight from the bridge) |
| `POST /launch/<id>` | same; `id` must match `\d+`, `tvinput.*` or `dev` |
| `POST /keypress/<key>` | same; whitelisted keys only |
| `GET /ping` | `lumen-bridge` |

Discovery: whenever Lumen is the active app, the bridge posts `/input?bridge=http://<its-ip>:8977` to the TV once per Lumen session. Lumen saves it in the registry (`bridge` key), so the next launch loads apps right away.

- **Now:** `bridge/lumen_bridge.py` on the Mac (stdlib only).
- **M4:** the Android `TvWatcherService` hosts the same endpoints (small embedded HTTP server on 8977) and announces itself the same way, so the Mac helper can be retired.
- Without a reachable bridge, Lumen still shows the painting, clock and controls, with a "Waiting for the Lumen helper…" toast; launches show "Can't reach the Lumen helper".
19. **Owner decision (27 Oct): app logos sit directly on the painted card.** This replaces spec 5.3's "icon inside the frame window" and its "never recolour" rule. The bridge removes each Roku icon's background (`bridge/lumen-img key`: flood fill from the border, feathered edge, trimmed and centred). Logos that are mostly white are drawn in ink so they read on pastels (Prime Video, Backdrops); coloured logos keep their own colours (Netflix, YouTube, Spotify). Cards are rounded versions of the pack's tile textures (`lumen-img card`, 288×192 r34). The phone bridge (M4) must do the same processing.
