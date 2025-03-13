package com.example.alice

import android.app.AlarmManager
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
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "Alarm"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getIntExtra("schedule_id", -1)
        if (scheduleId == -1) {
            Log.e(TAG, "Invalid schedule ID")
            return
        }
        val eventMessage = intent.getStringExtra("event") ?: "未命名事件"
        val remindMethods = intent.getStringExtra("remind_methods")?.split(";") ?: emptyList()
        val remindType = intent.getStringExtra("remind_type") ?: ""
        val noteMessage = intent.getStringExtra("note") ?: "无备注"
        val message = "事件："+ eventMessage + "\n" +"备注："+ noteMessage

        Log.d(TAG, "Received alarm: scheduleId=$scheduleId, event=$message, remindMethods=$remindMethods, remindType=$remindType")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // 创建通知渠道（保持不变）
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

        // 通知（保持不变）
        if ("notify" in remindMethods) {
            val notificationIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, scheduleId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, "schedule_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("日程提醒")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(scheduleId, notification)
            Log.d(TAG, "Notification sent: id=$scheduleId")
        }

        // AI 消息 - 添加综合提醒逻辑
        if ("ai" in remindMethods) {
            // 发送原始消息
            val aiIntent = Intent("com.example.alice.AI_MESSAGE").apply {
                putExtra("message", "日程提醒：$message")
            }
            context.sendBroadcast(aiIntent)
            Log.d(TAG, "AI message broadcast sent: $message")

            // 检查是否为特殊日程并发送综合提醒
            if (eventMessage == "喊叁七起床，要开始新的一天了") {
                // 初始化数据库
                val db = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, "app-db")
                    .addMigrations(AppDatabase.MIGRATION_1_2)
                    .build()

                // 计算当天时间范围
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val todayEnd = todayStart + AlarmManager.INTERVAL_DAY

                // 查询当天非“每天定时”的日程
                val schedules = db.scheduleDao().getAllSync().filter { schedule ->
                    val triggerTime = when (schedule.remindType) {
                        "当天定时" -> schedule.remindValue
                        "事件发生前" -> schedule.eventTime?.minus(schedule.remindValue * 60 * 1000) ?: Long.MAX_VALUE
                        else -> Long.MAX_VALUE // 排除“每天定时”和“不提醒”
                    }
                    triggerTime in todayStart..todayEnd && schedule.remindType != "每天定时"
                }

                if (schedules.isNotEmpty()) {
                    // 生成综合消息
                    val summaryMessage = buildString {
                        append("今天的事务提醒：\n")
                        schedules.forEachIndexed { index, schedule ->
                            val time = formatTime(when (schedule.remindType) {
                                "当天定时" -> schedule.remindValue
                                "事件发生前" -> schedule.eventTime?.minus(schedule.remindValue * 60 * 1000) ?: 0L
                                else -> 0L
                            })
                            append("${index + 1}. [$time] ${schedule.event}")
                            if (!schedule.note.isNullOrEmpty()) append(" (备注: ${schedule.note})")
                            append("\n")
                        }
                    }
                    Log.d(TAG, "Daily summary generated: $summaryMessage")

                    // 发送综合消息的 AI 广播
                    val summaryAiIntent = Intent("com.example.alice.AI_MESSAGE").apply {
                        putExtra("message", summaryMessage)
                    }
                    context.sendBroadcast(summaryAiIntent)
                    Log.d(TAG, "AI summary broadcast sent: $summaryMessage")
                } else {
                    Log.d(TAG, "No non-daily schedules for today")
                }
                db.close() // 关闭数据库
            }
        }

        // 铃声（保持不变）
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

        // 震动（保持不变）
        if ("vibrate" in remindMethods) {
            vibrator.vibrate(longArrayOf(0, 500, 500, 500), -1)
            Log.d(TAG, "Vibration triggered")
        }

        // “每天定时”重复调度（调整传递的 event）
        if (remindType == "每天定时") {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextTriggerTime = System.currentTimeMillis() + AlarmManager.INTERVAL_DAY
            val nextIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("schedule_id", scheduleId)
                putExtra("event", eventMessage) // 只传 eventMessage，避免嵌套格式
                putExtra("remind_methods", remindMethods.joinToString(";"))
                putExtra("remind_type", remindType)
                putExtra("note", noteMessage) // 传递原始 note
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, scheduleId, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "Cannot reschedule daily alarm: no SCHEDULE_EXACT_ALARM permission")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
                Log.w(TAG, "Daily alarm rescheduled for next day: scheduleId=$scheduleId, nextTriggerTime=${formatTime(nextTriggerTime)}")
            }
        }
    }
    // 添加 formatTime 方法（如果没有）
    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}