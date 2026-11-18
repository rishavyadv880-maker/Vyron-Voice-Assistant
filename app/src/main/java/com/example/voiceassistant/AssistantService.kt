package com.example.voiceassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantService : AccessibilityService() {
    companion object {
        @Volatile var instance: AssistantService? = null
            private set
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    fun clickText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(target)
        for (node in nodes) {
            if (clickNode(node)) return true
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.isEnabled) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.isEnabled) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            parent = parent.parent
        }
        return false
    }

    fun typeText(value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) })
    }

    fun tapSendAfterDelay(delayMs: Long = 3500L) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!clickText("Send") && !clickText("send")) clickText("भेजें")
        }, delayMs)
    }

    fun tapCenter(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build()
        dispatchGesture(gesture, null, null)
    }
}
