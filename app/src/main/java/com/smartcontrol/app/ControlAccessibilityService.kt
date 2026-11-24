package com.smartcontrol.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class ControlAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: ControlAccessibilityService? = null }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    fun swipe(up: Boolean) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val startY = if (up) metrics.heightPixels * 0.78f else metrics.heightPixels * 0.25f
        val endY = if (up) metrics.heightPixels * 0.25f else metrics.heightPixels * 0.78f
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 450)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
