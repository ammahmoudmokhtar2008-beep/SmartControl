package com.smartcontrol.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
    private lateinit var status: TextView
    private lateinit var masterButton: Button
    private lateinit var voiceButton: Button
    private lateinit var gestureButton: Button
    private val prefs by lazy { getSharedPreferences("smart_control", MODE_PRIVATE) }
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    private fun masterOn() = prefs.getBoolean("master_on", false)
    private fun voiceOn() = prefs.getBoolean("voice_on", false)
    private fun gesturesOn() = prefs.getBoolean("gestures_on", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshButtons()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) status.text = "التعرف على الصوت غير متاح على هذا الجهاز."
    }

    private fun buildUi() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val title = TextView(this).apply {
            text = "SmartControl"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val byline = TextView(this).apply {
            text = "by Elsayyad"
            textSize = 14f
            setTextColor(Color.rgb(103, 232, 249))
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "فعّل التحكم العام، ثم شغّل الصوت أو إشارات اليد."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        }
        masterButton = Button(this).apply {
            setOnClickListener { toggleMaster() }
        }
        voiceButton = Button(this).apply {
            setOnClickListener { toggleVoice() }
        }
        gestureButton = Button(this).apply {
            setOnClickListener { toggleGestures() }
        }
        val accessButton = Button(this).apply {
            text = "تفعيل صلاحية التحكم العام"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val permissionButton = Button(this).apply {
            text = "إدارة صلاحيات الكاميرا والمايك والتحكم"
            setOnClickListener {
                stopAllServices()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                startActivity(intent)
                status.text = "أوقفنا خدمات التطبيق. لإلغاء الصلاحيات نهائيًا ألغِ الكاميرا والميكروفون من صفحة معلومات التطبيق، وأوقف SmartControl من إعدادات إمكانية الوصول."
            }
        }
        val help = TextView(this).apply {
            text = "الأوامر الصوتية: قل «افتح» ثم اسم التطبيق، أو قل رجوع، الرئيسية، الإشعارات، الإعدادات، ارفع الصوت، اخفض الصوت، مرر لأعلى أو لأسفل.\n\nإشارات اليد: الإبهام لأعلى/لأسفل للتمرير، وتحريك أربعة أصابع لأعلى/لأسفل للتحكم بالصوت."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 0)
        }
        listOf(title, byline, status, masterButton, voiceButton, gestureButton, accessButton, permissionButton, help).forEach {
            layout.addView(it)
        }
        setContentView(layout)
    }

    private fun refreshButtons() {
        masterButton.text = if (masterOn()) "إيقاف SmartControl بالكامل" else "تفعيل SmartControl"
        voiceButton.text = if (voiceOn()) "إيقاف الأوامر الصوتية" else "تشغيل الأوامر الصوتية"
        gestureButton.text = if (gesturesOn()) "إيقاف أوامر الإشارة" else "تشغيل أوامر الإشارة"
        voiceButton.isEnabled = masterOn()
        gestureButton.isEnabled = masterOn()
    }

    private fun toggleMaster() {
        if (masterOn()) {
            prefs.edit().putBoolean("master_on", false).putBoolean("voice_on", false).putBoolean("gestures_on", false).apply()
            stopAllServices()
            status.text = "تم إيقاف الصوت والكاميرا وخدمات التحكم. الصلاحيات التي منحها أندرويد لا يمكن للتطبيق إلغاؤها بنفسه؛ استخدم زر إدارة الصلاحيات لإلغائها يدويًا."
        } else {
            prefs.edit().putBoolean("master_on", true).apply()
            status.text = "تم تفعيل SmartControl. شغّل الصوت أو الإشارات كلٌ على حدة."
        }
        refreshButtons()
    }

    private fun toggleVoice() {
        if (!masterOn()) return
        if (voiceOn()) {
            prefs.edit().putBoolean("voice_on", false).apply()
            stopService(Intent(this, VoiceControlService::class.java))
            status.text = "تم إيقاف الاستماع الصوتي."
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
                return
            }
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                status.text = "خدمة التعرف على الصوت غير متاحة."
                return
            }
            prefs.edit().putBoolean("voice_on", true).apply()
            ContextCompat.startForegroundService(this, Intent(this, VoiceControlService::class.java))
            status.text = "الأوامر الصوتية تعمل في الخلفية. يمكنك الانتقال لأي تطبيق."
        }
        refreshButtons()
    }

    private fun toggleGestures() {
        if (!masterOn()) return
        if (gesturesOn()) {
            prefs.edit().putBoolean("gestures_on", false).apply()
            stopService(Intent(this, GestureCameraService::class.java))
            status.text = "تم إيقاف الكاميرا والتحكم بالإشارات."
        } else {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 11)
                return
            }
            prefs.edit().putBoolean("gestures_on", true).apply()
            ContextCompat.startForegroundService(this, Intent(this, GestureCameraService::class.java))
            status.text = "إشارات اليد تعمل في الخلفية. ستظهر علامة خدمة الكاميرا أثناء التشغيل."
        }
        refreshButtons()
    }

    private fun stopAllServices() {
        stopService(Intent(this, VoiceControlService::class.java))
        stopService(Intent(this, GestureCameraService::class.java))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                10 -> { prefs.edit().putBoolean("master_on", true).putBoolean("voice_on", true).apply(); ContextCompat.startForegroundService(this, Intent(this, VoiceControlService::class.java)); status.text = "الأوامر الصوتية تعمل." }
                11 -> { prefs.edit().putBoolean("master_on", true).putBoolean("gestures_on", true).apply(); ContextCompat.startForegroundService(this, Intent(this, GestureCameraService::class.java)); status.text = "إشارات اليد تعمل." }
            }
        } else status.text = "لم يتم منح الصلاحية المطلوبة."
        refreshButtons()
    }

    override fun onResume() { super.onResume(); refreshButtons() }

    companion object {
        fun executeCommand(context: android.content.Context, raw: String) {
            val s = raw.lowercase(Locale("ar", "EG")).replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
            val service = ControlAccessibilityService.instance
            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            when {
                s.contains("ارجع") || s.contains("رجوع") || s.contains("ورا") -> service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                s.contains("الرئيسية") || s.contains("الشاشة الرئيسية") -> service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                s.contains("الاشعارات") || s.contains("اشعارات") -> service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                s.contains("الاعدادات") -> launchPackage(context, "com.android.settings")
                s.contains("ارفع الصوت") || s.contains("علي الصوت") -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                s.contains("اخفض الصوت") || s.contains("وطي الصوت") -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                s.contains("اكتم الصوت") -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                s.contains("مرر لفوق") || s.contains("اسكرول لفوق") || s.contains("اطلع لفوق") -> service?.swipe(true)
                s.contains("مرر لتحت") || s.contains("اسكرول لتحت") || s.contains("انزل لتحت") -> service?.swipe(false)
                s.contains("كاميرا") -> try { context.startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                else -> {
                    val requested = s.removePrefix("افتح").removePrefix("شغل").trim()
                    if (requested.isNotBlank()) launchByName(context, requested)
                    else Toast.makeText(context, "لم أفهم الأمر. جرّب: افتح واتساب", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun launchPackage(context: android.content.Context, pkg: String) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent != null) context.startActivity(intent)
            else Toast.makeText(context, "التطبيق غير مثبت", Toast.LENGTH_SHORT).show()
        }

        private fun launchByName(context: android.content.Context, requested: String) {
            val aliases = mapOf(
                "واتساب" to "com.whatsapp", "واتس" to "com.whatsapp", "فيسبوك" to "com.facebook.katana",
                "فيس" to "com.facebook.katana", "ماسنجر" to "com.facebook.orca", "انستجرام" to "com.instagram.android",
                "انستا" to "com.instagram.android", "تيك توك" to "com.zhiliaoapp.musically", "تيكتوك" to "com.zhiliaoapp.musically",
                "يوتيوب" to "com.google.android.youtube", "تليجرام" to "org.telegram.messenger", "تلجرام" to "org.telegram.messenger",
                "سناب شات" to "com.snapchat.android", "سناب" to "com.snapchat.android", "اكس" to "com.twitter.android",
                "تويتر" to "com.twitter.android", "لينكد ان" to "com.linkedin.android", "بنترست" to "com.pinterest",
                "سبوتيفاي" to "com.spotify.music", "جيميل" to "com.google.android.gm", "خرائط جوجل" to "com.google.android.apps.maps",
                "جوجل كروم" to "com.android.chrome", "كروم" to "com.android.chrome", "جوجل" to "com.google.android.googlequicksearchbox",
                "جوجل درايف" to "com.google.android.apps.docs", "صور جوجل" to "com.google.android.apps.photos",
                "جوجل بلاي" to "com.android.vending", "متجر بلاي" to "com.android.vending", "الكاميرا" to "camera"
            )
            val match = aliases.entries.firstOrNull { requested.contains(it.key) }
            if (match != null) {
                if (match.value == "camera") {
                    try { context.startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                } else launchPackage(context, match.value)
                return
            }
            val apps = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val app = apps.firstOrNull {
                context.packageManager.getApplicationLabel(it).toString().lowercase(Locale("ar", "EG")).contains(requested)
            }
            if (app != null) {
                context.packageManager.getLaunchIntentForPackage(app.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            } else Toast.makeText(context, "مش لاقي تطبيق باسم $requested", Toast.LENGTH_SHORT).show()
        }
    }
}
