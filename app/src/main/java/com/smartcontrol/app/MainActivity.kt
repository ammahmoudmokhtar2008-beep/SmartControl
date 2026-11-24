package com.smartcontrol.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
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
    private lateinit var voiceState: TextView
    private lateinit var gestureState: TextView
    private val prefs by lazy { getSharedPreferences("smart_control", MODE_PRIVATE) }

    private val navy = Color.rgb(8, 13, 28)
    private val panel = Color.rgb(19, 29, 51)
    private val cyan = Color.rgb(51, 220, 198)
    private val muted = Color.rgb(158, 174, 198)
    private val white = Color.rgb(245, 248, 255)

    private fun masterOn() = prefs.getBoolean("master_on", false)
    private fun voiceOn() = prefs.getBoolean("voice_on", false)
    private fun gesturesOn() = prefs.getBoolean("gestures_on", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshButtons()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "خدمة التعرّف على الكلام غير متاحة. فعّل خدمة Speech Services by Google."
        }
    }

    private fun rounded(color: Int, radius: Float = 22f, stroke: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
            if (stroke != Color.TRANSPARENT) setStroke((1 * resources.displayMetrics.density).toInt(), stroke)
        }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 2, 0, 2)
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(panel, 22f, Color.rgb(39, 57, 86))
        elevation = dp(2).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun actionButton(text: String, primary: Boolean = false): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            setTextColor(if (primary) navy else white)
            background = rounded(if (primary) cyan else Color.rgb(37, 51, 78), 15f)
            minHeight = dp(54)
            stateListAnimator = null
        }

    private fun addGap(parent: LinearLayout, height: Int = 14) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(10, 18, 38), navy, Color.rgb(8, 13, 28))
            )
        }

        val topLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = label("الصياد", 30f, white, true).apply { gravity = Gravity.RIGHT }
        val brandSub = label("SMART CONTROL", 10f, cyan, true).apply {
            letterSpacing = 0.22f
            gravity = Gravity.RIGHT
        }
        val brandStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(brand)
            addView(brandSub)
        }
        val version = label("الإصدار 0.4", 11f, muted).apply {
            background = rounded(Color.rgb(24, 42, 66), 30f)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        topLine.addView(brandStack, LinearLayout.LayoutParams(0, -2, 1f))
        topLine.addView(version)
        page.addView(topLine)
        addGap(page, 22)

        page.addView(label("خلّي إيدك على راحتك…\nوتحكّم في موبايلك بصوتك", 22f, white, true))
        addGap(page, 8)
        page.addView(label("أوامرك الصوتية هي الأساس. والإيماءات اختيار إضافي.", 13f, muted))
        addGap(page, 20)

        val statusCard = card()
        val statusTitle = label("حالة النظام", 14f, muted, true)
        status = label("جاهز للتشغيل — فعّل التحكم للبدء", 14f, white)
        status.setPadding(0, dp(8), 0, 0)
        statusCard.addView(statusTitle)
        statusCard.addView(status)
        page.addView(statusCard)
        addGap(page, 16)

        val voiceCard = card().apply {
            background = rounded(Color.rgb(20, 43, 61), 24f, Color.rgb(44, 119, 126))
        }
        voiceCard.addView(label("★  التحكم الصوتي", 21f, cyan, true))
        addGap(voiceCard, 5)
        voiceCard.addView(label("الميزة الرئيسية", 12f, muted))
        addGap(voiceCard, 12)
        voiceState = label("متوقف", 15f, white, true)
        voiceCard.addView(voiceState)
        addGap(voiceCard, 8)
        voiceButton = actionButton("تشغيل الأوامر الصوتية", true)
        voiceButton.setOnClickListener { toggleVoice() }
        voiceCard.addView(voiceButton, LinearLayout.LayoutParams(-1, dp(56)))
        addGap(voiceCard, 10)
        voiceCard.addView(label("جرّب: افتح واتساب • افتح يوتيوب • ارفع الصوت • ارجع", 12f, muted))
        page.addView(voiceCard)
        addGap(page, 16)

        val gestureCard = card()
        gestureCard.addView(label("إشارات اليد", 18f, white, true))
        addGap(gestureCard, 4)
        gestureCard.addView(label("ميزة إضافية — تعمل بالكاميرا الأمامية", 12f, muted))
        addGap(gestureCard, 8)
        gestureState = label("متوقفة", 14f, white)
        gestureCard.addView(gestureState)
        addGap(gestureCard, 8)
        gestureButton = actionButton("تشغيل إشارات اليد")
        gestureButton.setOnClickListener { toggleGestures() }
        gestureCard.addView(gestureButton, LinearLayout.LayoutParams(-1, dp(52)))
        page.addView(gestureCard)
        addGap(page, 16)

        masterButton = actionButton("تفعيل التحكم")
        masterButton.setOnClickListener { toggleMaster() }
        page.addView(masterButton, LinearLayout.LayoutParams(-1, dp(54)))
        addGap(page, 10)

        val settingsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val accessButton = actionButton("إعدادات الوصول")
        accessButton.textSize = 13f
        accessButton.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val permissionButton = actionButton("أذونات التطبيق")
        permissionButton.textSize = 13f
        permissionButton.setOnClickListener {
            disableEverything()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            status.text = "أوقفنا الخدمات. راجع أذونات الكاميرا والميكروفون من صفحة التطبيق."
        }
        settingsRow.addView(accessButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) })
        settingsRow.addView(permissionButton, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })
        page.addView(settingsRow)
        addGap(page, 20)

        val help = card()
        help.addView(label("أوامر تقدر تقولها", 16f, white, true))
        addGap(help, 8)
        help.addView(label("«افتح واتساب»  •  «افتح يوتيوب»\n«افتح فيسبوك»  •  «افتح الكاميرا»\n«ارفع الصوت»  •  «اخفض الصوت»\n«مرر لفوق»  •  «مرر لتحت»  •  «ارجع»", 13f, muted))
        addGap(help, 14)
        help.addView(label("معلومة مهمة", 14f, cyan, true))
        help.addView(label("الصوت يقدر يفتح التطبيقات ويتحكم في مستوى الصوت بدون خدمة إمكانية الوصول. أوامر الرجوع والتمرير وبعض وظائف الإيماءات تحتاج تفعيل الخدمة.", 12f, muted))
        page.addView(help)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(page)
        }
        setContentView(scroll)
    }

    private fun refreshButtons() {
        masterButton.text = if (masterOn()) "إيقاف التحكم بالكامل" else "تفعيل التحكم"
        voiceButton.text = if (voiceOn()) "إيقاف الأوامر الصوتية" else "تشغيل الأوامر الصوتية"
        gestureButton.text = if (gesturesOn()) "إيقاف إشارات اليد" else "تشغيل إشارات اليد"
        voiceState.text = if (voiceOn()) "● الاستماع الصوتي نشط" else "○ الصوت متوقف حالياً"
        gestureState.text = if (gesturesOn()) "● الكاميرا والإيماءات نشطة" else "○ الإيماءات متوقفة حالياً"
        voiceButton.isEnabled = masterOn()
        gestureButton.isEnabled = masterOn()
        voiceButton.alpha = if (masterOn()) 1f else 0.55f
        gestureButton.alpha = if (masterOn()) 1f else 0.55f
        masterButton.background = rounded(if (masterOn()) Color.rgb(54, 67, 88) else cyan, 15f)
        masterButton.setTextColor(if (masterOn()) white else navy)
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
            status.text = "تم إيقاف خدمات الصوت والكاميرا."
        } else {
            prefs.edit().putBoolean("master_on", true).apply()
            status.text = "التحكم العام مفعّل. شغّل الأوامر الصوتية أولاً."
        }
        refreshButtons()
    }

    private fun toggleVoice() {
        if (!masterOn()) {
            status.text = "فعّل التحكم العام أولاً."
            return
        }
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
                status.text = "فعّل خدمة Speech Services by Google أولاً."
                return
            }
            prefs.edit().putBoolean("voice_on", true).apply()
            try {
                ContextCompat.startForegroundService(this, Intent(this, VoiceControlService::class.java))
                status.text = "الأوامر الصوتية بدأت. سيظهر إشعار أثناء الاستماع."
            } catch (e: Exception) {
                prefs.edit().putBoolean("voice_on", false).apply()
                status.text = "تعذّر تشغيل الصوت: ${e.localizedMessage ?: "تحقق من الإذن"}"
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
                status.text = "إشارات اليد تحتاج تفعيل خدمة الصياد من إعدادات إمكانية الوصول."
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
                status.text = "الكاميرا بدأت تتبع اليد."
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
                10 -> {
                    prefs.edit().putBoolean("master_on", true).putBoolean("voice_on", true).apply()
                    ContextCompat.startForegroundService(this, Intent(this, VoiceControlService::class.java))
                    status.text = "الأوامر الصوتية تعمل."
                }
                11 -> {
                    if (!isAccessibilityEnabled()) {
                        prefs.edit().putBoolean("gestures_on", false).apply()
                        status.text = "فعّل إمكانية الوصول ثم شغّل الإشارات."
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

    override fun onResume() { super.onResume(); if (::masterButton.isInitialized) refreshButtons() }

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
