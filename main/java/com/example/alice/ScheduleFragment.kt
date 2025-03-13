package com.example.alice

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alice.databinding.DialogAddScheduleBinding
import com.example.alice.databinding.FragmentScheduleBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleFragment : Fragment() {
    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ScheduleAdapter
    private lateinit var db: AppDatabase
    private val IMPORT_SCHEDULE_REQUEST = 1001

    companion object {
        private const val TAG = "Alarm"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = androidx.room.Room.databaseBuilder(
            requireContext(), AppDatabase::class.java, "app-db"
        ).addMigrations(AppDatabase.MIGRATION_1_2).build()

        binding.scheduleList.layoutManager = LinearLayoutManager(context)
        adapter = ScheduleAdapter(
            onDelete = { event ->
                CoroutineScope(Dispatchers.IO).launch {
                    val schedules = db.scheduleDao().getAllSync().filter { it.event == event }
                    val allExpired = schedules.all { it.isExpired() }
                    if (allExpired) {
                        schedules.forEach {
                            db.scheduleDao().delete(it)
                            cancelAlarm(it.id)
                        }
                        loadSchedules()
                    } else {
                        withContext(Dispatchers.Main) {
                            (activity as MainActivity).showConfirmDialog(
                                title = "删除日程",
                                message = "确定要删除所有 '${event}' 的未过期日程吗？",
                                onConfirm = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        schedules.forEach {
                                            db.scheduleDao().delete(it)
                                            cancelAlarm(it.id)
                                        }
                                        loadSchedules()
                                    }
                                }
                            )
                        }
                    }
                }
            },
            onEdit = { schedule -> showEditDialog(schedule) }
        )
        binding.scheduleList.adapter = adapter

        binding.btnImportSchedule.apply {
            background = null
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { openFilePicker() }
        }

        binding.fabAdd.setOnClickListener { showAddDialog() }
        loadSchedules()
    }

    private fun loadSchedules() {
        CoroutineScope(Dispatchers.IO).launch {
            val schedules = db.scheduleDao().getAll()
            val foldedItems = schedules.groupBy { it.event }.map { (event, schedules) ->
                ScheduleAdapter.ScheduleItem(event, schedules.sortedBy { it.remindValue })
            }.sortedWith(compareBy({ it.schedules.any { s -> !s.isExpired() }.not() }, { it.schedules.minOf { s -> s.addTime } }))
            withContext(Dispatchers.Main) {
                adapter.submitList(foldedItems)
                Log.d(TAG, "Loaded ${schedules.size} schedules, folded into ${foldedItems.size} items")
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

    private fun importScheduleFromJson(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val jsonText = inputStream?.bufferedReader()?.use { it.readText() }
                inputStream?.close()

                val jsonArray = JSONArray(jsonText)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val dateStr = obj.getString("date")
                    val timeStr = obj.getString("time")
                    val event = obj.getString("event")
                    val note = obj.optString("note", null)

                    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                    val eventTime = dateFormat.parse("$dateStr $timeStr").time

                    val schedule = Schedule(
                        addTime = System.currentTimeMillis(),
                        event = event,
                        remindType = "事件发生前",
                        remindValue = 30L, // 提前 30 分钟
                        eventTime = eventTime, // 实际事件时间
                        remindMethods = "notify;ai",
                        note = note
                    )

                    db.scheduleDao().insert(schedule)
                    withContext(Dispatchers.Main) {
                        setAlarm(schedule)
                    }
                }

                withContext(Dispatchers.Main) {
                    loadSchedules()
                    Toast.makeText(context, "课表导入成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Import failed: ${e.message}", e)
                }
            }
        }
    }
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "选择课表文件"), IMPORT_SCHEDULE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_SCHEDULE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                importScheduleFromJson(uri)
            }
        }
    }
    private fun showAddDialog() {
        val dialogBinding = DialogAddScheduleBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setTitle("添加日程")
            .create()

        val remindTypes = arrayOf("每天定时", "当天定时", "事件发生前", "不提醒")
        dialogBinding.remindTypeSpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, remindTypes
        )

        dialogBinding.remindTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                dialogBinding.timePicker.visibility = View.GONE
                dialogBinding.datePicker.visibility = View.GONE
                dialogBinding.beforeEventTimeLayout.visibility = View.GONE
                dialogBinding.beforeMinutesInput.visibility = View.GONE
                dialogBinding.remindMethodsLabel.visibility = View.VISIBLE
                dialogBinding.notifyCheckbox.visibility = View.VISIBLE
                dialogBinding.aiCheckbox.visibility = View.VISIBLE
                dialogBinding.ringCheckbox.visibility = View.VISIBLE
                dialogBinding.vibrateCheckbox.visibility = View.VISIBLE
                when (position) {
                    0 -> dialogBinding.timePicker.visibility = View.VISIBLE
                    1 -> {
                        dialogBinding.datePicker.visibility = View.VISIBLE
                        dialogBinding.timePicker.visibility = View.VISIBLE
                    }
                    2 -> {
                        dialogBinding.beforeEventTimeLayout.visibility = View.VISIBLE
                        dialogBinding.beforeMinutesInput.visibility = View.VISIBLE
                    }
                    3 -> {
                        dialogBinding.remindMethodsLabel.visibility = View.GONE
                        dialogBinding.notifyCheckbox.visibility = View.GONE
                        dialogBinding.aiCheckbox.visibility = View.GONE
                        dialogBinding.ringCheckbox.visibility = View.GONE
                        dialogBinding.vibrateCheckbox.visibility = View.GONE
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        dialogBinding.confirmButton.setOnClickListener {
            val event = dialogBinding.eventInput.text.toString().trim()
            if (event.isEmpty()) return@setOnClickListener

            val remindType = remindTypes[dialogBinding.remindTypeSpinner.selectedItemPosition]
            val (remindValue, eventTime) = when (remindType) {
                "每天定时" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, dialogBinding.timePicker.hour)
                    cal.set(Calendar.MINUTE, dialogBinding.timePicker.minute)
                    Pair(cal.timeInMillis, null)
                }
                "当天定时" -> {
                    val cal = Calendar.getInstance()
                    cal.set(
                        dialogBinding.datePicker.year,
                        dialogBinding.datePicker.month,
                        dialogBinding.datePicker.dayOfMonth,
                        dialogBinding.timePicker.hour,
                        dialogBinding.timePicker.minute
                    )
                    Pair(cal.timeInMillis, null)
                }
                "事件发生前" -> {
                    val cal = Calendar.getInstance()
                    cal.set(
                        dialogBinding.beforeDatePicker.year,
                        dialogBinding.beforeDatePicker.month,
                        dialogBinding.beforeDatePicker.dayOfMonth,
                        dialogBinding.beforeTimePicker.hour,
                        dialogBinding.beforeTimePicker.minute
                    )
                    val minutes = dialogBinding.beforeMinutesInput.text.toString().toLongOrNull() ?: 0L
                    Pair(minutes, cal.timeInMillis)
                }
                else -> Pair(0L, null)
            }
            val methods = mutableListOf<String>()
            if (dialogBinding.notifyCheckbox.isChecked) methods.add("notify")
            if (dialogBinding.aiCheckbox.isChecked) methods.add("ai")
            if (dialogBinding.ringCheckbox.isChecked) methods.add("ring")
            if (dialogBinding.vibrateCheckbox.isChecked) methods.add("vibrate")
            val note = dialogBinding.noteInput.text.toString().ifEmpty { null }

            val schedule = Schedule(
                addTime = System.currentTimeMillis(),
                event = event,
                remindType = remindType,
                remindValue = remindValue,
                eventTime = eventTime,
                remindMethods = methods.joinToString(";"),
                note = note
            )

            CoroutineScope(Dispatchers.IO).launch {
                db.scheduleDao().insert(schedule)
                withContext(Dispatchers.Main) {
                    loadSchedules()
                    Log.w(TAG, "Schedule added: $schedule")
                    setAlarm(schedule) // 立即设置闹钟
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditDialog(schedule: Schedule) {
        val dialogBinding = DialogAddScheduleBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setTitle("编辑日程")
            .create()

        val remindTypes = arrayOf("每天定时", "当天定时", "事件发生前", "不提醒")
        dialogBinding.remindTypeSpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, remindTypes
        )

        dialogBinding.eventInput.setText(schedule.event)
        val remindIndex = remindTypes.indexOf(schedule.remindType)
        dialogBinding.remindTypeSpinner.setSelection(remindIndex)
        when (schedule.remindType) {
            "每天定时" -> {
                val cal = Calendar.getInstance().apply { timeInMillis = schedule.remindValue }
                dialogBinding.timePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
                dialogBinding.timePicker.minute = cal.get(Calendar.MINUTE)
                dialogBinding.timePicker.visibility = View.VISIBLE
            }
            "当天定时" -> {
                val cal = Calendar.getInstance().apply { timeInMillis = schedule.remindValue }
                dialogBinding.datePicker.updateDate(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                dialogBinding.timePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
                dialogBinding.timePicker.minute = cal.get(Calendar.MINUTE)
                dialogBinding.datePicker.visibility = View.VISIBLE
                dialogBinding.timePicker.visibility = View.VISIBLE
            }
            "事件发生前" -> {
                val cal = Calendar.getInstance().apply { timeInMillis = schedule.eventTime ?: System.currentTimeMillis() }
                dialogBinding.beforeDatePicker.updateDate(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                dialogBinding.beforeTimePicker.hour = cal.get(Calendar.HOUR_OF_DAY)
                dialogBinding.beforeTimePicker.minute = cal.get(Calendar.MINUTE)
                dialogBinding.beforeMinutesInput.setText(schedule.remindValue.toString())
                dialogBinding.beforeEventTimeLayout.visibility = View.VISIBLE
                dialogBinding.beforeMinutesInput.visibility = View.VISIBLE
            }
            "不提醒" -> {
                dialogBinding.remindMethodsLabel.visibility = View.GONE
                dialogBinding.notifyCheckbox.visibility = View.GONE
                dialogBinding.aiCheckbox.visibility = View.GONE
                dialogBinding.ringCheckbox.visibility = View.GONE
                dialogBinding.vibrateCheckbox.visibility = View.GONE
            }
        }
        val methods = schedule.remindMethods.split(";")
        dialogBinding.notifyCheckbox.isChecked = "notify" in methods
        dialogBinding.aiCheckbox.isChecked = "ai" in methods
        dialogBinding.ringCheckbox.isChecked = "ring" in methods
        dialogBinding.vibrateCheckbox.isChecked = "vibrate" in methods
        dialogBinding.noteInput.setText(schedule.note)

        dialogBinding.remindTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                dialogBinding.timePicker.visibility = View.GONE
                dialogBinding.datePicker.visibility = View.GONE
                dialogBinding.beforeEventTimeLayout.visibility = View.GONE
                dialogBinding.beforeMinutesInput.visibility = View.GONE
                dialogBinding.remindMethodsLabel.visibility = View.VISIBLE
                dialogBinding.notifyCheckbox.visibility = View.VISIBLE
                dialogBinding.aiCheckbox.visibility = View.VISIBLE
                dialogBinding.ringCheckbox.visibility = View.VISIBLE
                dialogBinding.vibrateCheckbox.visibility = View.VISIBLE
                when (position) {
                    0 -> dialogBinding.timePicker.visibility = View.VISIBLE
                    1 -> {
                        dialogBinding.datePicker.visibility = View.VISIBLE
                        dialogBinding.timePicker.visibility = View.VISIBLE
                    }
                    2 -> {
                        dialogBinding.beforeEventTimeLayout.visibility = View.VISIBLE
                        dialogBinding.beforeMinutesInput.visibility = View.VISIBLE
                    }
                    3 -> {
                        dialogBinding.remindMethodsLabel.visibility = View.GONE
                        dialogBinding.notifyCheckbox.visibility = View.GONE
                        dialogBinding.aiCheckbox.visibility = View.GONE
                        dialogBinding.ringCheckbox.visibility = View.GONE
                        dialogBinding.vibrateCheckbox.visibility = View.GONE
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        dialogBinding.confirmButton.setOnClickListener {
            val event = dialogBinding.eventInput.text.toString().trim()
            if (event.isEmpty()) return@setOnClickListener

            val remindType = remindTypes[dialogBinding.remindTypeSpinner.selectedItemPosition]
            val (remindValue, eventTime) = when (remindType) {
                "每天定时" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, dialogBinding.timePicker.hour)
                    cal.set(Calendar.MINUTE, dialogBinding.timePicker.minute)
                    Pair(cal.timeInMillis, null)
                }
                "当天定时" -> {
                    val cal = Calendar.getInstance()
                    cal.set(
                        dialogBinding.datePicker.year,
                        dialogBinding.datePicker.month,
                        dialogBinding.datePicker.dayOfMonth,
                        dialogBinding.timePicker.hour,
                        dialogBinding.timePicker.minute
                    )
                    Pair(cal.timeInMillis, null)
                }
                "事件发生前" -> {
                    val cal = Calendar.getInstance()
                    cal.set(
                        dialogBinding.beforeDatePicker.year,
                        dialogBinding.beforeDatePicker.month,
                        dialogBinding.beforeDatePicker.dayOfMonth,
                        dialogBinding.beforeTimePicker.hour,
                        dialogBinding.beforeTimePicker.minute
                    )
                    val minutes = dialogBinding.beforeMinutesInput.text.toString().toLongOrNull() ?: 0L
                    Pair(minutes, cal.timeInMillis)
                }
                else -> Pair(0L, null)
            }
            val methods = mutableListOf<String>()
            if (dialogBinding.notifyCheckbox.isChecked) methods.add("notify")
            if (dialogBinding.aiCheckbox.isChecked) methods.add("ai")
            if (dialogBinding.ringCheckbox.isChecked) methods.add("ring")
            if (dialogBinding.vibrateCheckbox.isChecked) methods.add("vibrate")
            val note = dialogBinding.noteInput.text.toString().ifEmpty { null }

            val updatedSchedule = schedule.copy(
                event = event,
                remindType = remindType,
                remindValue = remindValue,
                eventTime = eventTime,
                remindMethods = methods.joinToString(";"),
                note = note
            )

            CoroutineScope(Dispatchers.IO).launch {
                db.scheduleDao().delete(schedule)
                db.scheduleDao().insert(updatedSchedule)
                withContext(Dispatchers.Main) {
                    loadSchedules()
                    Log.w(TAG, "Schedule edited: $updatedSchedule")
                    setAlarm(updatedSchedule) // 立即设置闹钟
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setAlarm(schedule: Schedule) {
        if (schedule.remindType == "不提醒" || schedule.remindMethods.isEmpty()) {
            Log.w(TAG, "No alarm set for schedule ${schedule.id}: ${schedule.event}")
            return
        }

        val triggerTime = when (schedule.remindType) {
            "每天定时" -> {
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply { timeInMillis = schedule.remindValue }
                target.set(Calendar.YEAR, now.get(Calendar.YEAR))
                target.set(Calendar.MONTH, now.get(Calendar.MONTH))
                target.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
                if (target.before(now)) target.add(Calendar.DAY_OF_MONTH, 1)
                target.timeInMillis
            }
            "当天定时" -> schedule.remindValue
            "事件发生前" -> {
                val eventTime = schedule.eventTime ?: run {
                    Log.e(TAG, "No eventTime for schedule ${schedule.id}")
                    return
                }
                eventTime - (schedule.remindValue * 60 * 1000)
            }
            else -> {
                Log.w(TAG, "Invalid remindType for schedule ${schedule.id}: ${schedule.remindType}")
                return
            }
        }

        if (triggerTime < System.currentTimeMillis() && schedule.remindType != "每天定时") {
            Log.w(TAG, "Skipping expired alarm for schedule ${schedule.id}: ${schedule.event}")
            return
        }

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("schedule_id", schedule.id)
            putExtra("event", schedule.event)
            putExtra("remind_methods", schedule.remindMethods)
            putExtra("remind_type", schedule.remindType) // 新增
            putExtra("note", schedule.note) // 新增
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, schedule.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    Log.w(TAG, "Exact alarm set for schedule ${schedule.id}: ${schedule.event}, triggerTime=${formatTime(triggerTime)}, type=${schedule.remindType}")
                } else {
                    Log.e(TAG, "No permission to schedule exact alarms for schedule ${schedule.id}")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.w(TAG, "Exact alarm set (API 23+) for schedule ${schedule.id}: ${schedule.event}, triggerTime=${formatTime(triggerTime)}, type=${schedule.remindType}")
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.w(TAG, "Exact alarm set (pre-API 23) for schedule ${schedule.id}: ${schedule.event}, triggerTime=${formatTime(triggerTime)}, type=${schedule.remindType}")
            }
                } catch (e: SecurityException) {
            Log.e(TAG, "Failed to set alarm for schedule ${schedule.id}: ${e.message}", e)
        }
    }

    private fun cancelAlarm(scheduleId: Int) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, scheduleId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.w(TAG, "Canceled alarm for scheduleId=$scheduleId")
    }

    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}