package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback

/**
 * Runs Jarvis in the background: listens for the "Jarvis"/"Friday" wake
 * word even with the screen off, and shows the floating orb on top of
 * other apps. Tapping the orb also starts a conversation, same as
 * saying the wake word.
 *
 * HONEST NOTE ON THE LISTENING WINDOW:
 * After the wake word triggers (or the orb is tapped), this records
 * for a fixed 6 seconds and then processes what was said. That's a
 * simplification — a more polished version would detect when you stop
 * talking instead of using a fixed timer. Fine for a first working
 * version; worth revisiting once you're using it and see how 6 seconds
 * actually feels.
 */
class WakeWordService : Service() {

    private var porcupineManager: PorcupineManager? = null
    private var windowManager: WindowManager? = null
    private var orbView: View? = null
    private lateinit var engine: JarvisEngine
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var currentTurnJob: Job? = null
    private var isListeningForCommand = false

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "jarvis_background"
        private const val NOTIFICATION_ID = 1001
        private const val COMMAND_RECORD_MILLIS = 6000L

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = JarvisEngine(this)
        startForeground(NOTIFICATION_ID, buildNotification())
        showOrb()
        startWakeWordListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        porcupineManager?.stop()
        porcupineManager?.delete()
        removeOrb()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- Notification (required to keep running with screen off) ----------

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Jarvis background listening",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Jarvis is listening")
            .setContentText("Say \"Jarvis\" or \"Friday\", or tap the orb.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    // ---------- Wake word ----------

    private fun startWakeWordListening() {
        try {
            val builder = PorcupineManager.Builder()
                .setAccessKey(Config.PICOVOICE_ACCESS_KEY)

            val hasFridayFile = assets.list("")?.contains(Config.FRIDAY_KEYWORD_ASSET_PATH) == true

            if (hasFridayFile) {
                // Porcupine's Android SDK reads keyword files straight out of
                // app/src/main/assets/ when given the filename directly —
                // no manual copying to internal storage needed.
                builder.setKeywordPaths(arrayOf(Config.FRIDAY_KEYWORD_ASSET_PATH))
                builder.setBuiltInKeywords(arrayOf(Porcupine.BuiltInKeyword.JARVIS))
            } else {
                // "Friday" file hasn't been added to assets yet —
                // fall back to just the built-in "Jarvis" keyword so the
                // service still runs instead of crashing.
                builder.setBuiltInKeywords(arrayOf(Porcupine.BuiltInKeyword.JARVIS))
            }

            val callback = PorcupineManagerCallback { _ ->
                onWakeWordDetected()
            }

            porcupineManager = builder.build(applicationContext, callback)
            porcupineManager?.start()
        } catch (e: Exception) {
            // If the AccessKey is missing/invalid or setup failed, the
            // service still runs (orb tap still works) — it just won't
            // react to the voice wake word until this is fixed.
            updateNotificationError("Wake word setup failed: ${e.message}")
        }
    }

    private fun onWakeWordDetected() {
        startCommandRecording()
    }

    // ---------- Orb ----------

    private fun showOrb() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.orb_view, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDrag = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isDrag = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrag) {
                        v.performClick()
                        startCommandRecording()
                    } else {
                        snapOrbToNearestEdge(view, params)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(view, params)
        orbView = view
    }

    private fun snapOrbToNearestEdge(view: View, params: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val orbCenterX = params.x + view.width / 2
        params.x = if (orbCenterX < screenWidth / 2) 0 else screenWidth - view.width
        windowManager?.updateViewLayout(view, params)
    }

    private fun removeOrb() {
        orbView?.let { windowManager?.removeView(it) }
        orbView = null
    }

    // ---------- Talking (triggered by wake word or orb tap) ----------

    private fun startCommandRecording() {
        if (isListeningForCommand) return // already mid-conversation, ignore re-trigger
        isListeningForCommand = true

        porcupineManager?.stop() // don't listen for the wake word while recording a command
        engine.startListening()

        currentTurnJob = serviceScope.launch {
            kotlinx.coroutines.delay(COMMAND_RECORD_MILLIS)
            engine.stopListening()
            try {
                engine.think()
            } catch (e: Exception) {
                updateNotificationError("Jarvis error: ${e.message}")
            }
            isListeningForCommand = false
            try {
                porcupineManager?.start() // resume wake word listening
            } catch (_: Exception) {
                // If Porcupine failed to restart, orb tap still works as a fallback.
            }
        }
    }

    private fun updateNotificationError(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
