package com.example.bluetoothtimer

import android.app.*
import android.content.*
import android.os.*
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("timer", MODE_PRIVATE) }
    private var remote: IBluetoothOff? = null
    private var finishing = false
    private var bound = false
    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(this, PrivilegedBluetooth::class.java))
            .daemon(false).processNameSuffix("bluetooth_off").version(3)
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            remote = IBluetoothOff.Stub.asInterface(binder)
            if (finishing) executeClose()
        }
        override fun onServiceDisconnected(name: ComponentName?) { remote = null }
    }
    private fun alarmIntent() = PendingIntent.getService(this, 101,
        Intent(this, TimerService::class.java).setAction(FINISH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private val tick = object : Runnable {
        override fun run() {
            if (finishing) return
            val left = prefs.getLong("end", 0) - SystemClock.elapsedRealtime()
            if (left <= 0) finishTimer()
            else {
                val s = (left + 999) / 1000
                notify("剩余 %02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60), true)
                handler.postDelayed(this, 1000)
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "蓝牙倒计时", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, notification("准备倒计时", true))
        when (intent?.action) {
            ACTION_CANCEL -> complete("倒计时已取消")
            ACTION_START -> {
                val seconds = intent.getLongExtra(EXTRA_SECONDS, 0)
                if (seconds !in 1..604800) { complete("倒计时时间无效"); return START_NOT_STICKY }
                prefs.edit().putLong("end", SystemClock.elapsedRealtime() + seconds * 1000).apply()
                finishing = false
                getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, prefs.getLong("end", 0), alarmIntent())
                bindRemote()
                handler.removeCallbacks(tick)
                handler.post(tick)
            }
            FINISH -> finishTimer()
            else -> {
                if (prefs.getLong("end", 0) > 0) { bindRemote(); handler.post(tick) }
                else complete("没有进行中的倒计时")
            }
        }
        return START_STICKY
    }
    private fun bindRemote() {
        if (bound) return
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != 0) {
                complete("请启动 Shizuku 并授权后重新设置倒计时"); return
            }
            bound = true
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) { bound = false; complete("Shizuku 连接失败，请重新授权") }
    }
    private fun finishTimer() {
        if (finishing) return
        finishing = true
        handler.removeCallbacks(tick)
        notify("正在关闭蓝牙", true)
        if (remote != null) executeClose()
        else {
            // Rebind once when the previous shell service connection was lost.
            if (bound) { try { Shizuku.unbindUserService(args, connection, false) } catch (_: Exception) {} }
            bound = false
            bindRemote()
            handler.postDelayed({ if (finishing && remote == null) complete("未能连接 Shizuku，蓝牙未关闭") }, 15000)
        }
    }
    private fun executeClose() {
        val service = remote ?: return
        worker.execute {
            val message = try { service.disable() } catch (_: Exception) { "Shizuku 连接已断开，蓝牙未关闭" }
            handler.post { complete(message) }
        }
    }
    private fun notification(text: String, ongoing: Boolean) =
        Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("蓝牙定时关闭").setContentText(text).setOngoing(ongoing)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
    private fun notify(text: String, ongoing: Boolean) {
        getSystemService(NotificationManager::class.java).notify(1001, notification(text, ongoing))
    }
    private fun complete(message: String) {
        prefs.edit().remove("end").putString("result", message).apply()
        handler.removeCallbacksAndMessages(null)
        getSystemService(AlarmManager::class.java).cancel(alarmIntent())
        stopForeground(STOP_FOREGROUND_DETACH)
        notify(message, false)
        stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (bound) try { Shizuku.unbindUserService(args, connection, true) } catch (_: Exception) {}
        worker.shutdown()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    companion object {
        const val ACTION_START = "start"
        const val ACTION_CANCEL = "cancel"
        const val EXTRA_SECONDS = "seconds"
        private const val FINISH = "finish"
        private const val CHANNEL = "bluetooth_timer"
    }
}
