package com.smartcontrol.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

class GestureCameraService : LifecycleService() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var handLandmarker: HandLandmarker? = null
    private var lastCommandAt = 0L
    private var anchorY: Float? = null
    private var anchorX: Float? = null
    private var anchorAt = 0L
    private var lastVolumeAt = 0L
    private var openPalmSince = 0L
    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        try {
            val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.55f)
                .setMinHandPresenceConfidence(0.55f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, inputImage -> try { onHandResult(result) } finally { inputImage.close() } }
                .setErrorListener { error -> Log.e(TAG, "Hand tracking error", error) }
                .build()
            handLandmarker = HandLandmarker.createFromOptions(this, options)
            startCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Could not start hand tracking", e)
            stopSelf()
        }
    }

    private fun startAsForeground() {
        val channelId = "smart_control_gestures"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "إشارات اليد", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Elsayyad Smart Control")
            .setContentText("الكاميرا الأمامية تتابع إشارة يدك — أوقفها من التطبيق")
            .setContentIntent(openApp).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { analyzeFrame(it) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                Log.i(TAG, "Front camera analysis started")
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        try {
            val bitmap: Bitmap = proxy.toBitmap()
            val image = BitmapImageBuilder(bitmap).build()
            val processing = ImageProcessingOptions.builder()
                .setRotationDegrees(proxy.imageInfo.rotationDegrees).build()
            handLandmarker?.detectAsync(image, processing, SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis failed", e)
        } finally {
            proxy.close()
        }
    }

    private fun distance(a: Int, b: Int, points: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        val dx = points[a].x() - points[b].x()
        val dy = points[a].y() - points[b].y()
        return sqrt(dx * dx + dy * dy)
    }

    private fun fingerExtended(tip: Int, pip: Int, wrist: Int, points: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Boolean {
        return distance(tip, wrist, points) > distance(pip, wrist, points) * 1.12f
    }

    private fun onHandResult(result: HandLandmarkerResult) {
        val points = result.landmarks().firstOrNull() ?: run { resetAnchors(); return }
        if (points.size < 21) return
        val now = SystemClock.elapsedRealtime()
        val wristY = points[0].y()
        val wristX = points[0].x()
        val index = fingerExtended(8, 6, 0, points)
        val middle = fingerExtended(12, 10, 0, points)
        val ring = fingerExtended(16, 14, 0, points)
        val pinky = fingerExtended(20, 18, 0, points)
        val fourFingers = index && middle && ring && pinky
        val thumbExtended = distance(4, 2, points) > distance(3, 2, points) * 1.18f
        val otherFingersFolded = !index && !middle && !ring && !pinky

        // Thumb-only: direction is judged from the thumb base to its tip.
        if (otherFingersFolded && thumbExtended && now - lastCommandAt > 1100L) {
            val thumbDeltaY = points[4].y() - points[2].y()
            when {
                thumbDeltaY < -0.045f -> {
                    ControlAccessibilityService.instance?.swipe(true)
                    lastCommandAt = now
                    resetAnchors()
                    return
                }
                thumbDeltaY > 0.045f -> {
                    ControlAccessibilityService.instance?.swipe(false)
                    lastCommandAt = now
                    resetAnchors()
                    return
                }
            }
        }

        // Four fingers extended: move the hand up/down to change media volume.
        if (fourFingers) {
            if (anchorY == null || now - anchorAt > 1200L) {
                anchorY = wristY
                anchorX = wristX
                anchorAt = now
                openPalmSince = now
            } else if (abs(wristY - (anchorY ?: wristY)) > 0.13f && now - lastVolumeAt > 950L) {
                if (wristY < (anchorY ?: wristY)) {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                } else {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                lastVolumeAt = now
                lastCommandAt = now
                anchorY = wristY
                anchorAt = now
                openPalmSince = 0L
                return
            } else if (now - openPalmSince > 1700L && now - lastCommandAt > 1800L) {
                ControlAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                lastCommandAt = now
                openPalmSince = now
            }
        } else {
            openPalmSince = 0L
            anchorY = null
            anchorAt = now
        }

        // Horizontal movement is retained for horizontal page/carousel scrolling.
        val oldX = anchorX
        if (oldX == null || now - anchorAt > 650L) {
            anchorX = wristX
            if (anchorAt == 0L) anchorAt = now
        } else if (abs(wristX - oldX) > 0.22f && now - lastVolumeAt > 1200L) {
            ControlAccessibilityService.instance?.swipeHorizontal(wristX < oldX)
            lastVolumeAt = now
            lastCommandAt = now
            anchorX = wristX
            anchorAt = now
        }

        // A closed fist is the Back gesture.
        if (otherFingersFolded && !thumbExtended && now - lastCommandAt > 1200L) {
            ControlAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            lastCommandAt = now
        }
    }

    private fun resetAnchors() {
        anchorX = null
        anchorY = null
        anchorAt = 0L
        openPalmSince = 0L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return android.app.Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
        handLandmarker?.close()
        handLandmarker = null
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SmartControlGesture"
        private const val NOTIFICATION_ID = 42
    }
}