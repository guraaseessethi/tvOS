# Lumen: design spec

**A Roku TV home screen in Vincent van Gogh's style that repaints itself with the weather, plus an Android phone app that opens it automatically when the TV turns on, works as the remote, and lets you talk to Zeus, the AI assistant.**

Lumen is three pieces that work as one product:

| Piece | What it is | Tech |
|---|---|---|
| **Lumen for Roku** | The TV home screen: painting, clock, app tiles, Zeus panel. | Roku SceneGraph (BrightScript), sideloaded dev channel |
| **Lumen phone app** | Android app (APK). It watches for the TV turning on and opens Lumen, works as a full remote with a mic, and is Zeus's main way to hear you. | Kotlin, Jetpack Compose, foreground service |
| **Zeus backend** | The AI brain both clients call. It holds the API key. | Supabase Edge Function (Deno/TypeScript) calling the Claude API |

This doc is the source of truth for all three. Part A (sections 1 to 14) is the TV. Part B (section 15) is the phone app. Section 9 is the shared Zeus brain. TV pixel values are for the 1920×1080 FHD canvas. Phone values are in dp for a 412dp-wide screen. Where this doc and a reference image disagree, this doc wins.

Reference images are in `reference/`:
- `home_clear_night.jpg`, `home_sunny.jpg`, `home_rain.jpg`, `home_cloudy.jpg` show the TV home screen in four weathers.
- `home_snow_zeus_open.jpg` shows the TV Zeus panel open.
- `home_reference.html` is a working browser mockup. Open it with `?t=starry|sunflower|irises|blossom|snow`, `&f=<0-7>` to pick the focused tile, and `&theo` to open the Zeus panel.
- `phone/phone_remote.png`, `phone_zeus.png`, `phone_apps.png` and `phone_setup.png` are the phone app's four tabs, with matching `phone_*.html` source.

---

## 1. The idea in one paragraph

Lumen replaces the cluttered Roku home screen with one calm screen. A full-screen original painting in Van Gogh's swirling brushwork sits behind everything, and the painting changes with the real weather outside: a starry night on clear nights, a sunflower field when it's sunny, irises in the rain, almond blossom when it's cloudy, a snowy village when it snows. The UI floats on top in bold pastels with ImpulsTrip yellow (#FFDE03) as the only accent: every "light" in the system is yellow, from the stars in the paintings to the focus ring to Zeus himself. Zeus is the assistant. He greets you on the TV, suggests what to open based on your habits and the weather, opens apps, switches inputs, controls volume, sets a sleep timer and can repaint the screen on request.

The phone app makes it feel like Lumen *is* the TV's home screen. Turn the TV on and Lumen opens by itself within about 2 seconds. Pick up the phone and you have a remote in the same painted style, with a big yellow "Hold to talk to Zeus" button. Whatever you say is done on the TV, and the conversation shows up on the TV's Zeus panel too.

**Mood words:** calm, painterly, warm, confident, gallery-quality, never busy.
**Not:** glassy-grey, neon, techy, cluttered, ad-driven, generic launcher.

---

## 2. Platform reality (read first, design depends on it)

Lumen is a normal Roku channel, not a system replacement. These limits shape the build:

