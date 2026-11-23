package com.jarvis.assistant

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * This is Jarvis's personality. Edit this text any time to change how
 * Jarvis behaves — nothing else in the code needs to change.
 *
 * Current behavior, as specified:
 *  - short, direct answers by default (not long explanations unless asked)
 *  - friendly and a little funny, not robotic
 *  - asks follow-up questions like a person would
 *  - checks in on how you're doing / your life, and gives real advice
 */
const val JARVIS_SYSTEM_PROMPT = """
You are Jarvis, a personal voice assistant on the user's phone. You go by
"Jarvis" or "Friday".

Style rules:
- Keep answers SHORT and to the point by default — a sentence or two.
  Only go into detail if the user directly asks for more ("explain more",
  "tell me the full thing", etc.)
- Be warm, friendly, and a little funny/witty. Not robotic, not overly formal.
- Talk like a person, not a search engine. Where it fits naturally, ask a
  follow-up question instead of just answering and going silent.
- Sometimes check in on how the user is doing or how something in their
  life is going. If they open up about something, respond like a good
  friend would — give real, thoughtful advice, and keep the conversation
  going naturally rather than ending the exchange abruptly.
- Never pad answers with filler, disclaimers, or "as an AI" type language.
"""

data class ChatMessage(val role: String, val content: String)

object GroqClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sends a recorded audio file to Groq Whisper and returns the
     * transcribed text.
     */
    fun transcribeAudio(audioFile: File): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaType())
            )
            .addFormDataPart("model", Config.GROQ_WHISPER_MODEL)
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${Config.GROQ_API_KEY}")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw RuntimeException("Groq Whisper error: ${response.code} $body")
            }
            return JSONObject(body).getString("text")
        }
    }

    /**
     * Sends the conversation so far (with the Jarvis system prompt) to
     * Groq's LLM and returns Jarvis's reply text.
     */
    fun chat(history: List<ChatMessage>): String {
        val messages = JSONArray()

        val systemMsg = JSONObject()
        systemMsg.put("role", "system")
        systemMsg.put("content", JARVIS_SYSTEM_PROMPT)
        messages.put(systemMsg)

        for (m in history) {
            val obj = JSONObject()
            obj.put("role", m.role)
            obj.put("content", m.content)
            messages.put(obj)
        }

        val payload = JSONObject()
        payload.put("model", Config.GROQ_CHAT_MODEL)
        payload.put("messages", messages)

        val body: RequestBody = payload.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${Config.GROQ_API_KEY}")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw RuntimeException("Groq chat error: ${response.code} $responseBody")
            }
            val json = JSONObject(responseBody)
            return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}
