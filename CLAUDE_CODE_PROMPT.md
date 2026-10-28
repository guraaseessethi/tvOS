Build **Lumen**: a Roku TV home screen in Van Gogh's style, an Android phone app that opens it automatically and works as the remote, and **Zeus**, the AI assistant both of them talk to.

Everything you need is in this folder:
- `LUMEN_DESIGN_SPEC.md` is the full design and behaviour spec for all three parts. Read all of it before writing any code. It is the source of truth.
- `reference/` has the finished TV look (screenshots plus `home_reference.html`, a working browser mockup). `reference/phone/` has the four phone tabs as screenshots plus HTML.
- `images/` and `fonts/` hold every production asset, already sized, 9-patched and converted to static TTFs. Use them as they are for both the Roku channel and the Android app.
- `scripts/` holds the generators, if any asset is missing.

## The product in short

**The TV (Roku channel):**
- A full-screen original painting in Van Gogh's swirling style that changes with the live weather: Starry Night for clear nights, Sunflower Field for sun, Irises for rain, Almond Blossom for cloud, Snowy Evening for snow.
- A big clock, installed apps in painted pastel frames with a yellow focus ring, and inputs and quick actions.
- A cream "letter" Zeus panel.
- Colours: ImpulsTrip yellow #FFDE03 is the only accent, with ink #1C1C1C. Fraunces for display text and Manrope for UI.

**The phone (Android APK):**
- **Auto-open:** a foreground service watches the TV over home Wi-Fi with ECP. When the TV turns on and reaches Roku's home screen, the phone launches Lumen within about 2 seconds.
- **Remote:** a full remote in the same painted style: a paper D-pad with a yellow star OK button, nav, media and volume buttons, and typing.
- **Zeus by voice:** a big yellow "Hold to talk to Zeus" button. Zeus answers, takes actions on the TV through ECP, and pushes the conversation to the TV's Zeus panel.
- **Sleep timer:** it lives on the phone, so it works even after you leave Lumen.

**Zeus (backend):**
- A Supabase Edge Function that calls the Claude API (`claude-haiku-5-5`) with one forced `respond` tool.
- It returns `{reply, actions, chips}` from a fixed action list (spec section 9).
- The API key lives only here.

## Repo layout

```
lumen/
  roku/        SceneGraph channel (manifest, source/, components/, images/, fonts/)
  android/     Kotlin + Jetpack Compose app
  backend/     supabase/functions/zeus (+ zeus-tasks later), SQL migrations
  assets/      this pack, untouched
  PLAN.md  PROGRESS.md
```

## How to work

1. First, write `PLAN.md`. Cover the component trees for the Roku channel and the Android app, the backend contract, and the build order, all mapped to spec sections. Flag anything in the spec you think won't work on real hardware or current Android versions, with your proposed fix. Then continue without waiting.
2. Build in this order. Each milestone must run on its own, and each ends with a note in `PROGRESS.md` covering what works, what doesn't, and exact test steps (Roku sideload steps at `http://<tv-ip>`, `adb install` for the APK, what to press).
   - **M1 (Roku):** manifest, wallpaper layer (sharp, blur and deep-tinted scrims), clock block. Hard-code the `starry` theme.
   - **M2 (Roku):** the app row from real ECP data (icons in painted frames), the full focus treatment, launching plus the launching and error overlays, the bottom bar, the toast, and the painting credit.
   - **M3 (Android):** SSDP discovery, EcpClient, and the **Remote tab** pixel-matched to `reference/phone/phone_remote.png` (D-pad with swipe and hold-repeat, haptics). Ship this early because it's the thing the user touches most.
   - **M4 (Android):** `TvWatcherService` with auto-open (spec 15.4), the boot and Wi-Fi triggers, the persistent notification, the Setup tab and onboarding checks.
   - **M5 (both):** weather and the 5-theme engine on the TV (900ms repaint crossfade), the same theme logic on the phone, the Apps tab with the painting strip, and `?theme=` sync.
   - **M6 (backend + both):** the `zeus` edge function. Phone: the Zeus tab (hold to talk, live transcript, pulsing rings, receipts, spoken replies) plus local intents and the ActionRunner. TV: the Zeus panel with chips, keyboard, and messages pushed from the phone through `PhoneBridge.brs`. Phone-owned sleep timer.
   - **M7:** TV suggestion strip habits, Edit Home, App picker, Settings, Ambient, TV onboarding. Phone: Quick Settings tile, widget, optional "Hey Zeus" wake word.

## Hard rules

- Pixel and dp values, colours, fonts, timings and copy come from the spec and the reference images. Don't "improve" the look with extra effects, gradients or default Material styling. The phone must look like the screenshots, not like a stock Compose app.
- **Roku:**
  - No live blur and no shaders. Use only Posters, Rectangles, Labels, 9-patches, MaskGroup (with the clippingRect fallback) and Animation nodes, with at most 6 animations at once.
  - Every network call goes in a Task node.
- **Android:**
  - One foreground service, which only polls while on home Wi-Fi with the TV known. Back off as the spec says.
  - Ask for each permission at the moment it's needed, with a one-line reason.
  - Auto-open must never take over the TV when it was turned on into another app.
- **No API keys** in the Roku package or the APK. Clients use a per-install device token, and the edge function holds the Claude key and rate-limits.
- Never redraw or recolour third-party app logos. Real apps show their own ECP icon inside `frame_<pastel>.png` on both the TV and the phone.
- **TV navigation:** exactly one focused element at all times. Back always goes up one level. Everything within 3 D-pad presses of Home.
- No ads, no promos, no "featured" rows, and nothing that implies Lumen can see inside other apps.
- **Zeus's voice:** warm, brief, specific, no emoji, no exclamation marks, under 30 words, honest about limits (no picture mode, can't see what's playing). Only use app and input IDs that really exist. Never power the TV off unless explicitly asked.
- **Code organisation:**
  - Roku constants live in one `Theme.brs`.
  - Android tokens live in `ui/theme/`.
  - The five painting themes and their deep colours are defined once per platform, identically.

Start by reading the spec and writing `PLAN.md`.
