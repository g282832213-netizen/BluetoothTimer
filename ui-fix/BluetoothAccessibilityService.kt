package com.example.bluetoothtimer

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BluetoothAccessibilityService : AccessibilityService() {
    private var actionPending = false
    private var clickAttempted = false
    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_CLOSE_BLUETOOTH) return
            actionPending = true
            clickAttempted = false
            val settings = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(settings)
                retryFindSwitch(0)
            } catch (_: Exception) {
                actionPending = false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(ACTION_CLOSE_BLUETOOTH)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!actionPending || clickAttempted || event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            tryCloseSwitch()
        }
    }

    private fun retryFindSwitch(attempt: Int) {
        if (!actionPending || clickAttempted || attempt >= 12) return
        handler.postDelayed({
            tryCloseSwitch()
            if (actionPending && !clickAttempted) retryFindSwitch(attempt + 1)
        }, if (attempt == 0) 500L else 250L)
    }

    private fun tryCloseSwitch() {
        val root = rootInActiveWindow ?: return
        val switchNode = findBluetoothSwitch(root) ?: return
        if (switchNode.isChecked) {
            val clickable = clickableAncestor(switchNode) ?: switchNode
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                clickAttempted = true
                actionPending = false
            }
        } else {
            actionPending = false
        }
    }

    private fun findBluetoothSwitch(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val cls = node.className?.toString().orEmpty()
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        val label = (node.text?.toString().orEmpty() + " " + node.contentDescription?.toString().orEmpty()).lowercase()
        val looksLikeSwitch = cls.contains("switch") || cls.contains("compoundbutton") ||
            id.contains("switch") || id.contains("bluetooth")
        if (looksLikeSwitch && node.isCheckable) {
            if (node.isChecked || label.contains("bluetooth") || id.contains("bluetooth") || id.contains("switch")) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findBluetoothSwitch(it) }
            if (found != null) return found
        }
        return null
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(8) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val ACTION_CLOSE_BLUETOOTH = "com.example.bluetoothtimer.CLOSE_BLUETOOTH"
    }
}