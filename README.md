# Jarvis — Phase 1 Setup

This is the core loop: tap the button, talk, Jarvis transcribes it (Groq
Whisper), thinks of a reply (Groq LLM, with Jarvis's personality baked
in), and speaks it back (Edge TTS). No wake word, no calendar, no memory
yet — those come in later phases.

## Steps to get this running on your phone

1. **Install Android Studio** (free): https://developer.android.com/studio

2. **Open this project folder** in Android Studio (File → Open → select
   the `JarvisApp` folder).

3. **Get a free Groq API key:**
   - Go to https://console.groq.com
   - Sign up (no credit card required)
   - Create an API key

4. **Add your key to the project:**
   - Open `app/src/main/java/com/jarvis/assistant/Config.kt`
   - Replace `PUT_YOUR_GROQ_API_KEY_HERE` with your actual key

5. **Connect your phone:**
   - Enable Developer Options + USB Debugging on your Android phone
     (search "enable developer options [your phone model]" if you've
     never done this — it's a one-time toggle)
   - Plug your phone into your computer via USB
   - Your phone should show up as a device in Android Studio's toolbar

6. **Hit the green Run (▶) button** in Android Studio. It'll build the
   app and install it directly onto your phone.

7. **Grant microphone permission** when the app asks on first launch.

8. Tap **Talk**, say something, tap **Stop**, and Jarvis should reply
   out loud.

## If something breaks

The most likely trouble spot is `EdgeTtsClient.kt` — it talks to an
unofficial Microsoft endpoint, and details like exact headers can shift.
If you tap Talk and get an error instead of a voice reply, copy the
exact error text shown on screen and bring it back here — that'll tell
us exactly what to fix.

## What's next (not built yet)

- Phase 4: Real-time web info via Groq Compound
- Phase 5: Long-term memory via Firebase

---

# Phase 3 Setup — Wake Word + Floating Orb + Background Mode

Phase 3 adds:
- The floating orb (tap it anywhere, on top of any app, to talk to Jarvis)
- Voice wake word — say "Jarvis" (built in, no setup) or "Friday" (needs a
  quick free step below) to start talking hands-free
- Background operation — works with the screen off

### Step 1 — Get a free Picovoice AccessKey (required)

1. Go to https://console.picovoice.ai and sign up (free, no credit card)
2. Copy your AccessKey from the dashboard
3. Paste it into `Config.kt`, replacing `PUT_YOUR_PICOVOICE_ACCESS_KEY_HERE`

**"Jarvis" works immediately with just this step** — it's one of
Porcupine's built-in wake words, no extra files needed.

### Step 2 — (Optional) Add "Friday" as a second wake word

"Friday" is NOT a built-in Porcupine keyword, so it needs a quick free
training step:

1. On the Picovoice console, go to **Porcupine > Create a wake word**
2. Type "Friday", choose **Android** as the platform, generate it
3. Download the resulting `.ppn` file
4. Rename it to exactly `friday.ppn`
5. Place it in `app/src/main/assets/friday.ppn` (replacing the
   placeholder text file that's there now)

If you skip this step, saying "Jarvis" still works fine — the app
detects the missing file automatically and just runs with "Jarvis" only,
it won't crash.

### Step 3 — Run the app and enable background mode

1. Run the app on your phone (same as Phase 1/2)
2. Tap **"Allow Overlay to Enable Background Jarvis"** — this opens a
   system settings screen, toggle the permission on, then go back
3. Tap the button again (now "Start Background Jarvis")
4. You'll be asked to exempt Jarvis from battery optimization — allow it,
   or the phone may kill the background listener within hours
5. You should see a small blue orb appear on your screen, and a
   persistent notification saying "Jarvis is listening"
6. Try saying "Jarvis" or tapping the orb, then speak — it records for
   6 seconds and replies

### Honest caveats specific to Phase 3:

- **Fixed 6-second listening window:** after the wake word/tap, it
  records for a flat 6 seconds rather than detecting when you stop
  talking. Simple and reliable to start with; can be refined later once
  you've felt how it works in practice.
- **This is the most complex part of the whole app** (background
  service + floating overlay + always-on mic, all interacting) — it's
  the area most likely to behave differently across phone brands. If
  the orb doesn't appear, or the wake word doesn't fire, or the service
  gets killed overnight, bring me the exact behavior and we'll debug it
  against your specific phone.
- **Samsung/Xiaomi/etc. aggressive battery managers:** even with the
  battery optimization exemption granted, some phone brands have their
  own separate "auto-start" or "background app" list that also needs
  the app manually whitelisted. Search "[your phone brand] keep app
  running background" if the service stops overnight.
