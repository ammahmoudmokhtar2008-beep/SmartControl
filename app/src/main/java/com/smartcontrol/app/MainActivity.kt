package com.smartcontrol.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.media.AudioManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.view.Gravity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : Activity() {
    private var recognizer: SpeechRecognizer? = null
    private lateinit var status: TextView
    private lateinit var gestureButton: Button
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var gestureModeOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) status.text = "التعرف على الصوت غير متاح على هذا الجهاز."
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun buildUi() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val title = TextView(this).apply {
            text = "Smart Control"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "التحكم الصوتي جاهز. فعّل خدمة إمكانية الوصول أولاً."
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        val listenButton = Button(this).apply {
            text = "ابدأ الاستماع"
            setOnClickListener { startListening() }
        }
        val accessButton = Button(this).apply {
            text = "تفعيل صلاحية التحكم"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        gestureButton = Button(this).apply {
            text = "تشغيل التحكم بإشارات اليد"
            setOnClickListener { toggleGestureMode() }
        }
        val help = TextView(this).apply {
            text = "الأوامر الصوتية: ارجع، الرئيسية، الإشعارات، الإعدادات، واتساب، الكاميرا، ارفع الصوت، اخفض الصوت، مرر لأعلى أو لأسفل.\n\nإشارات اليد: كف مفتوح = الرئيسية، قبضة = رجوع، إبهام لأعلى/لأسفل = رفع/خفض الصوت، تحريك اليد يمينًا/يسارًا = تمرير أفقي."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 0)
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(listenButton)
        layout.addView(accessButton)
        layout.addView(gestureButton)
        layout.addView(help)
        setContentView(layout)
    }

    private fun toggleGestureMode() {
        if (gestureModeOn) {
            stopService(Intent(this, GestureCameraService::class.java))
            gestureModeOn = false
            gestureButton.text = "تشغيل التحكم بإشارات اليد"
            status.text = "تم إيقاف التحكم بالإشارات."
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 11)
            return
        }
        startGestureMode()
    }

    private fun startGestureMode() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        ContextCompat.startForegroundService(this, Intent(this, GestureCameraService::class.java))
        gestureModeOn = true
        gestureButton.text = "إيقاف التحكم بإشارات اليد"
        status.text = "وضع الإشارات يعمل في الخلفية. راجع الإشعار للتأكد من تشغيله."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 11) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startGestureMode()
            else Toast.makeText(this, "لازم تسمح باستخدام الكاميرا لتشغيل إشارات اليد.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "خدمة التعرف على الصوت غير متاحة."
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "سامعك... اتكلم الآن" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "جاري فهم الأمر..." }
            override fun onError(error: Int) { status.text = "لم ألتقط الأمر. اضغط وجرّب مرة أخرى." }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                status.text = "سمعت: $words"
                handleCommand(words)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "قل الأمر")
        }
        recognizer?.startListening(intent)
    }

    private fun handleCommand(raw: String) {
        val s = raw.lowercase(Locale("ar", "EG"))
        val service = ControlAccessibilityService.instance
        when {
            s.contains("ارجع") || s.contains("رجوع") || s.contains("ورا") -> service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            s.contains("الرئيسية") || s.contains("الشاشة الرئيسية") -> service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            s.contains("الإشعارات") || s.contains("الاشعارات") -> service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            s.contains("الإعدادات") || s.contains("الاعدادات") -> launchPackage("com.android.settings")
            s.contains("واتساب") -> launchPackage("com.whatsapp")
            s.contains("الكاميرا") -> launchIntent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            s.contains("ارفع الصوت") || s.contains("علي الصوت") -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            s.contains("اخفض الصوت") || s.contains("وطي الصوت") -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            s.contains("اكتم الصوت") -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            s.contains("مرر لفوق") || s.contains("اسكرول لفوق") -> service?.swipe(true)
            s.contains("مرر لتحت") || s.contains("اسكرول لتحت") -> service?.swipe(false)
            else -> Toast.makeText(this, "الأمر غير مدعوم في النسخة الأولية", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchPackage(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) startActivity(intent) else Toast.makeText(this, "التطبيق غير مثبت", Toast.LENGTH_SHORT).show()
    }

    private fun launchIntent(action: String) {
        try { startActivity(Intent(action)) } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح الكاميرا", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }
}
