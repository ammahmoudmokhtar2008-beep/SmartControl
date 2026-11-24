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
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun swipe(up: Boolean) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val startY = if (up) metrics.heightPixels * 0.78f else metrics.heightPixels * 0.25f
        val endY = if (up) metrics.heightPixels * 0.25f else metrics.heightPixels * 0.78f
        dispatchPath(x, startY, x, endY)
    }

    fun swipeHorizontal(left: Boolean) {
        val metrics = resources.displayMetrics
        val y = metrics.heightPixels * 0.5f
        val startX = if (left) metrics.widthPixels * 0.82f else metrics.widthPixels * 0.18f
        val endX = if (left) metrics.widthPixels * 0.18f else metrics.widthPixels * 0.82f
        dispatchPath(startX, y, endX, y)
    }

    private fun dispatchPath(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 400)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
