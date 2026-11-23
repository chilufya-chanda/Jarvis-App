package com.jarvis.assistant

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File

/**
 * The core talk pipeline (record -> transcribe -> think -> speak),
 * pulled out into one shared place so both the normal app screen
 * (MainActivity) and the background orb service can use the exact
 * same logic instead of two copies drifting apart over time.
 *
 * Not thread-safe for overlapping calls — only one conversation turn
 * should run at a time, which matches how a voice assistant is
 * actually used (you don't talk over yourself).
 */
class JarvisEngine(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private val audioFile: File = File(context.cacheDir, "jarvis_input.m4a")

    // Keeps conversation context across turns, same as before.
    private val conversation = mutableListOf<ChatMessage>()

    fun startListening() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            prepare()
            start()
        }
    }

    fun stopListening() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }

    /**
     * Runs one full turn: transcribes what was just recorded, asks
     * Jarvis for a reply, and speaks it out loud. Returns the pair of
     * (what you said, what Jarvis said) so the caller can show it on
     * screen if it wants to.
     *
     * Must be called from a background thread — this makes blocking
     * network calls.
     */
    fun think(): Pair<String, String> {
        val userText = GroqClient.transcribeAudio(audioFile)
        conversation.add(ChatMessage("user", userText))

        val reply = GroqClient.chat(conversation)
        conversation.add(ChatMessage("assistant", reply))

        val audioBytes = EdgeTtsClient.synthesize(reply)
        playAudio(audioBytes)

        return userText to reply
    }

    private fun playAudio(mp3Bytes: ByteArray) {
        val tempFile = File(context.cacheDir, "jarvis_reply.mp3")
        tempFile.writeBytes(mp3Bytes)

        val player = MediaPlayer()
        player.setDataSource(tempFile.absolutePath)
        player.setOnCompletionListener { it.release() }
        player.prepare()
        player.start()
    }
}
