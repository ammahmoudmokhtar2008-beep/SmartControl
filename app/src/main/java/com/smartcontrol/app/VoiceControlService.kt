package com.smartcontrol.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat

class VoiceControlService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var destroyed = false
    private var useOnDevice = false

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        useOnDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        scheduleRecognition(250)
    }

    private fun startAsForeground() {
        val channel = "smart_control_voice"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "الأوامر الصوتية", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Elsayyad Smart Control")
            .setContentText("الاستماع الصوتي نشط في الخلفية — أوقفه من التطبيق")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun active(): Boolean {
        val prefs = getSharedPreferences("smart_control", MODE_PRIVATE)
        return !destroyed && prefs.getBoolean("master_on", false) && prefs.getBoolean("voice_on", false) &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun scheduleRecognition(delay: Long) {
        handler.removeCallbacks(startTask)
        if (active()) handler.postDelayed(startTask, delay)
    }

    private val startTask = Runnable { startRecognition() }

    private fun startRecognition() {
        if (!active() || listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "No speech recognition provider is installed")
            scheduleRecognition(3000)
            return
        }
        try {
            recognizer?.destroy()
            recognizer = if (useOnDevice && Build.VERSION.SDK_INT >= 31)
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            else SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) {
                    listening = false
                    Log.w(TAG, "Recognition error: $error")
                    // No-match and silence are normal in continuous listening; recreate the recognizer.
                    scheduleRecognition(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 500)
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (words.isNotBlank() && active()) MainActivity.executeCommand(this@VoiceControlService, words)
                    scheduleRecognition(450)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDevice)
            }
            recognizer?.startListening(intent)
            listening = true
        } catch (e: Exception) {
            listening = false
            Log.e(TAG, "Could not start speech recognition", e)
            scheduleRecognition(1600)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleRecognition(200)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the explicitly enabled foreground listener alive when the app task is swiped away.
        scheduleRecognition(300)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SmartControlVoice"
        private const val NOTIFICATION_ID = 43
    }
}