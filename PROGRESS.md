# Lumen progress

## M1 + M2: Roku home screen (running on the Hisense TV, build 1.0.9)

**Verified on the TV** with Roku dev screenshots and the debug console, 27 Oct 2026:
- The Starry Night painting is sharp at the top and fades smoothly into blur behind the apps, with deep scrims.
- Date eyebrow and the 176px Fraunces clock (6.2). The weather line stays empty until M5.
- Your real apps come from the TV (Prime Video, Netflix, Spotify, Backdrops, YouTube), each with its own icon in a pastel painted frame. The focused tile gets the yellow ring, glow and scale, with its name below. The Add app tile is last, and tiles fade near the right edge.
- Ask Zeus pill and gear (6.3); the input pills (three HDMI inputs all named "PlayStation 5" by the TV), divider and four round buttons (6.6); the painting credit (6.7).
- **OK opens the app.** Prime Video opened through the bridge in about 1 second.
- 27 scripted key presses across every zone and both ends of the row, with no errors.

**Architecture change:** Roku OS 16 blocks ECP from a channel to its own TV (403 even in Permissive). Lumen now goes through the **Lumen bridge** (PLAN.md section 7). For now that's `bridge/lumen_bridge.py` on the Mac; in M4 it moves into the phone app.

**Placeholders until later milestones:** Ask Zeus (M6), Settings (M7), Add app and tile options (M7), Sleep timer (M6), Edit Home (M7).

### Running it

1. **Start the bridge on the Mac:**
   ```
   python3 bridge/lumen_bridge.py --tv 192.168.2.33
   ```
   Allow incoming connections if macOS asks. While Lumen is on screen, the bridge prints "told Lumen about http://192.168.2.21:8977".
2. **Build:** `sh roku/build.sh` creates `roku/out/lumen.zip`.
3. **Install:** sign in at `http://192.168.2.33` (user `rokudev`), Upload → `lumen.zip` → Replace with zip. Claude can do this through Claude in Chrome once you're signed in. `roku/deploy.sh` also installs from the command line if the password is saved in Keychain (service `lumen-roku`).
4. **Debug console:** `nc -d 192.168.2.33 8085`.
5. **Screenshot:** Utilities → Screenshot on the dev page, or `roku/screenshot.sh`.

**Not yet done:**
- The bridge only runs while it's started by hand. If the Mac sleeps or the helper stops, tiles can't open apps until it comes back.
- The three inputs all show "PlayStation 5" because that's what the TV calls them. Renaming arrives with tile options (M7).

## Next: M3 (Android remote) + M4 (phone hosts the bridge)
- M3: SSDP discovery, EcpClient, and the Remote tab matched to `assets/reference/phone/phone_remote.png`.
- M4: TvWatcherService auto-open, plus the bridge endpoints and announcement from PLAN.md section 7, so the Mac helper can be retired.

## Additions after M2 (owner requests, 28 Oct)
- **Logos on painted cards** (PLAN 6.19) with larger logos; Add app is a cream painted card with an ink plus.
- **Games card** (top-right, above the row): two original games play *inside* the card. **Firefly** is a one-button flier through cypress gaps; **Wheatfield Run** is a runner jumping haystacks. ◀ ▶ switches, OK starts / flaps / jumps, Back or moving away stops. Best scores are saved in the registry (`best_firefly`, `best_runner`). Reach it with Up from the right half of the app row, or Down from Ask Zeus.
- **Yellow liquid glass:** every yellow state (Ask Zeus, focused pills, focused Games card) is yellow at 86% plus a white rim, top sheen and bottom glow overlay (`glass_pill_64.9.png`, `glass_card_r28.9.png` from `roku/tools/gen_glass.py`). There's no live blur on Roku.

