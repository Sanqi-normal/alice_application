package com.example.alice

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ScheduleAdapter(
    private val onDelete: (Schedule) -> Unit,
    private val onEdit: (Schedule) -> Unit
) : ListAdapter<Schedule, ScheduleAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = getItem(position)
        holder.eventText.text = schedule.event
        holder.remindText.text = when (schedule.remindType) {
            "每天定时" -> "每天 ${formatTime(schedule.remindValue)}"
            "当天定时" -> "单次 ${formatTime(schedule.remindValue)}"
            "事件发生前" -> "提前 ${schedule.remindValue} 分钟 (${formatTime(schedule.eventTime ?: 0L)})"
            else -> "无提醒"
        }

        // 判断是否过期
        val currentTime = System.currentTimeMillis()
        val isExpired = when (schedule.remindType) {
            "当天定时" -> schedule.remindValue < currentTime
            "事件发生前" -> (schedule.eventTime ?: Long.MAX_VALUE) < currentTime
            else -> false // “每天定时”和“不提醒”不过期
        }

        // 应用过期样式
        if (isExpired) {
            holder.eventText.setTextColor(Color.GRAY)
            holder.remindText.setTextColor(Color.GRAY)
            holder.eventText.paintFlags = holder.eventText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.remindText.paintFlags = holder.remindText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.eventText.setTextColor(Color.BLACK)
            holder.remindText.setTextColor(Color.BLACK)
            holder.eventText.paintFlags = holder.eventText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.remindText.paintFlags = holder.remindText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        holder.deleteButton.setOnClickListener { onDelete(schedule) }
        holder.itemView.setOnClickListener { onEdit(schedule) }
    }

    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val eventText: TextView = view.findViewById(R.id.text_event)
        val remindText: TextView = view.findViewById(R.id.text_remind)
        val deleteButton: ImageButton = view.findViewById(R.id.btn_delete)
    }

    class DiffCallback : DiffUtil.ItemCallback<Schedule>() {
        override fun areItemsTheSame(oldItem: Schedule, newItem: Schedule) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Schedule, newItem: Schedule) = oldItem == newItem
    }
}