package com.example.voiceassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

/**
 * Best-effort wake-word listener. Android's regular SpeechRecognizer is not a
 * dedicated always-on hotword engine; OEM battery policies may stop it.
 */
class WakeWordService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private val channelId = "vyron_wake_word"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Hey Vyron is listening")
            .setContentText("Wake word listener active. Stop karne ke liye app kholo.")
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        startForeground(41, notification)
        beginListening()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Hey Vyron", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun beginListening() {
        if (listening || !SpeechRecognizer.isRecognitionAvailable(this)) return
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { listening = true }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { listening = false }
                    override fun onError(error: Int) { listening = false; scheduleRetry() }
                    override fun onResults(results: Bundle?) {
                        listening = false
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (isWakeWord(heard)) {
                            val launch = Intent(this@WakeWordService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra("wake_word_detected", true)
                            }
                            stopListeningAndRelease()
                            startActivity(launch)
                            stopSelf()
                        } else scheduleRetry()
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }
                sr.startListening(intent)
            }
        } catch (_: Exception) { listening = false; scheduleRetry() }
    }

    private fun isWakeWord(value: String): Boolean {
        val t = value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        return t.contains("hey vyron") || t.contains("hey vairon") || t.contains("hey viron") ||
            t.contains("hey v iron") || t.contains("हे वायरोन") || t.contains("हे विरॉन")
    }

    private fun scheduleRetry() { handler.postDelayed({ if (!listening) beginListening() }, 1200) }

    private fun stopListeningAndRelease() {
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { stopListeningAndRelease(); super.onDestroy() }
}
