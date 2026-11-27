package com.example.myagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

// রোবট আইকন স্ক্রিনে ভাসিয়ে রাখার সার্ভিস
class AgentService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.myagent.STOP"
        const val EXTRA_TASK = "task"
        private const val CHANNEL = "agent_running"
        private const val NOTIF_ID = 11

        @Volatile
        var running = false

        fun start(ctx: Context, task: String? = null) {
            val i = Intent(ctx, AgentService::class.java)
            if (task != null) i.putExtra(EXTRA_TASK, task)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AgentService::class.java))
        }
    }

    private var overlay: OverlayController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Agent running", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AgentService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("My Agent")
            .setContentText("রোবট চালু আছে")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "বন্ধ করুন", stop)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlay == null) {
            val o = OverlayController(this) { stopSelf() }
            o.start()
            overlay = o
        }
        running = true

        val task = intent?.getStringExtra(EXTRA_TASK)
        if (!task.isNullOrBlank()) overlay?.runTask(task)

        return START_STICKY
    }

    override fun onDestroy() {
        overlay?.stop()
        overlay = null
        running = false
        super.onDestroy()
    }
}
