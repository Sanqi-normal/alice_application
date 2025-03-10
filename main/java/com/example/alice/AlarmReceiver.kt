package com.example.alice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getIntExtra("schedule_id", -1)
        if (scheduleId == -1) {
            Log.e(TAG, "Invalid schedule ID")
            return
        }
        val event = intent.getStringExtra("event") ?: "未命名事件"
        val remindMethods = intent.getStringExtra("remind_methods")?.split(";") ?: emptyList()

        Log.d(TAG, "Received alarm: scheduleId=$scheduleId, event=$event, remindMethods=$remindMethods")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "schedule_channel",
                "日程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 通知
        if ("notify" in remindMethods) {
            val notificationIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, scheduleId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, "schedule_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("日程提醒")
                .setContentText(event)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(scheduleId, notification)
            Log.d(TAG, "Notification sent: id=$scheduleId")
        }

        // AI 消息
        if ("ai" in remindMethods) {
            val aiIntent = Intent("com.example.alice.AI_MESSAGE").apply {
                putExtra("message", "日程提醒：$event")
            }
            context.sendBroadcast(aiIntent)
            Log.d(TAG, "AI message broadcast sent")
        }

        // 铃声
        if ("ring" in remindMethods) {
            try {
                val mediaPlayer = MediaPlayer.create(context, Settings.System.DEFAULT_NOTIFICATION_URI)
                mediaPlayer?.start()
                mediaPlayer?.setOnCompletionListener {
                    it.release()
                    Log.d(TAG, "Ring sound completed")
                } ?: Log.e(TAG, "MediaPlayer creation failed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play ring: ${e.message}", e)
            }
        }

        // 震动
        if ("vibrate" in remindMethods) {
            vibrator.vibrate(longArrayOf(0, 500, 500, 500), -1)
            Log.d(TAG, "Vibration triggered")
        }
    }
}