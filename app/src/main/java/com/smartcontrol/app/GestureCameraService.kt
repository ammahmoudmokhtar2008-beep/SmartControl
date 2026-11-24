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

class GestureCameraService : LifecycleService() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var handLandmarker: HandLandmarker? = null
    private var lastCommandAt = 0L
    private var swipeAnchorX: Float? = null
    private var swipeAnchorY: Float? = null
    private var anchorAt = 0L
    private var lastSwipeAt = 0L
    private var openPalmSince = 0L
    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        try {
            val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base).setRunningMode(RunningMode.LIVE_STREAM).setNumHands(1)
                .setMinHandDetectionConfidence(0.65f).setMinHandPresenceConfidence(0.65f)
                .setMinTrackingConfidence(0.6f)
                .setResultListener { result, inputImage -> try { onHandResult(result) } finally { inputImage.close() } }
                .setErrorListener { error -> Log.e(TAG, "Hand tracking error", error) }.build()
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
                NotificationChannel(channelId, "التحكم بالإشارات", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("SmartControl يعمل")
            .setContentText("الكاميرا تتابع إشارات اليد — أوقفها من التطبيق عند عدم الحاجة")
            .setContentIntent(openApp).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(cameraExecutor) { analyzeFrame(it) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) { Log.e(TAG, "Camera initialization failed", e); stopSelf() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        try {
            val bitmap: Bitmap = proxy.toBitmap()
            val image = BitmapImageBuilder(bitmap).build()
            val processing = ImageProcessingOptions.builder().setRotationDegrees(proxy.imageInfo.rotationDegrees).build()
            handLandmarker?.detectAsync(image, processing, SystemClock.uptimeMillis())
        } catch (e: Exception) { Log.e(TAG, "Frame analysis failed", e) }
        finally { proxy.close() }
    }

    private fun onHandResult(result: HandLandmarkerResult) {
        val p = result.landmarks().firstOrNull() ?: run { resetAnchors(); return }
        if (p.size < 21) return
        val now = SystemClock.elapsedRealtime()
        val wristY = p[0].y()
        val wristX = p[0].x()
        val indexUp = p[8].y() < p[6].y()
        val middleUp = p[12].y() < p[10].y()
        val ringUp = p[16].y() < p[14].y()
        val pinkyUp = p[20].y() < p[18].y()
        val fourUp = indexUp && middleUp && ringUp && pinkyUp

        // Thumb-only pose: the other four fingers are folded. Thumb direction controls page scrolling.
        val folded = !indexUp && !middleUp && !ringUp && !pinkyUp
        val thumbTip = p[4]
        val thumbIp = p[3]
        val thumbExtended = abs(thumbTip.x() - p[2].x()) > 0.055f || abs(thumbTip.y() - p[2].y()) > 0.055f
        if (folded && thumbExtended && now - lastCommandAt > 900L) {
            when {
                thumbTip.y() < thumbIp.y() - 0.025f -> {
                    ControlAccessibilityService.instance?.swipe(true)
                    lastCommandAt = now
                    resetAnchors()
                    return
                }
                thumbTip.y() > thumbIp.y() + 0.025f -> {
                    ControlAccessibilityService.instance?.swipe(false)
                    lastCommandAt = now
                    resetAnchors()
                    return
                }
            }
        }

        // Four extended fingers moved vertically: volume up/down. Require a deliberate displacement.
        if (fourUp) {
            val oldY = swipeAnchorY
            if (oldY == null || now - anchorAt > 650L) {
                swipeAnchorY = wristY
                swipeAnchorX = wristX
                anchorAt = now
                openPalmSince = now
            } else if (abs(wristY - oldY) > 0.12f && now - lastSwipeAt > 850L) {
                if (wristY < oldY) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                else audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                lastSwipeAt = now
                lastCommandAt = now
                swipeAnchorY = wristY
                anchorAt = now
                openPalmSince = 0L
                return
            } else if (now - openPalmSince > 1000L && now - lastCommandAt > 1400L) {
                ControlAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                lastCommandAt = now
                openPalmSince = now
            }
        } else {
            openPalmSince = 0L
            swipeAnchorY = null
            anchorAt = now
        }

        // Horizontal hand movement remains available for horizontal scrolling.
        val oldX = swipeAnchorX
        if (oldX == null || now - anchorAt > 500L) {
            swipeAnchorX = wristX
            if (anchorAt == 0L) anchorAt = now
        } else if (abs(wristX - oldX) > 0.20f && now - lastSwipeAt > 1100L) {
            ControlAccessibilityService.instance?.swipeHorizontal(wristX < oldX)
            lastSwipeAt = now
            lastCommandAt = now
            swipeAnchorX = wristX
            anchorAt = now
        }
        if (now - lastCommandAt < 1000L) return
        // Closed fist: back.
        if (!indexUp && !middleUp && !ringUp && !pinkyUp && !thumbExtended) {
            ControlAccessibilityService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            lastCommandAt = now
        }
    }

    private fun resetAnchors() {
        swipeAnchorX = null
        swipeAnchorY = null
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
