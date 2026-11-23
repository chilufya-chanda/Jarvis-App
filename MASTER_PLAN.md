# Jarvis — Personal Android Voice Assistant
### Master Plan

---

## 1. What This Is

A personal, voice-activated AI assistant on Android, named **Jarvis** (wake word: "Jarvis" or "Friday"). Fully cloud-based for the AI processing — no home server, no self-hosting. Runs on your phone, screen on or off, wakes on voice trigger, talks back naturally.

---

## 2. Architecture — Confirmed Stack

| Function | Tool | Cost | Notes |
|---|---|---|---|
| Wake word detection | Picovoice Porcupine | Free tier | Custom wake words "Jarvis" / "Friday", on-device, background listening |
| Speech-to-text (STT) | Groq Whisper (Large v3 Turbo) | Free (2,000 requests/day) | Cloud API, fast, accurate |
| Text-to-speech (TTS) | Microsoft Edge TTS | Free (uncapped) | Cloud, natural neural voices |
| AI brain / reasoning | Groq (Llama models) | Free (~1,000-1,500 req/day) | Handles conversation, personality, advice |
| Real-time info | Groq Compound (built-in web search) | Free | Weather, news, current events |
| Personal memory | Firebase Firestore | Free (1 GiB storage, 50K reads/day) | Cloud database, stores notes/facts from conversations over time |

**Everything above is genuinely free at your expected personal-use volume. No credit card charges expected.**

**Note:** Google Calendar integration was built in Phase 2 and then removed at your request. It's not part of the current plan.

---

## 3. Personality & Behavior Spec

This is what makes it feel like *your* Jarvis, not a generic bot.

- **Name:** Jarvis
- **Wake words:** "Jarvis" or "Friday" (both trigger it)
- **Response length:** Short and direct by default. No long explanations unless you specifically ask for more detail ("tell me more", "explain fully", etc.)
- **Tone:** Friendly, a little funny/witty — not robotic, not overly formal
- **Conversational habit:** Asks follow-up questions like a person would, instead of just answering and stopping
- **Check-ins:** At appropriate moments, it asks about your day/life ("how did that thing go today?", "you good?") — and when you tell it what's going on, it responds with real advice and keeps the conversation going naturally, like a friend would, not a form to fill out

## 3a. Interface — Floating Orb

Instead of a normal app screen, Jarvis lives as a **small rounded floating overlay ("orb")** that sticks to the side of the screen on top of whatever app you're using — similar to Messenger's chat heads.

- **Trigger methods — both:**
  - Tap the orb to start talking
  - Say "Jarvis" or "Friday" to wake it hands-free (via Porcupine), no tap needed
- Requires Android's "draw over other apps" permission — one-time system prompt, same as Messenger asks for
- This replaces Phase 1's plain in-app screen as the real, final interface once built

---

## 4. Build Phases

**Phase 1 — Core Loop (build first, test immediately)**
Tap to talk → Groq Whisper transcribes → Groq LLM (with Jarvis personality) responds → Edge TTS speaks it back.
No wake word yet. Proves the whole pipeline works end to end.

**Phase 2 — (Removed)**
Google Calendar integration was built, then removed at your request. Skipped going forward.

**Phase 3 — Wake Word + Floating Orb + Background Operation**
Porcupine listens for "Jarvis" / "Friday" with screen off. The plain Phase 1 screen is replaced with the floating orb overlay (see Section 3a) — tap it or say the wake word, either works. Battery optimization exemption setup (Android will try to kill the background service otherwise — you'll need to manually whitelist the app).

**Phase 4 — Real-Time Info + Personality Polish**
Groq Compound wired in for live web info. Tune the personality prompt (humor, question-asking, life check-ins) based on how it actually feels once you're using it.

**Phase 5 — Memory**
Wire in Firebase Firestore. Jarvis starts saving and recalling personal notes across conversations.

---

## 5. Known Honest Limitations

- **Groq free tier caps:** ~1,000-1,500 LLM requests/day, 2,000 Whisper requests/day. Very unlikely to hit these in normal personal use, but not infinite.
- **Edge TTS is unofficial:** It's a reverse-engineered wrapper around Microsoft's browser voice feature, not an official paid product. Stable and widely used, but Microsoft could change it without notice.
- **Background wake-word listening drains battery** more than a phone that's just idle. Real cost of "always listening."
- **Some phone brands (Samsung/Xiaomi aggressive battery managers) may kill the background service** unless manually exempted — this will need a one-time setup step on your specific phone.
- **No GPU/local hosting involved** — every AI call goes out over the internet. No wifi/data = no Jarvis.

---

## 6. Personal Memory — Decided

**Tool: Firebase Firestore (free "Spark" plan)**

- 1 GiB storage, 50,000 reads/day, free forever — no credit card charges expected at personal-use scale
- Cloud-based, no self-hosting, fits the rest of the stack
- Requires your Google account

**How it works:**
1. During conversations, Jarvis identifies things worth remembering (life events, ongoing situations, preferences) and saves short dated notes to Firestore
2. Before responding, Jarvis quietly pulls in relevant saved notes so it can follow up naturally ("how's that thing with your job going?") instead of starting fresh every time
3. Invisible to you — it just feels like Jarvis remembers

**Known open design question:** deciding what's worth saving vs. what's just small talk isn't automatic — too much saved gets cluttered, too little feels forgetful. This will need real tuning once you're actually using it day to day, not something finalized on paper in advance.

---

## 7. What You'll Need

- Android Studio installed on a PC (confirmed available)
- A free Groq API key (console.groq.com — no credit card)
- A free Picovoice account + AccessKey (for Porcupine wake word)
- Your Google account (for Firebase, later in Phase 5)

---

## 8. Status

**Phase 1:** Built — core talk/reply loop (Groq Whisper → Groq LLM → Edge TTS). Not yet tested on a real device.
**Phase 2:** Removed at your request — no calendar integration in the current build.
**Phase 3:** Built — wake word ("Jarvis" built-in, "Friday" via optional free custom training), floating draggable orb overlay, background foreground-service operation with battery-optimization exemption flow. Uses a fixed 6-second listening window after trigger (documented simplification, see README). Requires a free Picovoice AccessKey.
**Current step:** Phase 4 — real-time web info via Groq Compound.
