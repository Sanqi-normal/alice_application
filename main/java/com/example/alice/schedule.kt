package com.example.alice

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val addTime: Long,        // 添加时间戳
    val event: String,        // 事件
    val remindType: String,   // 提醒类型: daily, once, before, none
    val remindValue: Long,    // 对于“事件发生前”是提前分钟数，其他是提醒时间
    val eventTime: Long? = null, // 新增：事件发生时间，仅“事件发生前”使用
    val remindMethods: String, // 提醒方式: notify;ai;ring;vibrate（分号分隔）
    val note: String?         // 备注
)