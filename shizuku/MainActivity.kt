package com.example.bluetoothtimer

import android.Manifest
import android.app.*
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.graphics.Color
import android.view.*
import android.widget.*
import android.text.*
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("presets", MODE_PRIVATE) }
    private lateinit var status: TextView
    private val fields = mutableListOf<EditText>()
    private var slot = 0
    private var loading = false
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        status.text = if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) "Shizuku 已授权，可以开始倒计时" else "未获得 Shizuku 授权"
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
            setBackgroundColor(Color.WHITE)
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(dp(20) + insets.systemWindowInsetLeft, dp(20) + insets.systemWindowInsetTop,
                dp(20) + insets.systemWindowInsetRight, dp(24) + insets.systemWindowInsetBottom)
            insets
        }
        fun text(label: String, size: Float) = TextView(this).apply {
            text = label; textSize = size; setTextColor(Color.rgb(30,30,30))
        }
        root.addView(text("蓝牙定时关闭", 24f), params())
        status = text("先启动 Shizuku，再点击授权", 15f)
        root.addView(status, params())
        val selector = Spinner(this)
        selector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("倒计时 1", "倒计时 2", "倒计时 3"))
        root.addView(selector, params())
        for (label in arrayOf("小时", "分钟", "秒")) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(label, 16f), LinearLayout.LayoutParams(dp(64), -2))
            val input = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                textSize = 20f; setTextColor(Color.BLACK); setSingleLine(true)
                minHeight = dp(52)
                filters = arrayOf(InputFilter.LengthFilter(3))
            }
            row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            fields.add(input)
            root.addView(row, params())
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    if (!loading) save()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label; textSize = 16f; minimumHeight = dp(52)
                setOnClickListener { action() }
            }, params())
        }
        button("授权 Shizuku") {
            try {
                if (!Shizuku.pingBinder()) status.text = "请先在 Shizuku 应用中启动服务"
                else if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) status.text = "Shizuku 已授权"
                else Shizuku.requestPermission(17)
            } catch (_: Exception) { status.text = "Shizuku 不可用，请先启动服务" }
        }
        button("开始倒计时") { startTimer() }
        button("取消倒计时") {
            startForegroundService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_CANCEL))
            status.text = "倒计时已取消"
        }
        root.addView(text("每个预设自动保存。到点只关闭蓝牙，无需无障碍权限。", 13f), params())
        setContentView(ScrollView(this).apply { setFillViewport(true); addView(root) })
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                slot = position; prefs.edit().putInt("selected", slot).apply(); restore()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        slot = prefs.getInt("selected", 0).coerceIn(0,2)
        selector.setSelection(slot)
        restore()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
    }
    private fun params() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    private fun save() {
        val edit = prefs.edit()
        fields.forEachIndexed { i, f -> edit.putString("preset_${slot}_$i", f.text.toString()) }
        edit.apply()
    }
    private fun restore() {
        loading = true
        fields.forEachIndexed { i, f -> f.setText(prefs.getString("preset_${slot}_$i", if (i == 1) "30" else "0")) }
        loading = false
    }
    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            val timer = getSharedPreferences("timer", MODE_PRIVATE)
            val end = timer.getLong("end", 0)
            status.text = if (end > SystemClock.elapsedRealtime()) "倒计时进行中，通知栏可查看剩余时间"
                else timer.getString("result", "先启动 Shizuku，再点击授权")
        }
    }
    private fun startTimer() {
        save()
        val values = fields.map { it.text.toString().toLongOrNull() ?: 0L }
        val total = values[0] * 3600 + values[1] * 60 + values[2]
        if (values[1] !in 0..59 || values[2] !in 0..59 || total !in 1..604800) {
            status.text = "分钟和秒须为 0–59，总时长须为 1 秒至 7 天"; return
        }
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                status.text = "请先启动 Shizuku，并点击授权"; return
            }
            if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
                status.text = "请允许闹钟和提醒，再点击开始"
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:$packageName"))); return
            }
            startForegroundService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_START)
                .putExtra(TimerService.EXTRA_SECONDS, total))
            status.text = "倒计时已开始"
        } catch (_: Exception) { status.text = "启动失败，请确认 Shizuku 正在运行" }
    }
    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
