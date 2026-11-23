package com.jarvis.assistant

/**
 * Drop your free Groq API key here. Get one at https://console.groq.com
 * (no credit card required).
 *
 * Nothing else in this project needs an API key for Phase 1 —
 * Edge TTS doesn't require one at all.
 */
object Config {
    const val GROQ_API_KEY = "PUT_YOUR_GROQ_API_KEY_HERE"

    // Groq model used for conversation. llama-3.3-70b-versatile is a strong
    // free-tier option. Swap this later if you want to try others.
    const val GROQ_CHAT_MODEL = "llama-3.3-70b-versatile"

    // Groq's hosted Whisper model for speech-to-text.
    const val GROQ_WHISPER_MODEL = "whisper-large-v3-turbo"

    // Edge TTS voice. "en-US-GuyNeural" is a natural, friendly-sounding
    // male US English voice. Full voice list is easy to find by searching
    // "edge-tts list voices" if you want to change it later.
    const val EDGE_TTS_VOICE = "en-US-GuyNeural"

    /**
     * Free AccessKey from https://console.picovoice.ai — required for the
     * "Jarvis" / "Friday" wake word to work. See README "Phase 3 Setup".
     */
    const val PICOVOICE_ACCESS_KEY = "PUT_YOUR_PICOVOICE_ACCESS_KEY_HERE"

    /**
     * "Jarvis" is one of Porcupine's built-in wake words, so it works with
     * no extra files. "Friday" is NOT built in and needs a custom trained
     * file (free, a few minutes, see README) placed at this exact path:
     * app/src/main/assets/friday.ppn
     */
    const val FRIDAY_KEYWORD_ASSET_PATH = "friday.ppn"
}