## Build 21 (28 Oct)
- **Games panel** spans x 583 to 1824 (1241 px). Games run on a 1210x342 stage: **Flutter** (red bird, rounded painted columns, Starry Night), **Pup Run** (beagle pup, Van Gogh sunflower field), **Star Catcher**. Frames are preloaded and toggled, obstacles pooled, backgrounds scrolled by Animation nodes. Uri swaps every frame had caused the flicker.
- **Focus** is white liquid glass; yellow is only the Zeus star, the tile focus ring and the New best badge. Ask Zeus and Settings sit on a near-solid deep pill; the date sits on a dark frosted pill.
- **Logos** are ink-black, except YouTube's red badge (`lumen-img key … mono|mono-red`). Icons are upscaled 4x before keying and output at 2x, so edges are smooth.
- **Memories** is the first card. Empty: a painted card with picture glyph and *Memories*. With photos: a rounded full-card slideshow (`/memories/<name>?card=1`), and OK opens a full-screen slideshow. Photos come from `~/Pictures/Lumen Memories` via the Mac helper for now; the phone app takes this over later.
- **Home behaviour** (helper `watcher()`): backing or Home-ing out of an app onto Roku's home reopens Lumen. Home pressed while in Lumen stays on Roku's home until another app opens. Roku never reports a held Home, so "press Home twice" replaces press-and-hold. Verified: Backdrops → Home → Lumen back within ~1 s; Home in Lumen → stays on Roku home.

## M3 + M4 + M6: Android app (28 Oct, built on the Mac with `android/gradlew`)
- `android/`: Kotlin + Compose app `app.lumen` with five tabs: Remote, Apps, Zeus, Games and Setup. See `android/README.md`.
- **Accounts (Firebase Auth):** sign up with name, email, password and confirm password, or Google. The name greets you on the TV and shows on the leaderboard.
- **Phone hosts the bridge** (PLAN 7) on :8977, with the same contract as `bridge/lumen_bridge.py`:
  - icons keyed in Kotlin (port of `lumen-img key`), Memories from photos picked on the phone, the sleep timer, `/zeus`;
  - new: `POST /score` and `GET /leaderboard/<game>`.
- **Watcher** (`TvWatcherService`): a port of the Mac helper's `watcher()`. It opens Lumen on power-on, returns to Lumen when you back out of an app, keeps Roku home after Home in Lumen, has a "Pause auto-open for 2 hours" notification action, and announces the bridge with `?bridge=&phone=&theme=`.
- **Zeus** uses Gemini through Firebase AI Logic (no key in the APK). Local intents handle volume, play/pause, open app, inputs, power and sleep instantly. Replies can be spoken and are mirrored to the TV's Zeus panel.
- **TV (build 31):**
  - Games send each finished run to the bridge, and the TV toasts your leaderboard rank.
  - Lumen accepts `?theme=` (the phone repaints the TV, or follows the weather) and `?zeus=` (phone exchanges shown in the Zeus panel).
- **Tests:** 25 JVM unit tests in `android/app/src/test`, all passing. They were run with kotlinc + JUnit, because Google's Maven host is blocked in the cloud session, so Gradle couldn't run there. `bsc` lint on `roku/` reports no new issues.

### Follow-up (28 Oct, owner feedback)
- **Zeus failing:**
  - Gemini 2.5's thinking was using up the 400-token budget, so answers came back empty. The budget is now 2048.
  - Models fall back in order: 3.8 Flash → flash-latest → 3.5 Flash-Lite → 3 Flash preview → 2.5 Flash. Google only serves 2.5 to projects that used it before, so a new Firebase project needs the 3.x models.
  - Answers wrapped in ```json fences or plain text are accepted.
  - **Setup → Check Zeus** shows the real error.
- **Zeus on both sides:**
  - One shared conversation (`ZeusLog`). Phone exchanges open the TV's Zeus panel for 8 s (any key keeps it open).
  - The TV's questions appear on the phone with an "On the TV" tag.
- **"Hey Zeus":** on-device speech recognition with a sound-alike-tolerant wake phrase, while the app is in the foreground. It keeps the screen on and pauses while Zeus is listening or talking.
- **Widget:** 2x2, with the painting, Zeus and play/pause.
- **Timer handoff (15.6):** the phone is the bridge, so the TV's sleep sheet and Zeus already set the phone's timer through `/sleep`. The phone now reports changes back with `/input?sleep=`. No backend linking is needed.
- **Accounts:**
  - The name no longer shows as the email prefix after sign-up.
  - Google sign-in uses the Google name, has a bottom-sheet fallback, and gives clear SHA-1 / config errors.
  - The official multicolour Google "G" is on the button.
  - Onboarding asks "What should Zeus call you?"
- **Keyboard:** a "keyboard down" button floats above the keyboard. The remote's Type box has arrow keys and OK.
- **QR pairing:** the TV's hello card shows `lumen://connect?ip=…&name=…` (image from api.qrserver.com). "Find your Roku" has **Scan QR on the TV** (Google code scanner, no camera permission).
- **Tests:** 34 JVM tests, all passing. `bsc` reports no new TV issues.
