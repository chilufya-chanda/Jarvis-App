package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRecording = false
    private lateinit var engine: JarvisEngine

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            setStatus("Jarvis needs microphone access to hear you.")
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        updateBackgroundButtonState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine = JarvisEngine(this)

        ensureMicPermission()
        ensureNotificationPermission()
        setUpBackgroundJarvisButton()

        binding.talkButton.setOnClickListener {
            if (!isRecording) startRecording() else stopRecordingAndProcess()
        }
    }

    override fun onResume() {
        super.onResume()
        updateBackgroundButtonState()
    }

    // ---------- Background "always on" Jarvis (orb + wake word) ----------

    private fun setUpBackgroundJarvisButton() {
        binding.backgroundJarvisButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
                return@setOnClickListener
            }
            requestBatteryOptimizationExemption()
            WakeWordService.start(this)
            setStatus("Jarvis is running in the background. Say \"Jarvis\" or tap the orb.")
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun updateBackgroundButtonState() {
        if (Settings.canDrawOverlays(this)) {
            binding.backgroundJarvisButton.text = "Start Background Jarvis"
        } else {
            binding.backgroundJarvisButton.text = "Allow Overlay to Enable Background Jarvis"
        }
    }

    // ---------- Permissions ----------

    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun ensureNotificationPermission() {
        // Android 13+ requires explicit permission to show the
        // "Jarvis is listening" notification the background service needs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ---------- Foreground tap-to-talk (this screen) ----------

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ensureMicPermission()
            return
        }
        engine.startListening()
        isRecording = true
        binding.talkButton.text = "Stop"
        setStatus("Listening...")
    }

    private fun stopRecordingAndProcess() {
        engine.stopListening()
        isRecording = false
        binding.talkButton.text = "Talk"
        setStatus("Thinking...")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val (userText, reply) = withContext(Dispatchers.IO) { engine.think() }
                appendConversation("You", userText)
                appendConversation("Jarvis", reply)
                setStatus("Tap the button and talk to Jarvis")
            } catch (e: Exception) {
                setStatus("Error: ${e.message}")
            }
        }
    }

    // ---------- UI helpers ----------

    private fun setStatus(text: String) {
        binding.statusText.text = text
    }

    private fun appendConversation(speaker: String, text: String) {
        binding.conversationText.append("\n$speaker: $text\n")
    }
}
