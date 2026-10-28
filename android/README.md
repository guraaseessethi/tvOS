# Lumen for Android

The phone half of Lumen (spec section 15). It is the TV's remote, Zeus's microphone, the games
leaderboard, and the **Lumen helper**: it hosts the bridge the Roku channel talks to (PLAN section 7),
so the Mac helper (`bridge/lumen_bridge.py`) is no longer needed.

| Tab | What it does |
|---|---|
| Remote | D-pad (tap, swipe, hold to repeat), Back/Home/Options/Type, media, volume, power, Zeus button |
| Apps | Your TV's apps (logos on painted cards, like the TV) and inputs, repaint the TV, Memories photos |
| Zeus | Hold to talk or type. Simple commands run instantly; everything else goes to Gemini through Firebase |
| Games | Leaderboard for Flutter, Pup Run and Star Catcher. Runs played on the TV are saved under your name |
| Setup | Auto-open, spoken replies, lock-screen remote, follow the weather, checks, account, city, change TV |

In the background, a foreground service:
- opens Lumen when the TV turns on and when you back out of an app,
- serves the bridge on port 8977,
- runs the sleep timer.

There's also a Quick Settings tile, "Zeus".

## Build the APK (on the Mac)

```sh
cd android
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # unit tests (no phone needed)
```

Requirements: JDK 17 or newer and the Android SDK. Android Studio installs both. If the build can't
find the SDK, create `android/local.properties` with `sdk.dir=/Users/<you>/Library/Android/sdk`.

Install it on the Pixel:
- **USB:** turn on USB debugging, then run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- **No cable:** copy the APK to the phone, open it, and allow "Install unknown apps".

## Firebase (accounts, leaderboard, Zeus)

The app builds and the remote works without Firebase. Sign-up, the leaderboard and Zeus need it:

1. Go to [console.firebase.google.com](https://console.firebase.google.com) → **Add project** (e.g. "Lumen").
2. **Add app → Android**:
   - Package name `app.lumen`.
   - Add your **SHA-1**, which Google sign-in needs. Get it with `cd android && ./gradlew signingReport`
     (look for "SHA1" under `debug`).
   - Download **google-services.json** into `android/app/`. It's git-ignored.
3. **Authentication → Sign-in method**: turn on **Email/Password** and **Google**.
4. **Firestore Database → Create database** (production mode). Then, under **Rules**, paste
   `firebase/firestore.rules` and click Publish.
5. **AI Logic → Get started**: choose the **Gemini Developer API**. This is what Zeus uses. No API
   key goes in the app; Firebase handles it.
6. Rebuild: `./gradlew assembleDebug`. If you turned on Google sign-in after downloading
   google-services.json, download the file again first.

## Tests

`app/src/test` runs on the JVM with no device:
- `LocalIntentsTest`: offline voice commands.
- `ZeusRulesTest`: the safety filter on Zeus's answers.
- `EcpParseTest`: Roku responses.
- `ThemeRulesTest`: weather → painting, and app colours matching the TV.
