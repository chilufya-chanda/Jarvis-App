package com.jarvis.assistant

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Talks to Microsoft's Edge "read aloud" voice service to turn text into
 * speech, and returns raw MP3 audio bytes.
 *
 * IMPORTANT — READ THIS:
 * Edge TTS is not an official public API. There is no key, no signup —
 * it's the same voice engine behind the "Read Aloud" feature in the Edge
 * browser, accessed directly. This makes it free, but it also means
 * Microsoft could change how this endpoint behaves without notice.
 *
 * This is the one piece of Phase 1 most likely to need small adjustments
 * once you actually test it in Android Studio, since it can't be verified
 * from outside a real device/network. If audio doesn't come back, this
 * is the first file to look at — come back and I'll help debug it with
 * you against whatever error you're seeing.
 */
object EdgeTtsClient {

    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val WSS_URL =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
            "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // websocket stays open until we close it
        .build()

    /**
     * Synthesizes [text] using [voice] and returns raw MP3 bytes.
     * This call blocks until audio is fully received or it times out.
     */
    fun synthesize(text: String, voice: String = Config.EDGE_TTS_VOICE): ByteArray {
        val audioBuffer = ByteArrayOutputStream()
        val latch = CountDownLatch(1)
        var failure: Exception? = null

        val request = Request.Builder()
            .url(WSS_URL)
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .build()

        val requestId = UUID.randomUUID().toString().replace("-", "")

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 1) Send the config message
                val configMessage =
                    "X-Timestamp:${isoTimestamp()}\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""
                webSocket.send(configMessage)

                // 2) Send the actual SSML to speak
                val ssml = buildSsml(text, voice)
                val ssmlMessage =
                    "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${isoTimestamp()}\r\n" +
                        "Path:ssml\r\n\r\n" +
                        ssml
                webSocket.send(ssmlMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    webSocket.close(1000, null)
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary audio frames have a small header before the actual
                // MP3 data; the header length is encoded in the first 2 bytes.
                val data = bytes.toByteArray()
                if (data.size < 2) return
                val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                val audioStart = 2 + headerLength
                if (audioStart < data.size) {
                    audioBuffer.write(data, audioStart, data.size - audioStart)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure = RuntimeException("Edge TTS connection failed: ${t.message}", t)
                latch.countDown()
            }
        }

        val webSocket = client.newWebSocket(request, listener)
        val completed = latch.await(20, TimeUnit.SECONDS)
        if (!completed) {
            webSocket.cancel()
            throw RuntimeException("Edge TTS timed out waiting for audio")
        }
        failure?.let { throw it }

        return audioBuffer.toByteArray()
    }

    private fun buildSsml(text: String, voice: String): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return """<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>""" +
            """<voice name='$voice'>$escaped</voice></speak>"""
    }

    private fun isoTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'")
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }
}
