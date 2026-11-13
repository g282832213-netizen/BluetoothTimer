package com.example.bluetoothtimer

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.*

class MainActivity : Activity() {
    private lateinit var hours: EditText
    private lateinit var minutes: EditText
    private lateinit var seconds: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        buildUi()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
        }
        layout.addView(TextView(this).apply {
            text = "蓝牙定时关闭"
            textSize = 21f
            gravity = Gravity.CENTER
            maxLines = 1
        }, fullWrap(0))

        status = TextView(this).apply {
            text = "设置倒计时时长"
            textSize = 16f
            gravity = Gravity.CENTER
            maxLines = 2
        }
        layout.addView(status, fullWrap(12))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        hours = numberInput("小时")
        minutes = numberInput("分钟")
        seconds = numberInput("秒")
        row.addView(hours, weight())
        row.addView(minutes, weight())
        row.addView(seconds, weight())
        layout.addView(row, fullWrap(12))

        layout.addView(makeButton("开始倒计时") { startTimer() }, fullWrap(4))
        layout.addView(makeButton("取消倒计时") {
            startService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_CANCEL))
            status.text = "已取消"
        }, fullWrap(4))
        layout.addView(makeButton("开启必要的无障碍权限") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, fullWrap(4))
        layout.addView(TextView(this).apply {
            text = "安全规则：只会在明确识别到蓝牙处于开启状态时执行关闭；已关闭或状态不明确时绝不点击。"
            textSize = 12f
            setPadding(0, pad, 0, 0)
            maxLines = 4
        }, fullWrap(0))
        setContentView(ScrollView(this).apply {
            setFillViewport(true)
            addView(layout)
        })
    }

    private fun numberInput(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER
        textSize = 16f
        setSingleLine(true)
        minHeight = (48 * resources.displayMetrics.density).toInt()
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun startTimer() {
        if (!isAccessibilityEnabled()) {
            status.text = "请先开启无障碍权限"
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val h = hours.text.toString().toLongOrNull() ?: 0L
        val m = minutes.text.toString().toLongOrNull() ?: 0L
        val s = seconds.text.toString().toLongOrNull() ?: 0L
        val total = h * 3600 + m * 60 + s
        if (total <= 0 || total > 7L * 24 * 3600) {
            status.text = "请输入 1 秒至 7 天之间的时间"
            return
        }
        val i = Intent(this, TimerService::class.java)
            .setAction(TimerService.ACTION_START)
            .putExtra(TimerService.EXTRA_SECONDS, total)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        status.text = "倒计时已开始"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name.endsWith("BluetoothAccessibilityService") }
    }

    private fun fullWrap(bottomDp: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = (bottomDp * resources.displayMetrics.density).toInt() }

    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginEnd = (6 * resources.displayMetrics.density).toInt()
    }
}