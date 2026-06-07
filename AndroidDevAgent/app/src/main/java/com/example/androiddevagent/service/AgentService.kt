package com.example.androiddevagent.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.androiddevagent.ui.MainActivity

class AgentService : Service() {

    companion object {
        const val ACTION_START = "com.example.androiddevagent.ACTION_START"
        const val ACTION_STOP = "com.example.androiddevagent.ACTION_STOP"
        const val EXTRA_TASK = "extra_task"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "agent_channel"
    }

    private val notificationManager by lazy {
        AgentNotificationManager(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel()
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val task = intent.getStringExtra(EXTRA_TASK) ?: ""
                showRunningNotification(task)
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        notificationManager.cancelNotification()
    }

    private fun startForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("助手正在运行")
            .setContentText("正在执行: 初始化")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showRunningNotification(task: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("助手正在运行")
            .setContentText("正在执行: $task")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}
