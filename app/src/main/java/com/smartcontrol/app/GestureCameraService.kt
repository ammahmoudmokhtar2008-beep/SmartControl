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
import com.google.mediapipe.framework.image.ImageProcessingOptions
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.abs

class GestureCameraService : LifecycleService() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var handLandmarker: HandLandmarker? = null
    private var lastCommandAt = 0L
    private var swipeAnchorX: Float? = null
    private var swipeAnchorAt = 0L
    private var lastSwipeAt = 0L
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
                .setResultListener { result, inputImage ->\n                    try { onHandResult(result) } finally { inputImage.close() }\n                }
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
            val channel = NotificationChannel(channelId, "التحكم بالإشارات", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Smart Control يعمل")
            .setContentText("الكاميرا تتابع إشارات اليد للتحكم في الهاتف")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy -> analyzeFrame(proxy) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
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
                .setRotationDegrees(proxy.imageInfo.rotationDegrees)
                .build()
            handLandmarker?.detectAsync(image, processing, SystemClock.uptimeMillis())
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis failed", e)
        } finally {
            proxy.close()
        }
    }

    private fun onHandResult(result: HandLandmarkerResult) {
        val points = result.landmarks().firstOrNull() ?: return
        if (points.size < 21) return
        val indexUp = points[8].y() < points[6].y()
        val middleUp = points[12].y() < points[10].y()
        val ringUp = points[16].y() < points[14].y()
        val pinkyUp = points[20].y() < points[18].y()
        val thumbUp = points[4].y() < points[3].y()
        val thumbDown = points[4].y() > points[3].y()
        val fingersUp = listOf(indexUp, middleUp, ringUp, pinkyUp).count { it }
        val now = SystemClock.elapsedRealtime()

        val wristX = points[0].x()
        val anchorX = swipeAnchorX
        if (anchorX == null || now - swipeAnchorAt > 450L) {
            swipeAnchorX = wristX
            swipeAnchorAt = now
        } else if (abs(wristX - anchorX) > 0.18f && now - lastSwipeAt > 1100L) {
            lastSwipeAt = now
            swipeAnchorX = wristX
            swipeAnchorAt = now
            ControlAccessibilityService.instance?.swipeHorizontal(wristX < anchorX)
            return
        }
        if (now - lastCommandAt < 1300L) return

        val service = ControlAccessibilityService.instance
        when {
            fingersUp == 4 -> {
                lastCommandAt = now
                service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            }
            fingersUp == 0 && thumbUp -> {
                lastCommandAt = now
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            fingersUp == 0 && thumbDown -> {
                lastCommandAt = now
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            fingersUp == 0 -> {
                lastCommandAt = now
                service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return android.app.Service.START_STICKY
    }

    override fun onDestroy() {
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) { }
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
