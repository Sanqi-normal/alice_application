package com.example.alice

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ScheduleAdapter(
    private val onDelete: (String) -> Unit,
    private val onEdit: (Schedule) -> Unit
) : ListAdapter<ScheduleAdapter.ScheduleItem, ScheduleAdapter.ViewHolder>(DiffCallback()) {

    // 数据类表示折叠后的项
    data class ScheduleItem(
        val event: String,
        val schedules: List<Schedule>
    )

    private var foldedSchedules: List<ScheduleItem> = emptyList()

    override fun submitList(list: List<ScheduleItem>?) {
        foldedSchedules = list ?: emptyList()
        super.submitList(foldedSchedules)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.eventText.text = item.event

// 修改时间显示逻辑
        val schedules = item.schedules
        holder.remindText.text = when {
            schedules.size > 2 -> "多次" // 超过 2 个显示“多次”
            schedules.isEmpty() -> "无提醒"
            else -> schedules.joinToString("\n") { schedule ->
                when (schedule.remindType) {
                    "每天定时" -> "每天 ${formatTime(schedule.remindValue)}"
                    "当天定时" -> "单次 ${formatTime(schedule.remindValue)}"
                    "事件发生前" -> "提前 ${schedule.remindValue} 分钟 (${formatTime(schedule.eventTime ?: 0L)})"
                    else -> "无提醒"
                }
            }
        }

        val hasActive = item.schedules.any { !it.isExpired() }
        if (!hasActive) {
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

        holder.deleteButton.setOnClickListener { onDelete(item.event) }
        holder.itemView.setOnClickListener {
            when{
                schedules.size<2->onEdit(item.schedules.first())
                else -> Toast.makeText(holder.itemView.context, "当前不支持修改课表", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun Schedule.isExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        return when (remindType) {
            "当天定时" -> remindValue < currentTime
            "事件发生前" -> (eventTime ?: Long.MAX_VALUE) < currentTime
            else -> false
        }
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

    class DiffCallback : DiffUtil.ItemCallback<ScheduleItem>() {
        override fun areItemsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem) = oldItem.event == newItem.event
        override fun areContentsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem) = oldItem == newItem
    }
}