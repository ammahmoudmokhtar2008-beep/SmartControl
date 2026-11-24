package com.smartcontrol.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var masterButton: Button
    private lateinit var voiceButton: Button
    private lateinit var gestureButton: Button
    private val prefs by lazy { getSharedPreferences("smart_control", MODE_PRIVATE) }

    private fun masterOn() = prefs.getBoolean("master_on", false)
    private fun voiceOn() = prefs.getBoolean("voice_on", false)
    private fun gesturesOn() = prefs.getBoolean("gestures_on", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshButtons()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "خدمة التعرف على الكلام غير متاحة على الجهاز. ثبّت أو فعّل Speech Services by Google."
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(Color.rgb(9, 16, 32))
        }
        val title = TextView(this).apply {
            text = "ELSAYYAD"
            textSize = 13f
            letterSpacing = 0.28f
            setTextColor(Color.rgb(45, 212, 191))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "SMART CONTROL"
            textSize = 29f
            letterSpacing = 0.05f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val tagline = TextView(this).apply {
            text = "تحكّم في هاتفك بالصوت وإشارات اليد"
            textSize = 15f
            setTextColor(Color.rgb(148, 163, 184))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }
        status = TextView(this).apply {
            text = "ابدأ بتفعيل التحكم العام، ثم اختر الصوت أو إشارات اليد."
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(14, 18, 14, 18)
            setBackgroundColor(Color.rgb(22, 35, 58))
        }
        masterButton = makeButton()
        masterButton.setOnClickListener { toggleMaster() }
        voiceButton = makeButton()
        voiceButton.setOnClickListener { toggleVoice() }
        gestureButton = makeButton()
        gestureButton.setOnClickListener { toggleGestures() }
        val accessButton = makeButton("فتح إعدادات إمكانية الوصول").apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val permissionButton = makeButton("إدارة أذونات الكاميرا والميكروفون").apply {
            setOnClickListener {
                disableEverything()
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                status.text = "أوقفنا الخدمات. يمكنك مراجعة أذونات الكاميرا والميكروفون من صفحة التطبيق."
            }
        }
        val help = TextView(this).apply {
            text = "الأوامر الصوتية\n«افتح واتساب» • «افتح يوتيوب» • «افتح فيسبوك» • «ارجع» • «الرئيسية» • «ارفع الصوت» • «اخفض الصوت» • «مرر لفوق» • «مرر لتحت»\n\nإشارات اليد\nالإبهام لأعلى/لأسفل للتمرير. حرّك أربعة أصابع لأعلى/لأسفل للتحكم في الصوت. اقبض يدك للرجوع.\n\nمهم: يلزم تفعيل خدمة SmartControl من إعدادات إمكانية الوصول حتى تعمل أوامر التحكم خارج التطبيق."
            textSize = 14f
            setTextColor(Color.rgb(203, 213, 225))
            gravity = Gravity.CENTER
            setPadding(8, 20, 8, 8)
        }
        listOf(title, subtitle, tagline, status, masterButton, voiceButton, gestureButton, accessButton, permissionButton, help)
            .forEach { root.addView(it, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun makeButton(label: String = "") = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
    }

    private fun refreshButtons() {
        masterButton.text = if (masterOn()) "إيقاف التحكم بالكامل" else "تفعيل التحكم"
        voiceButton.text = if (voiceOn()) "إيقاف الأوامر الصوتية" else "تشغيل الأوامر الصوتية"
        gestureButton.text = if (gesturesOn()) "إيقاف إشارات اليد" else "تشغيل إشارات اليد"
        voiceButton.isEnabled = masterOn()
        gestureButton.isEnabled = masterOn()
    }

    private fun disableEverything() {
        prefs.edit().putBoolean("master_on", false).putBoolean("voice_on", false).putBoolean("gestures_on", false).apply()
        stopService(Intent(this, VoiceControlService::class.java))
        stopService(Intent(this, GestureCameraService::class.java))
        refreshButtons()
    }

    private fun toggleMaster() {
        if (masterOn()) {
            disableEverything()
            status.text = "تم إيقاف خدمات الصوت والكاميرا. أندرويد لا يسمح للتطبيق بإلغاء أذوناته بنفسه."
        } else {
            prefs.edit().putBoolean("master_on", true).apply()
            status.text = "التحكم مفعّل. شغّل الصوت أو إشارات اليد."
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
                status.text = "فعّل Speech Services by Google أو خدمة التعرف على الكلام أولاً."
                return
            }
            prefs.edit().putBoolean("voice_on", true).apply()
            try {
                ContextCompat.startForegroundService(this, Intent(this, VoiceControlService::class.java))
                status.text = "بدأ الاستماع في الخلفية. اترك إشعار SmartControl ظاهرًا أثناء الاستخدام."
            } catch (e: Exception) {
                prefs.edit().putBoolean("voice_on", false).apply()
                status.text = "تعذّر تشغيل الميكروفون: ${e.localizedMessage ?: "تحقق من الأذونات"}"
            }
        }
        refreshButtons()
    }

    private fun toggleGestures() {
        if (!masterOn()) return
        if (gesturesOn()) {
            prefs.edit().putBoolean("gestures_on", false).apply()
            stopService(Intent(this, GestureCameraService::class.java))
            status.text = "تم إيقاف الكاميرا وإشارات اليد."
        } else {
            if (!isAccessibilityEnabled()) {
                status.text = "فعّل خدمة Elsayyad Smart Control من إعدادات إمكانية الوصول أولاً، ثم ارجع وشغّل الإشارات."
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 11)
                return
            }
            prefs.edit().putBoolean("gestures_on", true).apply()
            try {
                ContextCompat.startForegroundService(this, Intent(this, GestureCameraService::class.java))
                status.text = "الكاميرا بدأت تتبع اليد. استخدم إشارة واضحة أمام الكاميرا الأمامية."
            } catch (e: Exception) {
                prefs.edit().putBoolean("gestures_on", false).apply()
                status.text = "تعذّر تشغيل الكاميرا: ${e.localizedMessage ?: "تحقق من الأذونات"}"
            }
        }
        refreshButtons()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, ControlAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                10 -> { prefs.edit().putBoolean("master_on", true).putBoolean("voice_on", true).apply(); ContextCompat.startForegroundService(this, Intent(this, VoiceControlService::class.java)); status.text = "الأوامر الصوتية تعمل." }
                11 -> {
                    if (!isAccessibilityEnabled()) {
                        prefs.edit().putBoolean("gestures_on", false).apply()
                        status.text = "الكاميرا مسموح بها. فعّل إمكانية الوصول ثم شغّل الإشارات."
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } else {
                        prefs.edit().putBoolean("master_on", true).putBoolean("gestures_on", true).apply()
                        ContextCompat.startForegroundService(this, Intent(this, GestureCameraService::class.java))
                        status.text = "إشارات اليد تعمل."
                    }
                }
            }
        } else status.text = "لم يتم منح الإذن المطلوب."
        refreshButtons()
    }

    override fun onResume() { super.onResume(); refreshButtons() }

    companion object {
        private fun normalize(value: String): String {
            val noMarks = Normalizer.normalize(value.lowercase(Locale("ar", "EG")), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            return noMarks.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
        }

        fun executeCommand(context: android.content.Context, raw: String) {
            val s = normalize(raw)
            val access = ControlAccessibilityService.instance
            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            when {
                listOf("ارجع", "رجوع", "ورا", "back").any(s::contains) -> access?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                listOf("الرئيسيه", "الشاشه الرئيسيه", "افتح الرئيسيه", "home").any(s::contains) -> access?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                listOf("الاشعارات", "اشعارات", "افتح الاشعارات").any(s::contains) -> access?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                s.contains("الاعدادات") -> launchPackage(context, "com.android.settings")
                listOf("ارفع الصوت", "علي الصوت", "زود الصوت").any(s::contains) -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                listOf("اخفض الصوت", "وطي الصوت", "قلل الصوت").any(s::contains) -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                listOf("اكتم الصوت", "اقفل الصوت").any(s::contains) -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0)
                listOf("مرر لفوق", "اسكرول لفوق", "اطلع لفوق", "انزل لفوق").any(s::contains) -> access?.swipe(true)
                listOf("مرر لتحت", "اسكرول لتحت", "انزل لتحت").any(s::contains) -> access?.swipe(false)
                s.contains("كاميرا") -> try { context.startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                else -> {
                    val requested = s.replace(Regex("^(من فضلك )?(افتح|شغل|روح|ادخل)(لي)?( تطبيق)? "), "")
                        .replace(Regex("^(من فضلك )?(افتح|شغل|روح|ادخل)(لي)?$"), "").trim()
                    if (requested.isNotBlank()) launchByName(context, requested)
                    else Toast.makeText(context, "لم أفهم الأمر. مثال: افتح واتساب", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun launchPackage(context: android.content.Context, pkg: String) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent != null) context.startActivity(intent)
            else Toast.makeText(context, "التطبيق غير مثبت أو لا يملك شاشة تشغيل.", Toast.LENGTH_SHORT).show()
        }

        private fun launchByName(context: android.content.Context, requested: String) {
            val aliases = linkedMapOf(
                "خرائط جوجل" to "com.google.android.apps.maps", "جوجل درايف" to "com.google.android.apps.docs",
                "صور جوجل" to "com.google.android.apps.photos", "سناب شات" to "com.snapchat.android",
                "تيك توك" to "com.zhiliaoapp.musically", "لينكد ان" to "com.linkedin.android",
                "ماسنجر" to "com.facebook.orca", "انستجرام" to "com.instagram.android",
                "فيسبوك" to "com.facebook.katana", "تليجرام" to "org.telegram.messenger",
                "تلجرام" to "org.telegram.messenger", "يوتيوب" to "com.google.android.youtube",
                "واتساب" to "com.whatsapp", "واتس" to "com.whatsapp", "فيس" to "com.facebook.katana",
                "انستا" to "com.instagram.android", "تيكتوك" to "com.zhiliaoapp.musically",
                "سناب" to "com.snapchat.android", "تويتر" to "com.twitter.android", "اكس" to "com.twitter.android",
                "بنترست" to "com.pinterest", "سبوتيفاي" to "com.spotify.music", "جيميل" to "com.google.android.gm",
                "كروم" to "com.android.chrome", "جوجل كروم" to "com.android.chrome",
                "جوجل بلاي" to "com.android.vending", "متجر بلاي" to "com.android.vending"
            )
            val key = aliases.keys.sortedByDescending { it.length }.firstOrNull { requested.contains(it) }
            if (key != null) { launchPackage(context, aliases.getValue(key)); return }

            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val target = apps.firstOrNull { normalize(pm.getApplicationLabel(it).toString()) == requested }
                ?: apps.firstOrNull {
                    val label = normalize(pm.getApplicationLabel(it).toString())
                    label.isNotBlank() && (label.contains(requested) || requested.contains(label))
                }
            val launch = target?.let { pm.getLaunchIntentForPackage(it.packageName) }
            if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else Toast.makeText(context, "مش لاقي تطبيق باسم «$requested». جرّب نطق اسمه بوضوح.", Toast.LENGTH_LONG).show()
        }
    }
}