| Fact | What we do about it |
|---|---|
| A channel can't replace Roku's own home screen or auto-start on boot. | **The phone app does it.** It watches the TV over home Wi-Fi (ECP `GET /query/device-info`, `power-mode`) and, when the TV turns on, sends `POST /launch/dev` (the sideloaded channel's ID) as soon as Roku's home screen is up (section 15.4). The TV's Settings > Startup screen shows whether a phone is connected. |
| No live backdrop blur, no shaders, weak GPUs on budget TVs. | Every blur is pre-rendered. Each wallpaper ships with a `_blur.jpg` version. "Frosted" surfaces are solid tinted 9-patch shapes at partial opacity. |
| The channel can't see inside other apps. | Tiles are shortcuts only. No "continue watching" rows and no posters from other services. Zeus never claims to know what is playing. |
| Installed apps, launching and inputs go through ECP (External Control Protocol) on the TV's own IP, port 8060. | Use `GET /query/apps`, `GET /query/icon/<id>`, `POST /launch/<id>`, and `POST /launch/tvinput.hdmi1` (and similar) via `roUrlTransfer` to `http://<own-ip>:8060`. Get the IP from `roDeviceInfo.GetIPAddrs()`. If ECP is blocked, show the launch error state: "Turn on Settings > System > Advanced system settings > Control by mobile apps". |
| There is no public API for picture mode or the TV's own sleep timer. | Zeus's sleep timer runs on the phone (it survives the user leaving Lumen) and sends ECP `keypress/PowerOff` at the set time. Picture mode is not offered as an action. Zeus explains: "Press ✱ while watching to change picture mode." |
| Channels can't use the remote's microphone. | **The phone is Zeus's mic.** Hold-to-talk uses Android speech recognition. On the TV itself Zeus still works through suggestion chips and Roku's on-screen keyboard (`StandardKeyboardDialog`). |
| A channel only runs while it's on screen. When you open Netflix, Lumen stops. | Anything that must outlive Lumen (sleep timer, auto-open, voice) runs on the phone. The phone talks to Lumen only while it's open: `POST /input?...` for live messages, or `POST /launch/dev?...` to open it with a message. |
| An API key must never ship inside the channel package. | Zeus's AI runs through a backend proxy (section 9). |
| Label nodes have no letter-spacing or text-shadow. | Tracked eyebrow text is written in caps without tracking. Text sits on scrims, which carry legibility. For the clock, an optional duplicate Label offset 2px in the deep colour at 50% opacity acts as a shadow. |
| Variable fonts can't be controlled on Roku. | Static TTF instances are already built in `fonts/` (section 4). |

---

## 3. Colour

### 3.1 Brand constants

| Token | Hex | Use |
|---|---|---|
| `yellow` | #FFDE03 | The only accent. Focus ring, Zeus button, Zeus mark, user chat bubbles, icon highlights, toggles on. |
| `ink` | #1C1C1C | Text on light surfaces, icon line work, primary buttons inside the Zeus panel, focus ring outline. |
| `white` | #FFFFFF | Text on paintings, action buttons on the home screen. |
| `paper` | #FBF7EC | The Zeus panel background (it reads like a letter). |
| `ink60` | #1C1C1C at 60% | Secondary text on paper. |

### 3.2 Per-painting theme (the "deep" colour)

Each painting has one **deep** colour. Every scrim, frosted surface and dim layer is that deep colour at a fixed opacity, so the whole UI takes on the painting's mood. Text on the home screen is always white.

| Theme key | Painting | Weather | Deep RGB | Deep hex |
|---|---|---|---|---|
| `starry` | Starry Night | Clear, after sunset | 20,24,74 | #14184A |
| `sunflower` | Sunflower Field | Clear or mostly clear, daytime | 112,62,0 | #703E00 |
| `irises` | Irises in the Rain | Drizzle, rain, showers, thunderstorms | 44,36,96 | #2C2460 |
| `blossom` | Almond Blossom | Partly cloudy, overcast, fog | 10,70,78 | #0A464E |
| `snow` | Snowy Evening | Snow, snow grains, snow showers | 46,48,104 | #2E3068 |

Deep colour usage (opacity applied to the deep colour):

| Surface | Opacity |
|---|---|
| Frosted pill / round button / suggestion strip fill | 34% |
| Bottom scrim (gradient, see 6.2) | 0% at y=432, 42% at y=648, 78% at y=1080 |
| Top-left scrim (radial from 0,0, radius 1200×700) | 70% at the corner, 0% at 70% of the radius |
| Dim layer behind the Zeus panel | 45% |
| "Today" card band in the Zeus panel | 85% on the left, 10% on the right |

Frosted surfaces also get a hairline: `pill_64_stroke.9.png` on top (white 16% outline plus a white 37% arc highlight along the top).

### 3.3 Tile pastels

Eight painted pastel textures, assigned to apps by `hash(appId) mod 8` so an app keeps its colour forever:

| Key | Base hex |
|---|---|
| rose | #F7C6CF |
| sky | #BCD6FF |
| mint | #BFEAD7 |
| wheat | #FFE2A0 |
| lilac | #D6CBFF |
| peach | #FFD0B8 |
| aqua | #BDE9F0 |
| cream | #FBF3DC |

---

## 4. Typography

Two families, both SIL OFL (free to bundle). They are already instanced to static TTFs in `fonts/`.

| File | Built from | Use |
|---|---|---|
| `Fraunces-Display.ttf` | Fraunces opsz 144, wght 420, SOFT 100, WONK 0 | The clock only |
| `Fraunces-Title.ttf` | Fraunces opsz 72, wght 560, SOFT 100 | "Zeus" title, Today card headline, section titles |
| `Fraunces-Italic.ttf` | Fraunces Italic opsz 36, wght 500, SOFT 100 | Painting name label |
| `Manrope-SemiBold.ttf` | Manrope wght 600 | Body, weather line, chat bubbles, suggestion text |
| `Manrope-Bold.ttf` | Manrope wght 700 | Pills, chips, secondary labels |
| `Manrope-ExtraBold.ttf` | Manrope wght 800 | Eyebrows, buttons, focused tile name, toast |

Type scale (px at 1920×1080):

| Role | Font | Size | Notes |
|---|---|---|---|
| Clock | Fraunces-Display | 176 | Line height 1.0. Format h:mm, 12-hour default, no AM/PM. |
| Zeus title | Fraunces-Title | 52 | |
| Today headline | Fraunces-Title | 40 | |
| Painting name | Fraunces-Italic | 30 | |
| Suggestion text | Manrope-SemiBold | 27 | |
| Weather line | Manrope-SemiBold | 28 | |
| Focused tile name | Manrope-ExtraBold | 28 | |
| Chat bubble | Manrope-SemiBold | 26 | Line height 1.4, wrap at 470px |
| Pill / button label | Manrope-Bold or ExtraBold | 24 | |
| Chip | Manrope-Bold | 23 | |
| Eyebrow (date) | Manrope-ExtraBold | 22 | UPPERCASE |
| Eyebrow (painting credit) | Manrope-ExtraBold | 18 | UPPERCASE, 78% white. The only text under 22px. |

Rules: nothing thinner than 600 under 40px. Fraunces only for display moments (clock, names, titles), never for UI controls.

---

## 5. Iconography and marks

### 5.1 The mark (Lumen + Zeus share it)

One of Vincent's stars: a yellow disc, a ring of 8 dashed brush strokes, and a solid ink dot in the middle.
- On a 64 grid: disc r30 #FFDE03, dashed ring r17 stroke 4 #1C1C1C (dash 6, gap 6.4, round caps, rotated -12°), centre dot r7 #1C1C1C.
- Files: `images/brand/zeus_mark_{48,60,72,120,168}.png` and `zeus_mark_cream_120.png` (cream disc for use on yellow).
- The Lumen logo is the mark plus the word "Lumen" in Fraunces 520 SOFT 100: `lumen_logo_ink.png`.

### 5.2 App glyphs (Lumen's own tiles)

A 64 grid, 4px #1C1C1C line, round caps and joins, and exactly **one yellow fill per icon**: the "spot of light" (play triangle, star on the TV, note heads, lens, ball, sun, balloon, mic grille). They sit centred on painted pastel tiles at 92px.
- Pre-rendered tiles: `images/tiles/tile_<name>.png` at 288×192, plus `tile_<name>_focus.png` at 317×211 for crisp 1.1× focus.
- Names: streaming1, streaming2, music, video, sports, photos, kids, podcasts.
- These are for previews, Lumen's built-in tiles (Photos, Add app) and the fallback when an app icon fails to load.

### 5.3 Real installed apps

Roku gives each app's own icon as a rectangular image (`/query/icon/<id>`, about 290×218). Never redraw or recolour third-party logos. Instead:
- Draw the app icon as a Poster at **208×156**, centred in the 288×192 tile (x+40, y+18).
- Put `images/tiles/frame_<pastel>.png` (288×192) **on top**. It is the painted tile with a rounded r20 window cut out and a 3px paper lip, so the logo gets rounded corners and a painted border without any masking.
- Pastel = `hash(appId) mod 8`.

### 5.4 UI glyphs

White 64px PNGs in `images/ui/ic_*.png`, drawn at 30px on 64px buttons. Set `blendColor` to ink when they sit on paper or yellow. The set: gear, sleep, picture, edit, power, hdmi, livetv, close, back, plus, check, wifi, wx_clear_night, wx_sun, wx_rain, wx_cloud, wx_snow.

---

## 6. Home screen layout (FHD, x/y = top-left)

Safe areas: action-safe is 48/27 px from the edges, title-safe is 96/54 px. All text stays within title-safe. The left content edge is x=96.

### 6.1 Layers (back to front)

1. `wallSharp`: Poster, the painting, 1920×1080.
2. `wallBlur`: Poster, the same painting's `_blur.jpg`, faded in from y=497 to y=691 (46% to 64% of the height) so the painting dissolves into soft blur behind the apps. Use a `MaskGroup` with `images/ui/blur_mask.png` stretched to 1920×1080. **Fallback** where MaskGroup is unsupported: a Group with `clippingRect=[0,600,1920,480]` holding the blur Poster at 0,0, with the seam hidden by the bottom scrim.
3. `scrimTopLeft`: `images/ui/scrim_topleft.png` (half-res white alpha) drawn at 1920×1080, `blendColor` = deep.
4. `scrimBottom`: `images/ui/scrim_bottom.png` drawn at 1920×1080, `blendColor` = deep.
5. Content (sections 6.2 to 6.8).
6. Overlays: toast, then the Zeus dim layer and Zeus panel.

For the crossfade (section 8.3), keep two wallSharp+wallBlur pairs and swap them.

### 6.2 Clock block (top-left)

- Date eyebrow: x=96, y=66, ExtraBold 22, white 90%, for example "TUESDAY 27 OCTOBER".
- Clock: x=96 (minus about 4px optical correction for the Fraunces side bearing), y=92, Fraunces-Display 176, white.
- Weather line: x=96, y=282, SemiBold 28, white 94%. Format: `8°  Clear in Barrie` (two spaces after the degree sign). The temperature unit follows Settings.
- This block is not focusable. Pressing up from the suggestion strip goes to the Zeus button, not here.

### 6.3 Top-right controls

- **Ask Zeus** button: right edge at x=1824, y=72, height 64, radius 32, fill #FFDE03. Zeus mark 48px at an 8px inset, 12px gap, label "Ask Zeus" ExtraBold 24 in ink, 26px right padding. A soft shadow below (`tile_shadow.png` scaled, or skip on low-end).
- **Settings**: a 64×64 round frosted button with the gear glyph, 14px left of... actually to the right of Ask Zeus, flush at x=1824. Order left to right: [Ask Zeus] [⚙].

### 6.4 Zeus suggestion strip

- x=96, y=452, height 88, radius 44 (`pill_88.9.png`), frosted (deep at 34% plus stroke).
- Contents left to right: Zeus mark 60px at a 14px inset, 18px gap, suggestion text (SemiBold 27, white, one line, max 980px with ellipsis), 10px gap, action button (white pill h64, padding 26, ExtraBold 24 ink, e.g. "Open Music"), dismiss × (52px round, white glyph).
- It appears 400ms after Home shows (rise 10px + fade, 350ms ease-out). After a dismiss it collapses (fade 200ms) and the app row stays where it is.
- At most one suggestion is shown. It refreshes when the theme changes or every 30 minutes.

### 6.5 App row (the hero)

- Tiles are 288×192, radius 34, with a 36px gap (324px pitch). The first tile is at x=96, y=624.
- Each tile is a `tile_shadow.png` drop shadow (offset y+18) plus the tile (an installed app in a frame, or a Lumen tile).
- **Focused tile:**
  - Scale 1.10 about its centre and move up 10px (use the `_focus` asset for Lumen tiles).
  - Draw `focus_ring_tile.png` (304×208: a 5px yellow ring with a 2px ink outline outside, 8px out from the tile) and `focus_glow_tile.png` (yellow glow, 47% alpha, blurred) behind it.
  - The app name fades in centred below the tile, top = tile top + 224, ExtraBold 28 white. Long names are clipped at 340px and marquee-scroll after 1s (a `ScrollingLabel`).
- **Right-edge fade:** the last 360px (x=1560 to 1920) of the row gets a gradient Poster in the deep colour (alpha 0 to 80%), so tiles dissolve rather than cut off. No arrows.
- **Scrolling:** focus moves within the visible tiles. When it would pass the 4th visible slot, the row slides one pitch (324px) so the focused tile stays at most in slot 4. The row never scrolls past the first tile.
- An "Add app" tile is always last: paper-coloured, dashed 3px ink outline (9-patch), plus glyph, label "Add app".
- Default focus on launch: the first tile.

### 6.6 Inputs and quick actions (bottom-left)

- Row baseline: y=956 (bottom 60), height 64, gap 12.
- **Input pills** (Roku TV only), frosted `pill_64.9.png`: 20px left padding, 30px glyph, 12px gap, Bold 24 label, 24px right padding. The label is the user's custom name ("PlayStation", "Apple TV") or the default ("HDMI 1", "Live TV", "AV").
- Divider: 1×36px white 35%, with 10px margins.
- **Quick actions**: four 64px round frosted buttons: Sleep timer, Picture (opens a Zeus explainer, see section 2), Edit Home, Turn off TV.
- On streaming sticks (no `tvinput` apps in `/query/apps` and the `roDeviceInfo` model is not a TV), hide the inputs, Picture and Turn off TV. The remaining controls shift left.

### 6.7 Painting credit (bottom-right)

- Right edge x=1824. Line 1 at y=944: the painting name in Fraunces-Italic 30 white, e.g. *Starry Night*.
- Line 2 at y=986: ExtraBold 18 UPPERCASE white 78%: `AFTER VINCENT VAN GOGH · PAINTED FOR CLEAR WEATHER`.
- Not focusable. It is a museum label, quiet on purpose.

### 6.8 Toast

- Centred horizontally, y=80, 520×64, radius 32, fill ink, text ExtraBold 24 yellow.
- Shows for 2.4s. In: rise 10px + fade 300ms. Out: fade 250ms. One at a time, never focusable.
- Copy examples: "Repainted for rain", "Opening Music…", "Sleep timer set for 10:11".

---

## 7. Zeus panel

Opened from Ask Zeus (OK), or with the ✱ key from anywhere on Home with nothing else focused. Back closes it.

### 7.1 Layout

- **Dim layer:** full screen, deep at 45%.
- **Panel:** x=1220, y=40, 660×1000, radius 44 (`sheet_r44.9.png`, blendColor #FBF7EC). Padding 40, vertical gap 20.
- **Header:** Zeus mark 72px, 18px gap, then "Zeus" (Fraunces-Title 52, ink) with "Your TV's assistant" below it (Bold 22, ink60). A close button on the right: 64px round with a 2px ink-20% ring and the × glyph tinted ink.
- **Today card:** height 150, radius 28 (`card_r28.9.png` as a mask or frame). It shows the current painting's thumbnail (`wall_<key>_thumb.jpg`) covered by the deep band gradient.
  - Text in white at a 26px inset: eyebrow "TODAY IN BARRIE" (ExtraBold 18), headline "8° and clear" (Fraunces-Title 40), sub "Showing Starry Night" (SemiBold 22).
- **Conversation:** fills the remaining height and is bottom-aligned. The newest message is at the bottom, and older ones clip off the top (keep the last 4).
  - Zeus bubbles: white, radius 28 (`bubble_r28.9.png`), padding 20/24, SemiBold 26 ink, max width 470, left-aligned.
  - User bubbles: yellow fill, Bold 26 ink, right-aligned.
  - New bubbles rise 10px + fade 350ms.
  - While Zeus is thinking, show a Zeus bubble with three ink dots that pulse in opacity in sequence (600ms loop).
- **Chips:** wrap row, gap 10, height 56, radius 28, 2px ink border, Bold 23 ink.
  - Focused chip: yellow fill, ink border, scale 1.04.
  - Default chips change with context, e.g. "Paint me something sunny", "What should I watch?", "Sleep in 30 min", "Switch to PlayStation".
- **Input bar:** height 76, radius 38, white.
  - Placeholder "Ask Zeus anything…" (SemiBold 25, ink at 55%). OK opens `StandardKeyboardDialog`.
  - On the right, an ink pill "Talk on your phone" (ExtraBold 21, yellow text). OK shows a card: "Open the Lumen app on your phone and hold the yellow button." If no phone has said hello this session (section 15.6), it adds a QR code to the APK download page instead.
- **Messages from the phone:** when Zeus is used by voice on the phone, the exchange appears here live. The panel opens automatically for 8s if it was closed, showing the user bubble with a small phone glyph and Zeus's reply, then closes again unless the user presses a key.

### 7.2 Panel motion

- **Open:** dim fades 0 to 1 over 250ms. The panel goes from x+60 and 0 opacity to its place at full opacity over 320ms, ease-out-cubic. Focus lands on the first chip.
- **Close:** the reverse in 220ms, ease-in. Focus returns to Ask Zeus (or to whatever had focus before ✱).

### 7.3 Zeus's voice

Zeus is warm, brief and specific. He uses the user's name (from Settings), one or two sentences, no emoji and no exclamation marks. He never invents what's on other apps. He's honest about limits.

Suggestion copy by theme (these are defaults when no habit data exists yet; habit-based ones replace them):

| Theme | Strip text | Action |
|---|---|---|
| starry | Clear and quiet tonight. Pick up Music where you left off? | Open Music |
| sunflower | Big sunny Saturday. Good day for Sports. | Open Sports |
| irises | Rainy afternoon. Good one for a movie on Streaming 2? | Open Streaming 2 |
| blossom | Grey outside, blossom in here. Want your usual? | Open <most used> |
| snow | Snow day. Sleep timer at 11 like last night? | Set timer |

Habit suggestions come from Lumen's own launch log only (section 10): "You usually open <app> around now." That needs at least 3 launches of the same app within ±45 min of the current time on the same weekday type (weekday or weekend) in the last 21 days.

---

## 8. Weather and theming engine

### 8.1 Data

- **Open-Meteo** needs no key. Forecast: `https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current=temperature_2m,weather_code,is_day&daily=sunrise,sunset&timezone=auto`
- **Location:** a city in Settings (default "Barrie, Ontario"), resolved once via `https://geocoding-api.open-meteo.com/v1/search?name=<city>&count=1`. Cache the lat/lon in the registry.
- Fetch on launch, then every 30 min (a `Timer` node), from a Task node.

### 8.2 Mapping (WMO weather_code)

| Codes | Day theme | Night theme |
|---|---|---|
| 0, 1 | sunflower | starry |
| 2, 3, 45, 48 | blossom | blossom (scrim +10% opacity at night) |
| 51–67, 80–82, 95–99 | irises | irises (scrim +10%) |
| 71–77, 85, 86 | snow | snow |
| Unknown or offline | last theme, or starry at night / sunflower by day |

Night = `is_day == 0`. Overrides from Zeus ("paint me something sunny") last until the next sunrise or sunset, or the next change of weather group, whichever comes first.

### 8.3 Repaint transition

- Load the next sharp, blur and thumb textures first, and wait for `loadStatus = "ready"`.
- Crossfade the new wallpaper pair 0 to 1 over 900ms, ease-in-out. Animate all deep-tinted nodes' `blendColor` from the old deep to the new deep over the same 900ms (`ColorFieldInterpolator`).
- Never repaint while a key was pressed in the last 3s. Defer it.
- Show the toast "Repainted for <weather>". Then unload the old textures.
- Max 6 nodes animating at once, so batch the blendColor changes into one interpolator per group where possible.

### 8.4 Paintings

`images/wall/`:
- `wall_<key>.jpg`: 1920×1080, q84.
- `wall_<key>_blur.jpg`: Gaussian 48.
- `wall_<key>_thumb.jpg`: 480×270.

Keys: starry, sunflower, irises, blossom, snow. All are original procedural paintings made by `scripts/paint.py`, not copies of real artworks. Don't swap in photos of real Van Gogh paintings.

---

## 9. Zeus brain (shared by TV and phone)

### 9.1 Architecture

```
                 ┌─────────────── home Wi-Fi ───────────────┐
 Phone app ──ECP (HTTP :8060)──> Roku TV ──(only while open)──> Lumen channel
   │   keypress / launch / query / input (?zeus=...)            │
   │                                                            │
   └──HTTPS──> Supabase Edge Function `zeus` ──> Claude API <──HTTPS── (TV chips + keyboard)
```

- **Two clients, one brain.** The phone (voice, mostly) and the TV (chips and keyboard on the Zeus panel) both call the same `zeus` function with the same contract.
- **Who executes actions:** the client that asked does.
  - The **phone** executes everything through ECP. It forwards UI-only actions (`set_theme`, `show_message`) to Lumen with `/input` when Lumen is open, or launches Lumen with them when it isn't.
  - The **TV** executes what it can locally (repaint, toast, launch through ECP to its own IP) and keeps chatting. A sleep timer requested from the TV is handed to the phone if one is connected (via the phone's next poll, see 15.6). Otherwise the TV runs it only while Lumen stays open and warns: "I'll turn the TV off at 10:52 if Lumen stays open. For a timer that always works, ask me from your phone."
- No API key lives in either client. Each install creates a random device token on first launch and sends it as `Authorization: Bearer <token>`. The edge function rate-limits per token (30 requests per 10 min).
- Model: `claude-haiku-5-5` for speed (swap in `claude-sonnet-5-5` if replies need more judgement). Max 300 output tokens. Use a single forced tool `respond`.

### 9.2 Request and response contract

Request (same shape from both clients; `source` tells Zeus where the user is):
```json
{
  "source": "phone_voice | tv_chip | tv_keyboard",
  "userName": "Aasees",
  "message": "open music on the tv and turn it off in half an hour",
  "context": {
    "localTime": "2026-10-27T22:22:00-04:00",
    "weather": {"tempC": 8, "code": 0, "isDay": false, "city": "Barrie"},
    "theme": "starry",
    "tv": {"isRokuTV": true, "power": "PowerOn", "activeApp": {"id": "dev", "name": "Lumen"}},
    "apps": [{"id": "12", "name": "Netflix"}, {"id": "837", "name": "YouTube"}],
    "inputs": [{"id": "tvinput.hdmi1", "name": "PlayStation"}],
    "sleepTimer": null,
    "recentLaunches": [{"appId": "12", "at": "2026-10-26T21:10:00-04:00"}],
    "history": [{"role": "zeus", "text": "..."}, {"role": "user", "text": "..."}]
  }
}
```

Response:
```json
{
  "reply": "Opening Music on the TV. It'll switch off at 10:52.",
  "actions": [
    {"type": "launch_app", "appId": "2213"},
    {"type": "set_sleep_timer", "minutes": 30}
  ],
  "chips": ["Paint me something sunny", "Switch to PlayStation", "What should I watch?"]
}
```

Allowed actions (both clients ignore anything else and anything with unknown IDs):

| type | fields | Phone does | TV does |
|---|---|---|---|
| `launch_app` | `appId` | ECP `/launch/<id>` | ECP to self, launching overlay |
| `switch_input` | `inputId` | ECP `/launch/tvinput.*` | same |
| `keypress` | `key` (one of: VolumeUp, VolumeDown, VolumeMute, Play, Rev, Fwd, Home, Back, InstantReplay), `repeat` (1 to 10) | ECP `/keypress/<key>` ×repeat, 120ms apart | same |
| `power` | `state` (on or off) | ECP `PowerOn` / `PowerOff` | `PowerOff` only |
| `set_theme` | `theme`, `until` | forwards to Lumen (15.6) | repaint (8.3) |
| `clear_theme_override` | none | forwards | back to weather theme |
| `set_sleep_timer` | `minutes` (5 to 240) | phone timer, then `PowerOff`, plus a notification 1 min before | hands to phone (see 9.1) |
| `cancel_sleep_timer` | none | cancels | forwards to phone |
| `open_lumen` | none | ECP `/launch/dev` | n/a |
| `show_message` | `text` | forwards to Lumen as a toast | toast |

**System prompt essentials:**
- Who Zeus is: the 7.3 voice.
- Hard limits: he can't see inside apps, can't change picture mode, and can't know what's playing beyond the active app's name.
- Only use IDs present in `context.apps` and `context.inputs`.
- `power: off` needs explicit words like "turn off" or "shut it off". Never infer it.
- Volume steps: "a bit" = 3, "a lot" = 8.
- Keep replies under 30 words, spoken-friendly (they may be read aloud on the phone), with no lists or emoji.
- Always return 2 to 4 short chips.

### 9.3 Offline and local intents

Both clients have a small local intent matcher (regex plus keyword lists), used first when there's no internet and as a fast path for obvious commands. That keeps "volume up", "pause", "open YouTube", "switch to HDMI 1", "turn off the TV" and "sleep in 30 minutes" instant (under 300ms) without a network round trip. Anything else goes to the backend.

### 9.4 Failure states

| Case | What the user sees |
|---|---|
| Offline | Local intents still work. Free text gets: "I can't reach the internet right now, but I can still open apps, change volume and set timers." |
| Timeout (8s) | "That took too long. Try again?" with a chip "Try again". |
| TV unreachable (phone) | The Zeus screen shows a banner "Can't see the TV. Is it plugged in and on this Wi-Fi?" Actions are queued for 60s and run if the TV reappears. |

## 10. Data and persistence

`roRegistrySection("lumen")` keys:
- `userName`
- `city`, `lat`, `lon`
- `units` (C or F)
- `clock24` (true or false)
- `homeApps` (JSON array of app IDs, in order)
- `inputNames` (JSON map)
- `hiddenInputs`
- `deviceToken`
- `phoneSeen` ({name, at}: the last phone hello, see 15.6)
- `launchLog` (JSON, last 200 launches as {appId, ts}, trimmed FIFO)
- `themeOverride` ({theme, until})
- `motion` (full or reduced)
- `onboarded` (bool)

Write the registry sparingly (after changes, not on every keypress) and `Flush()` after each write batch.

---

## 11. Focus, navigation and remote

**Exactly one element is focused at all times.** Focus is the loudest thing on screen: the yellow ring plus glow on tiles, and yellow fill on chips and pills.

Focusable elements on Home and how the D-pad moves between them:

```
                         [Ask Zeus] [⚙]
                              ▲
[ Suggestion strip: action button ] [×]
                              ▲▼
[T1] [T2] [T3] [T4] ... [Add app]          ◀ ▶ move within the row, no wrap
                              ▲▼
[Input pills...] | [Sleep] [Picture] [Edit] [Off]
```

- Moving up or down between rows picks the item nearest by x centre. Each row remembers its last focused item.
- From the app row, up goes to the suggestion strip if it's visible, otherwise to Ask Zeus.

Remote keys on Home:

| Key | Action |
|---|---|
| OK | Activate the focused item (open app, switch input, run the action) |
| ✱ (options) | On a tile: options sheet (Move, Rename (inputs only), Hide from Home, App info). With no options available: open Zeus. |
| Back | Close the topmost overlay. On bare Home: toast "You're home". |
| Play/Pause | On a tile: open it (same as OK) |
| Instant Replay | Repeat Zeus's last suggestion |

Inside the Zeus panel: up and down move between the conversation (read-only), the chips and the input bar. Left and right move within the chips. Back closes.

Pressed state: on OK, scale ×0.96 over 100ms, then back over 120ms with out-back easing.

---

## 12. Motion spec

| Event | Property | From → To | ms | Easing |
|---|---|---|---|---|
| Focus leaves tile | scale, y, ring+glow opacity | 1.10, -10, 1 → 1.0, 0, 0 | 160 | outCubic |
| Focus enters tile | scale, y, ring+glow opacity | 1.0, 0, 0 → 1.10, -10, 1 | 220 | outBack (overshoot ~1.3) |
| Tile name | opacity, y | 0, +6 → 1, 0 (60ms delay) | 200 | outCubic |
| Row slide | x | x → x - 324 | 240 | inOutCubic |
| Press | scale | ×1 → ×0.96 → ×1 | 100 + 120 | inCubic / outBack |
| Suggestion strip in | opacity, y | 0, +10 → 1, 0 | 350 | outCubic |
| Zeus open | dim opacity; panel x, opacity | 0 → 1; +60, 0 → 0, 1 | 250; 320 | linear; outCubic |
| Zeus close | reverse | | 220 | inCubic |
| Chat bubble in | opacity, y | 0, +10 → 1, 0 | 350 | outCubic |
| Repaint | wallpaper opacity, deep blendColor | old → new | 900 | inOutCubic |
| Toast | opacity, y | 0, +10 → 1, 0 | 300 in / 250 out | outCubic / inCubic |
| Ambient clock drift | translation | random ±120px | 60000 | inOutCubic |

When Motion is set to Reduced: drop all scale, translation and overshoot, and keep opacity fades only (150ms).

---

## 13. Other screens (same visual language)

All other screens use the current painting behind a deep-tinted full scrim (deep at 55%). Panels are paper (#FBF7EC) with ink text and r44 corners, like the Zeus panel. Primary buttons are ink with yellow text. Focus is a yellow fill or the yellow ring.

1. **Launching overlay:** a centred paper card 640×420 r44 with the app icon in its painted frame (scaled to 1.25×), "Opening Music…" in Fraunces-Title 40, and a 400×8 ink track with a yellow shimmer sliding across (1.2s loop). Back cancels.
2. **Launch error:** the same card plus "Couldn't open Music" and the one-line reason, with buttons [Try again] (ink, focused) and [Dismiss] (outline).
3. **Options sheet (✱):** a right sheet in the Zeus panel style, 560 wide. Items are 84px rows with Bold 30 text; the focused row gets a yellow fill.
4. **Edit Home:** the lifted tile goes to scale 1.14 with a stronger shadow, and its ring pulses in opacity (1 to .6, 800ms loop). Left and right swap it with its neighbours; OK drops it; Back cancels. A hint pill at the bottom centre reads "◀ ▶ Move · OK Done · Back Cancel", ink with yellow key caps.
5. **App picker:** "Choose apps for Home" in Fraunces-Title 56 over a 5-column grid of framed app tiles. Apps already on Home get a yellow check badge (52px yellow disc with an ink check). The Done button is pinned top-right.
6. **Settings:** two panes on paper. The left list (Name, Wallpaper, Zeus, Clock & weather, Inputs, Motion, Startup, About) has a yellow fill on the focused row. Steppers are `◀ value ▶` rows changed with left and right.
   - **Wallpaper:** "Follow the weather" (default) or pin one of the five paintings, shown as thumbnails in painted frames.
   - **Zeus:** your name, and suggestions on or off.
   - **Startup:** explains that the Lumen phone app opens Lumen when the TV turns on, shows "Phone connected: <name>, last seen <time>" or "No phone yet" with a QR code to the APK download page, and lists the two TV settings it needs (Control by mobile apps: Enabled or Permissive, and Fast TV start: on).
7. **Ambient (after 5 min idle):** the UI fades out and the painting slowly pans (scale 1.0 to 1.06 over 60s, ping-pong). A small paper card holds the clock (Fraunces-Display 120), date and weather, and drifts every minute for burn-in protection. Any key wakes Lumen without triggering that key's action.
8. **Onboarding (3 steps):** paper cards centred on the painting.
   - Step 1: "Your TV, calmer." with the Lumen logo.
   - Step 2: "What should Zeus call you?" (keyboard).
   - Step 3: pick apps (reuses the App picker).
   - Done: "Zeus will repaint your screen with the weather." Progress dots are yellow pills, and Continue (ink, yellow text) has focus by default.
9. **Empty, error and edge states:**
   - No apps yet: one big paper "Add your first app" tile.
   - Offline: the weather line reads "Offline · last painting kept", and Zeus works in local mode.
   - Long names: marquee on focus.
   - Streaming stick: the reflow described in 6.6.

---

## 14. SceneGraph architecture (suggested)

```
source/main.brs                  roSGScreen, deep-link args, Main loop
components/
  MainScene.xml/.brs             owns layers, key routing, overlay stack
  theme/ThemeManager.xml         interface: theme, deep (color), override; runs repaint anims
  wall/WallpaperLayer.xml        two sharp+blur pairs, crossfade
  home/ClockBlock.xml            date, clock (Timer 1s aligned to minute), weather line
  home/ZeusButton.xml
  home/SuggestionStrip.xml
  home/AppRow.xml                custom row (Group + Posters), focus ring/glow, slide anim
  home/AppTile.xml               frame or Lumen tile, name label
  home/BottomBar.xml             input pills + quick actions
  home/PaintingCredit.xml
  zeus/ZeusPanel.xml             header, TodayCard, ChatList, Chips, InputBar
  zeus/ZeusTask.xml              Task: calls backend, returns {reply, actions, chips}
  zeus/PhoneBridge.brs           handles roInput events from the phone (?zeus=, ?theme=, ?hello=)
  zeus/ActionRunner.brs          validates + executes actions
  ecp/EcpTask.xml                Task: query apps/icons, launch, keypress
  weather/WeatherTask.xml        Task: Open-Meteo fetch + mapping
  common/Toast.xml, FrostPill.xml (9-patch + stroke), Chip.xml
  screens/ Launching, LaunchError, OptionsSheet, EditHome, AppPicker, Settings*, Ambient, Onboarding*
fonts/ images/ (from this pack)
manifest
```

`manifest` essentials:
```
title=Lumen
major_version=1
minor_version=0
build_version=1
ui_resolutions=fhd
mm_icon_focus_fhd=pkg:/images/brand/poster_fhd_540x405.png
mm_icon_focus_hd=pkg:/images/brand/poster_hd_290x218.png
splash_screen_fhd=pkg:/images/brand/splash_fhd.png
splash_color=#141844
splash_min_time=600
```

Performance budget:
- At most 3 full-screen JPGs decoded at once (current sharp, current blur, plus the next one during a crossfade).
- Free the old textures by clearing `uri` after the fade.
- Tiles total under 40 posters.
- No more than 6 simultaneous Animation nodes.
- Every network call in a Task node, never on the render thread.

---

## 15. Lumen phone app (Android)

### 15.1 What it does

1. **Auto-open:** opens Lumen on the TV every time the TV turns on.
2. **Remote:** a full Roku remote over Wi-Fi: D-pad, OK, Back, Home, Options, media, volume, power and typing.
3. **Zeus by voice:** hold to talk, with an optional "Hey Zeus" wake word while the app is open.
4. **Apps and inputs:** tap to open any installed app or HDMI input. Tap a painting to repaint the TV.
5. **Timers:** the sleep timer lives here, so it works even when you've left Lumen.

### 15.2 Tech stack

- Kotlin, Jetpack Compose, Material 3 as the base with a fully custom theme. minSdk 26, targetSdk the current stable.
- Networking: OkHttp for ECP (plain HTTP on the LAN) and for the backend (HTTPS). Parse ECP XML with `XmlPullParser`.
- Discovery: SSDP M-SEARCH to `239.255.255.250:1900` with `ST: roku:ecp`. Hold a `WifiManager.MulticastLock` during the search. Allow a manual IP entry as a fallback.
- Background work: one foreground service, `TvWatcherService`, type `connectedDevice`, using the `CHANGE_WIFI_MULTICAST_STATE` permission (plus `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`). It starts on boot (`RECEIVE_BOOT_COMPLETED`) and when the phone joins the home Wi-Fi.
- Local network: on Android versions that require a runtime local-network permission, request it during onboarding with a plain-language reason.
- Speech: `SpeechRecognizer` with `EXTRA_PARTIAL_RESULTS` (live transcript) and `EXTRA_PREFER_OFFLINE`. `RECORD_AUDIO` is requested only on the first mic press.
- Spoken replies (optional, on by default): `TextToSpeech`, with en-CA voice if available, rate 1.0.
- Wake word (optional, off by default, foreground only): Picovoice Porcupine with a custom "Hey Zeus" keyword. The AccessKey is entered in Setup and never hardcoded.
- Storage: DataStore (preferences) for settings, the device token, the TV IP and serial, and input names.
- Fonts: bundle the same `Fraunces-*.ttf` and `Manrope-*.ttf` in `res/font`.

### 15.3 ECP cheat sheet (phone to TV, `http://<tv-ip>:8060`)

| Need | Call |
|---|---|
| Power state, model, name, is it a TV | `GET /query/device-info` → `power-mode` (PowerOn, DisplayOff, Ready, Headless), `user-device-name`, `is-tv`, `serial-number` |
| What's on screen | `GET /query/active-app` (Roku's home screen shows as `<app>Roku</app>` with no id) |
| Installed apps | `GET /query/apps` |
| App icon | `GET /query/icon/<id>` |
| Open an app or input | `POST /launch/<id>` (Lumen sideloaded = `dev`), `POST /launch/tvinput.hdmi1` |
| Open Lumen with a message | `POST /launch/dev?zeus=<urlencoded json>` |
| Send to Lumen while it's open | `POST /input?zeus=<urlencoded json>` |
| Buttons | `POST /keypress/<Key>`, plus `/keydown/<Key>` and `/keyup/<Key>` for press-and-hold |
| Typing | `POST /keypress/Lit_<urlencoded char>` per character |
| Power | `POST /keypress/PowerOn`, `/keypress/PowerOff` |

Keys used: Up, Down, Left, Right, Select, Back, Home, Info (the ✱ button), Play, Rev, Fwd, InstantReplay, VolumeUp, VolumeDown, VolumeMute, Backspace, PowerOn, PowerOff.

### 15.4 Auto-open logic (`TvWatcherService`)

```
on home Wi-Fi && TV known:
  poll GET /query/device-info every 3s (every 10s after 30 min of DisplayOff, back to 3s on phone unlock)
  if power-mode goes DisplayOff|Ready|Headless → PowerOn:
      for up to 20s, every 500ms: GET /query/active-app
         if active app is Roku home  → POST /launch/dev ; log "auto-opened in Xs" ; stop
         if active app is anything else (user turned the TV on into an app, or a voice launch) → do nothing ; stop
  if poll fails 3 times → mark TV unreachable, back off to 15s
not on home Wi-Fi → stop polling, keep the service idle (no battery cost)
```

- Only one auto-open per power-on.
- A "Pause auto-open for 2 hours" action in the notification for when guests want the normal Roku screen.
- Fast TV start must be on, otherwise the TV's network sleeps and no phone can see it turn on. Setup checks this indirectly: if the TV answers ECP while `power-mode` isn't PowerOn, fast start is on.
- "Control by mobile apps" must allow launches. Setup checks it by calling `/query/active-app`: a 403 or refusal means it's off. Setup then shows the exact path: Settings > System > Advanced system settings > Control by mobile apps > Network access > Enabled (or Permissive).

### 15.5 Phone UI (412dp design width, see `reference/phone/`)

Same language as the TV: the current painting, deep-colour scrims, white text on paintings, paper (#FBF7EC) sheets with ink text, yellow #FFDE03 as the only accent, Fraunces for display and Manrope for UI. The phone computes the same theme as the TV (same weather call, same mapping, same override, received from the TV hello or set from the phone) so both screens always show the same painting.

**Shared frame:**
- The background is the painting's blurred version at 55% plus the sharp painting in the top 330dp, fading out, covered by a deep gradient (35% at the top, 92% at the bottom).
- The bottom tab bar is 16dp from the edges, 68dp tall, radius 34, deep at 55% frosted. Its four tabs are Remote, Apps, Zeus and Setup (icon 24 plus ExtraBold 12). The active tab is yellow.
- Edge-to-edge drawing, with content respecting system bar insets. No fake status bar.

**Remote tab (default):**
- Header: eyebrow "ROKU TV" (ExtraBold 12, tracking .16em), the TV name in Fraunces 28, and a status dot plus "On · Lumen open" (Bold 13; green #7EE2A8 when on, grey when off). A 52dp frosted power button sits top-right; a long press shows "Turn TV off?".
- Painting line: *Starry Night · 8° clear in Barrie* (Fraunces Italic 15).
- **D-pad:** a 280dp paper disc with a soft shadow and ink chevrons (30dp, 3dp stroke) in four 72dp hit areas.
  - The centre is a 112dp yellow OK button with a 3dp ink outline and a dashed ink ring (the Zeus star again) around "OK" (ExtraBold 20).
  - Taps send keypress. Swipes across the disc send the arrow for the swipe direction. Holding an arrow repeats every 150ms after 400ms.
  - Haptic tick on every press.
- **Nav row:** four 56dp frosted round buttons with 12dp labels: Back, Home, Options (✱), Type.
  - Type opens the keyboard and streams `Lit_` keypresses as you type, with Backspace on delete.
- **Media and volume row:**
  - A frosted pill holding Rewind, Play/Pause (a white 56×44 pill with an ink glyph) and Fast-forward.
  - A volume pill: − VOL +, where holding repeats.
  - A mute button.
  - Volume and mute are hidden if `is-tv` is false.
- **Zeus button:** full width minus 40, 84dp tall, radius 42, yellow, with a 2dp ink outline. It holds the Zeus mark (64, ink and yellow inverted), "Hold to talk to Zeus" (ExtraBold 19) above "or say 'Hey Zeus'" (Bold 13 at 70%), and a 52dp ink mic circle on the right. Pressing it opens the Zeus tab already listening.

**Zeus tab:**
- A paper background with a 250dp painting header band (deep gradient). In it: "TALKING TO LIVING ROOM TV" and a Connected pill.
- In the centre of the band, the Zeus mark (96) inside three dashed yellow rings. While listening, the rings pulse outward (scale 1 to 1.15, opacity to 0, staggered 300ms, 1.2s loop) and their brightness follows mic volume.
- **Listening state:**
  - Eyebrow "LISTENING".
  - The live transcript in Fraunces 30, ink, line height 1.2. Unconfirmed words show at 35% ink.
  - Bottom button: ink, 64dp, with yellow waveform bars and "Release to send". A drag left cancels.
- **Reply state:**
  - A Zeus bubble (white, radius 22/22/22/6, SemiBold 16) with the mark at 32dp.
  - Action receipts under it as small white chips, each with a green check: "Music opened on TV", "Sleep timer 10:52".
  - Zeus also speaks the reply if spoken replies are on.
- **Try chips:** 40dp tall with a 2dp ink outline, filled from `chips` in the last reply.
- **Not connected:** a yellow banner "Can't see the TV" with a Retry button.

**Apps tab:**
- Title "Apps & inputs" in Fraunces 34, with "Tap to open on the TV" below.
- A 3-column grid of real installed apps: each app icon sits in its painted frame (`frame_<pastel>.png`, same hash as the TV). Name in ExtraBold 13. The last cell is a dashed "Add app" that opens the TV's App picker in Lumen.
- Long-press on a tile: "Pin to Lumen home", "Hide", "Rename" (inputs only).
- Inputs: frosted pills (HDMI glyph plus custom name).
- Repaint the TV: a horizontal strip of the 5 painting thumbnails (96×56dp, radius 14). The active one gets a yellow ring with an ink outline. Tapping one sends `set_theme` until sunset. A caption reads "Following the weather · tap a painting to override until sunset".

**Setup tab:**
- A paper sheet. TV card: TV glyph tile, the TV name, its IP with "found on your Wi-Fi", and "Connected" in green.
- Toggles (yellow when on, with an ink-outlined knob):
  - Open Lumen when the TV turns on
  - Hey Zeus
  - Remote on lock screen
  - Spoken replies
- Checks list (green check or amber !):
  - Control by mobile apps
  - Fast TV start
  - Lumen installed (`dev` in `/query/apps`)
  - Battery: allow background use. Tapping it opens the battery optimisation exemption screen.
- A yellow footer card: "Last auto-open: today at 7:02 pm, 1.8 s after the TV turned on."
- Below it: your name (sent to Zeus), city, °C or °F, and the Porcupine key field.

**Onboarding (first run, 4 cards on the painting):**
1. "Your TV, calmer."
2. "Finding your Roku…" (SSDP, with a list if there are several TVs, and manual IP as a fallback).
3. "Two TV settings", with illustrated steps for Control by mobile apps and Fast TV start. A "Check again" button re-runs the checks.
4. "Let Lumen open itself": asks for the notification permission and battery exemption, explaining why in one line each.

**Persistent notification** (required by the foreground service, so make it useful):
- Small icon: the Zeus mark. Title "Lumen", text "Watching for Living Room TV" or "TV on · Lumen open".
- Actions: [Talk to Zeus] [Play/Pause] [Pause auto-open].
- With "Remote on lock screen" on, use a custom compact layout with ◀ ▶ OK-style volume and play buttons.

**Extras:**
- A Quick Settings tile "Zeus" that opens straight into listening.
- An optional 2×2 home-screen widget with the painting thumbnail, the Zeus button and play/pause.

### 15.6 Phone ↔ TV bridge

- **Hello:** when the phone connects (and every 60s while Lumen is open), it sends `POST /input?hello=<urlencoded json {"phone":"Pixel 8","v":1}>`. Lumen stores `phoneSeen` and shows "Phone connected" in Settings > Startup.
- **Theme sync:**
  - Both apps compute the weather theme the same way, from the same Open-Meteo call and the mapping in 8.2, so they agree without talking.
  - Overrides chosen on the phone (painting strip, or Zeus by voice) are sent as `POST /input?theme=<json {"theme":"sunflower","until":"<iso>"}>`, or with `/launch/dev?theme=...` if Lumen isn't open.
  - Overrides chosen on the TV can't reach the phone (a channel can't call out to a phone). The phone's painting strip may show "Following the weather" until the override ends. That's accepted for v1.
- **Zeus messages:** after each voice exchange, the phone sends `?zeus=<urlencoded json {"user":"...","reply":"...","receipts":["Music opened on TV"]}>` so the TV panel shows it (7.1). Keep the JSON under 1,500 characters and trim the history.
- **Timer handoff from the TV (phase 2):**
  - Linking: the TV shows a 6-digit home code in Settings > Startup, and the user types it once in the phone's Setup. The backend links the two device tokens.
  - After that, a sleep timer asked for on the TV is stored by the backend (`zeus_tasks` table). The phone picks it up by polling `GET /zeus-tasks` every 30s while on home Wi-Fi and runs it.
  - Until phase 2 ships, the TV runs the timer itself and shows the warning described in 9.1.
- Lumen's `PhoneBridge.brs` reads `roInput` events (and `args` on launch) for the `zeus`, `theme` and `hello` keys and ignores anything else.

### 15.7 Phone project layout

```
android/
  app/src/main/java/app/lumen/
    MainActivity.kt
    ui/theme/ (Color.kt, Type.kt with Fraunces and Manrope, Shapes.kt)
    ui/remote/RemoteScreen.kt  (DPad.kt, NavRow.kt, MediaRow.kt, ZeusButton.kt)
    ui/zeus/ZeusScreen.kt      (ListeningRings.kt, Transcript.kt, Bubble.kt, ReceiptChip.kt)
    ui/apps/AppsScreen.kt      (FramedAppTile.kt, InputPill.kt, PaintingStrip.kt)
    ui/setup/SetupScreen.kt, ui/onboarding/*
    ecp/EcpClient.kt, ecp/SsdpDiscovery.kt, ecp/Models.kt
    watcher/TvWatcherService.kt, watcher/BootReceiver.kt, watcher/WifiMonitor.kt
    zeus/ZeusClient.kt, zeus/LocalIntents.kt, zeus/ActionRunner.kt, zeus/SpeechController.kt, zeus/WakeWord.kt
    theme/WeatherRepo.kt, theme/PaintingTheme.kt (same 5 themes and deep colours as section 3.2)
    timer/SleepTimer.kt (AlarmManager exact alarm, plus a notification 1 min before)
  app/src/main/res/font/ (TTFs from fonts/), res/drawable/ (wall thumbs, frames, zeus mark)
```

## 16. Asset pack contents

```
fonts/                     6 static TTFs (see 4)
images/wall/               wall_{starry,sunflower,irises,blossom,snow}{,_blur,_thumb}.jpg
images/tiles/              tile_<app>.png (288x192), tile_<app>_focus.png (317x211), frame_<pastel>.png (288x192 with window)
images/ui/                 pill_64.9.png, pill_64_stroke.9.png, pill_88.9.png, sheet_r44.9.png, bubble_r28.9.png, card_r28.9.png,
                           focus_ring_tile.png, focus_glow_tile.png, tile_shadow.png,
                           scrim_bottom.png, scrim_topleft.png (draw at 1920x1080, blendColor = deep), blur_mask.png,
                           ic_*.png (white 64px glyphs)
images/brand/              zeus_mark_{48,60,72,120,168}.png, zeus_mark_cream_120.png, lumen_logo_ink.png,
                           poster_fhd_540x405.png, poster_hd_290x218.png, splash_fhd.png
reference/                 TV screenshots + home_reference.html (browser mockup, open locally)
reference/phone/           phone tab screenshots (remote, zeus, apps, setup) + their HTML source
scripts/                   paint.py (wallpapers), tiles.py (tile textures), frames.py, build_assets.py, render_pngs.js
```

All the 9-patch files are white so one file serves every tint through `blendColor`. Stretch guides are the standard 1px black border.

---

## 17. Acceptance checklist

- [ ] At 3m on a 55" TV, the focused tile is obvious in under a second in all 5 themes.
- [ ] All home text passes 4.5:1 against its scrim in all 5 themes (check the sunflower sky and snow areas especially).
- [ ] The repaint crossfade is smooth (no flash or black frame) on a budget Roku TV (e.g. TCL 4-series).
- [ ] Focus moves respond in under 100ms. Anim durations match section 12.
- [ ] Zeus round trip is under 3s on normal Wi-Fi. Offline mode works for local actions.
- [ ] No API key in the package (`grep -ri "sk-" .` returns nothing).
- [ ] No third-party logo is recoloured or redrawn. Real apps show their own icon in a painted frame.
- [ ] Every screen reachable from Home in 3 D-pad presses or fewer.
- [ ] Back always goes up one level. Nothing traps focus.
- [ ] The streaming-stick reflow works (inputs, Picture and Off hidden).
- [ ] No ads, promos or "featured" rows anywhere.
- [ ] Phone: TV off → on opens Lumen within 3s, 10 times out of 10, and never hijacks the TV when it was turned on into another app.
- [ ] Phone: the remote's D-pad round trip is under 150ms on home Wi-Fi. Press-and-hold repeat works. Haptics on every press.
- [ ] Phone: "Hold to talk" shows the live transcript within 500ms of speaking. Local intents (volume, pause, open app) run in under 300ms.
- [ ] Phone: the sleep timer still powers the TV off when the user is inside another app.
- [ ] Phone: the battery cost of the watcher is under 2% per day (check in Android battery stats over 24h).
- [ ] The phone and TV always show the same painting.
- [ ] No API key in the APK either (decompile check with `apktool` and grep).
