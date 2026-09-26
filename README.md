# RintOS

A deeply customizable Android launcher with a pixel-art roommate named **Rin**.

![RintOS screens](docs/screenshots.jpg)

## What's inside

- **Home screen**: grid (3–7 columns, 4–9 rows), 8 page transitions, drag and drop between pages and the dock, rename/hide apps, icon packs, 10 icon shapes and 5 icon styles, real frosted glass (the wallpaper blurred behind every widget, dock and pill), and a magnifying dock.
- **Wallpapers**: three bundled artworks (Tide, Pixel Night starring Rin, Paper), plus system, solid, gradient and animated aurora modes.
- **Interactive widgets**: clock (7 faces, incl. tty-style blocks), Rint Music, Rin the pet, sticky note, checklist, focus timer, full calculator, tally, dice and coin, flashlight, battery, month calendar, weather (Open-Meteo), Ask Rin. Other apps' Android widgets are supported too.
- **Rint Music**: search any song, play it through your music app (Spotify, YouTube Music, …) or from files on the phone, with synced lyrics from LRCLIB (fallbacks to plain lyrics), karaoke highlight, blocky terminal font, blurred artwork background, and Rin head-bobbing during instrumental breaks.
- **Notch**: your own dynamic island with 6 shapes, live activity while music plays, and an expanded now-playing view with the current lyric line.
- **Lock screen**: 10 styles (classic, blocks, stacked, words, analog, terminal, minimal, poster, music, Rin's room), up to 10 shortcuts and 5 unlock effects. It sits *on top of* the system keyguard, so your PIN/fingerprint still protects the phone.
- **Rin assistant**: chat or talk to Rin, powered by **Gemini, Groq, Claude or OpenRouter** (you type the model name). Rin can see the screen, tap, type, scroll, open apps, play music and restyle the launcher. Before anything irreversible (sending, buying, deleting, posting) he asks first.
  - Voice: speech-to-text uses Android's built-in recognizer (free, no key). Rin's voice uses Gemini's TTS models with a Gemini API key (free tier), with Android's offline voice as a fallback engine.
  - API keys are stored only on the phone, never included in exports, and the app opts out of cloud backup.
- **Settings**: 168 options in grouped lists, a live preview, one-tap presets, search, and export/import of your whole setup.
- **Intro**: a synthesized soundtrack (generated live, no audio files) driving a 24-bar launch film, a "make it yours" step, and a finale that turns into a START button. Then permissions and a guided tour from Rin.
- **Stability**: ambient animations run on a lifecycle-aware clock, so they freeze whenever the screen is off, the app is in the background, or something covers them.

## Build

Requirements: JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # logic tests + Paparazzi screenshot checks
./gradlew :app:recordPaparazziDebug   # re-render the screenshots in app/src/test/snapshots
```

Install the APK, open it, and choose RintOS as your home app when asked.

## Permissions

| Permission | What it unlocks |
|---|---|
| Default home app | RintOS appears when you press home |
| Notification access | Notification dots, and live lyrics for music playing in other apps |
| Music & audio | Playing your own song files |
| Contacts | Finding people from search |
| Microphone | Talking to Rin |
| Accessibility ("RintOS gestures") | Double-tap to lock, recents, auto-showing the lock screen, and Rin operating the phone. Used only while you've asked Rin to do something. |

## Known limits

- The screenshots in this repo are rendered on a desktop JVM (Paparazzi), where no apps are installed, so app icons there are stand-ins. On a phone you see your real app icons.
- It hasn't yet been run on a physical device or emulator. Expect some rough edges on first install.
- Rint Music never plays YouTube in a hidden player: YouTube's terms forbid hiding or obscuring the video. It hands playback to your music app and follows along instead.
- Gemini/Groq/OpenRouter default model names may go stale. Change them in Settings → Rin assistant.
