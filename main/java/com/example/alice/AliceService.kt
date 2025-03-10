package com.example.alice

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AliceService : Service() {
    private val binder = AliceBinder()
    private val messages = mutableListOf<Message>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var aiMessageReceiver: BroadcastReceiver
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val db by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "app-db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    inner class AliceBinder : Binder() {
        fun getService(): AliceService = this@AliceService
        fun sendMessage(message: String, callback: (String) -> Unit) {
            this@AliceService.sendMessage(message, callback)
        }
        fun getMessages(): List<Message> = messages.toList()
        fun clearHistory(callback: () -> Unit) = this@AliceService.clearHistory(callback)
        fun exportHistory(outputPath: String? = null) = this@AliceService.exportHistory(outputPath)
        fun importHistory(uri: String) = this@AliceService.importHistory(uri)
    }
    fun getMessages(): List<Message> = messages.toList()
    override fun onBind(intent: Intent?): IBinder = binder

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        registerReceiver()
        loadHistoryFromPrefs()
        cleanExpiredSchedules() // 新增：清理过期日程
    }
    // 新增：清理过期日程
    private fun cleanExpiredSchedules() {
        scope.launch(Dispatchers.IO) {
            val schedules = db.scheduleDao().getAll()
            val now = System.currentTimeMillis()
            schedules.filter { schedule ->
                val triggerTime = when (schedule.remindType) {
                    "每天定时" -> schedule.remindValue // 每天定时不过期
                    "当天定时" -> schedule.remindValue
                    "事件发生前" -> schedule.eventTime?.let { it - (schedule.remindValue * 60 * 1000) } ?: schedule.remindValue
                    else -> Long.MAX_VALUE
                }
                triggerTime < now && schedule.remindType != "每天定时" // 保留每天定时
            }.forEach { schedule ->
                db.scheduleDao().delete(schedule)
                cancelAlarm(schedule.id)
                Log.d("Alarm", "Cleared expired schedule: ${schedule.event}")
            }
        }
    }
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "alice_channel")
            .setContentTitle("Alice Service")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun registerReceiver() {
        aiMessageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.getStringExtra("message")?.let { message ->
                    scope.launch {
                        sendToAlice(
                            "($message)",
                            SettingsData.getSystemPrompt(),
                            SettingsData.getTemperature(),
                            SettingsData.getApiKey(),
                            messages.takeLast(10),
                            128
                        )
                    }
                }
            }
        }
        registerReceiver(aiMessageReceiver, IntentFilter("com.example.alice.AI_MESSAGE"), RECEIVER_NOT_EXPORTED)
    }

    private suspend fun sendToAlice(
        message: String,
        systemPrompt: String,
        temperature: Float,
        apiKey: String,
        recentHistory: List<Message>,
        maxTokens: Int,
        callback: ((String) -> Unit)? = null
    ) {
        val assistantIndex = messages.size
        addMessage("assistant", "", false) // 添加占位符消息
        var accumulatedContent = ""

        ApiService.sendMessageStream(systemPrompt, message, temperature, apiKey, recentHistory, maxTokens)
            .collect { response ->
                accumulatedContent = response
                messages[assistantIndex] = Message("assistant", accumulatedContent, getCurrentTime())
                withContext(Dispatchers.Main) { callback?.invoke(response) } // 实时回调更新 UI
            }

        if (accumulatedContent.isNotEmpty()) {
            saveHistory()
            updateEmbeddings(message, accumulatedContent)
        } else {
            messages.removeAt(assistantIndex)
            addMessage("system", "错误: Alice 未返回响应", false)
        }
    }

    private fun addMessage(role: String, content: String, saveToHistory: Boolean = true) {
        val finalcontent =if(role=="user") "[${getCurrentTime()}]"+ content else content
        val message = Message(role, finalcontent, getCurrentTime())
        messages.add(message)
        if (saveToHistory && role != "system") saveHistory()
    }

    private fun saveHistory() {
        val jsonArray = JSONArray()
        messages.filter { it.role != "system" }.forEach {
            jsonArray.put(JSONObject().apply {
                put("role", it.role)
                put("content", it.content)
                put("timeStamp", it.timeStamp)
            })
        }
        getSharedPreferences("AliceChat", MODE_PRIVATE)
            .edit()
            .putString("chatHistory", jsonArray.toString())
            .apply()
    }

    private fun loadHistoryFromPrefs() {
        val historyJson = getSharedPreferences("AliceChat", MODE_PRIVATE)
            .getString("chatHistory", null)
        if (historyJson != null) {
            messages.clear()
            val jsonArray = JSONArray(historyJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                messages.add(
                    Message(
                        obj.getString("role"),
                        obj.getString("content"),
                        obj.optString("timeStamp", "时间未知")
                    )
                )
            }
        }
    }


    private fun cancelAlarm(scheduleId: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, scheduleId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private suspend fun quantizeConversation(text: String): List<Float>? {
        val json = JSONObject().apply {
            put("model", "BAAI/bge-m3")
            put("input", text.take(8000)) // 截断超过 8k
            put("encoding_format", "float")
        }
        val request = okhttp3.Request.Builder()
            .url("https://api.siliconflow.cn/v1/embeddings")
            .header("Authorization", "Bearer <token>")
            .header("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO){
            try {
            val response =  client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("QUAN", "网络请求失败，状态码：${response.code}")
                response.close()
                return@withContext null
            }

            response.body?.use { body ->
                Log.d("QUAN", "量化网络请求响应成功")
                val responseJson = JSONObject(body.string())
                val embeddingArray = responseJson.getJSONArray("data").getJSONObject(0).getJSONArray("embedding")
                (0 until embeddingArray.length()).map { embeddingArray.getDouble(it).toFloat() }
            } ?: run {
                Log.e("QUAN", "网络请求失败，body 为 null")
                response.close()
                return@run null
            }
        } catch (e: Exception) {
            Log.e("TEST", "Quantize failed: ${e.message}", e)
                return@withContext null
        }
    }
    }


    suspend fun quantizeHistory(callback: (Int) -> Unit) {
        Log.d("QUAN", "开始量化历史记录")
        val totalMessages = messages.size
        val embeddings = mutableListOf<Embedding>()

        messages.forEachIndexed { index, msg ->
            val progress = ((index + 1) * 100) / totalMessages
            withContext(Dispatchers.Main) { callback(progress) } // 更新进度条
            if (msg.role != "user" && msg.role != "assistant") {
                Log.d("QUAN", "跳过量化，消息 $index 角色 ${msg.role} 不是 user 或 assistant")
            } else {
                val embedding = quantizeConversation("${msg.role}: ${msg.content}")
                embedding?.let {
                    embeddings.add(Embedding(it, index))
                    Log.d("QUAN", "消息 $index 量化成功")
                } ?: Log.d("QUAN", "消息 $index 量化失败")
            }
        }
        if (embeddings.isNotEmpty()) {
            Log.d("QUAN", "量化完成，共 ${embeddings.size} 条记录")
            saveEmbeddings(embeddings)
            Log.d("QUAN", "量化文件已保存至: ${File(filesDir, "memory_embeddings.json").absolutePath}")
            withContext(Dispatchers.Main) {
                addMessage("system", "历史记录已量化并保存", false)
            }
        }else{
            withContext(Dispatchers.Main){
                addMessage("system", "量化失败，请检查网络连接", false)
            }
        }
    }

    fun sendMessage(message: String, callback: (String) -> Unit) {
        if (message.isEmpty()) return
        addMessage("user", message)

        scope.launch {
            try {
                val systemPrompt = SettingsData.getSystemPrompt()
                val temperature = SettingsData.getTemperature()
                val apiKey = SettingsData.getApiKey()
                val maxTokens = SettingsData.getMaxTokens()
                val recentHistory = messages.takeLast(10).filter { it.role != "system" }

                if (systemPrompt.isEmpty() || apiKey.isEmpty()) {
                    addMessage("system", "错误: 系统提示或 API Key 未设置", false)
                    withContext(Dispatchers.Main) { callback("Error: Setup required") }
                    return@launch
                }

                // 恢复：量化对话逻辑
                val queryEmbedding = quantizeConversation(message)
                val enhancedPrompt = if (queryEmbedding != null) {
                    val embeddingsFile = File(filesDir, "memory_embeddings.json")
                    if (embeddingsFile.exists()) {
                        val context = getRelevantContext(queryEmbedding, embeddingsFile)
                        if (context.isNotEmpty()) "[相关记忆]\n$context\n" else ""
                    } else ""
                } else ""

                sendToAlice(
                    message,
                    systemPrompt + enhancedPrompt,
                    temperature,
                    apiKey,
                    recentHistory,
                    maxTokens,
                    callback
                )
            } catch (e: Exception) {
                addMessage("system", "错误: ${e.message}", false)
                withContext(Dispatchers.Main) { callback("Error: ${e.message}") }
            }
        }
    }

    private fun getRelevantContext(queryEmbedding: List<Float>, embeddingsFile: File): String {
        val embeddingsJson = embeddingsFile.readText()
        val embeddingsArray = JSONArray(embeddingsJson)
        val embeddings = (0 until embeddingsArray.length()).map { i ->
            val obj = embeddingsArray.getJSONObject(i)
            Embedding(
                embedding = obj.getJSONArray("embedding").let { array ->
                    (0 until array.length()).map { array.getDouble(it).toFloat() }
                },
                index = obj.getInt("index")
            )
        }
        val topK = findTopKSimilar(queryEmbedding, embeddings, messages)
        return topK.joinToString("\n") {
            "${it.user?.role ?: "Unknown"}: ${it.user?.content ?: ""}\n${it.assistant?.role ?: "Unknown"}: ${it.assistant?.content ?: ""}"
        }
    }

    private fun findTopKSimilar(queryEmbedding: List<Float>, embeddings: List<Embedding>, conversations: List<Message>, k: Int = 5): List<SimilarityResult> {
        val similarities = embeddings.map { embedding ->
            Similarity(
                index = embedding.index,
                similarity = cosineSimilarity(queryEmbedding, embedding.embedding)
            )
        }.filter { it.similarity > 0.6f }
            .sortedByDescending { it.similarity }

        val results = mutableMapOf<String, SimilarityResult>()
        for (sim in similarities) {
            val conv = conversations.getOrNull(sim.index) ?: continue
            val context = getContext(conversations, sim.index)
            val key = "${conv.content}|${context?.content ?: ""}"
            if (!results.containsKey(key)) {
                results[key] = SimilarityResult(
                    user = if (conv.role == "user") conv else context?.takeIf { it.role == "user" },
                    assistant = if (conv.role == "assistant") conv else context?.takeIf { it.role == "assistant" },
                    similarity = sim.similarity
                )
            }
            if (results.size >= k) break
        }
        return results.values.toList()
    }

    private fun cosineSimilarity(vecA: List<Float>, vecB: List<Float>): Float {
        if (vecA.size != vecB.size) return 0f
        val dotProduct = vecA.zip(vecB).map { (a, b) -> a * b }.sum()
        val magA = Math.sqrt(vecA.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        val magB = Math.sqrt(vecB.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        return if (magA == 0f || magB == 0f) 0f else dotProduct / (magA * magB)
    }

    private fun getContext(conversations: List<Message>, index: Int): Message? {
        val current = conversations.getOrNull(index) ?: return null
        return if (current.role == "user" && index + 1 < conversations.size) {
            conversations[index + 1]
        } else if (current.role == "assistant" && index > 0) {
            conversations[index - 1]
        } else null
    }

    private fun updateEmbeddings(userMessage: String, assistantResponse: String) {
        scope.launch {
            val userEmbedding = quantizeConversation("user: $userMessage")
            val assistantEmbedding = quantizeConversation("assistant: $assistantResponse")
            val embeddingsFile = File(filesDir, "memory_embeddings.json")
            val embeddings = if (embeddingsFile.exists()) {
                JSONArray(embeddingsFile.readText()).let { array ->
                    (0 until array.length()).map { i ->
                        val obj = array.getJSONObject(i)
                        Embedding(
                            embedding = obj.getJSONArray("embedding").let { arr ->
                                (0 until arr.length()).map { arr.getDouble(it).toFloat() }
                            },
                            index = obj.getInt("index")
                        )
                    }.toMutableList()
                }
            } else mutableListOf()

            val maxIndex = embeddings.maxOfOrNull { it.index } ?: -1
            userEmbedding?.let { embeddings.add(Embedding(it, maxIndex + 1)) }
            assistantEmbedding?.let { embeddings.add(Embedding(it, maxIndex + 2)) }
            saveEmbeddings(embeddings)
        }
    }

    private fun saveEmbeddings(embeddings: List<Embedding>) {
        val jsonArray = JSONArray().apply {
            embeddings.forEach { embedding ->
                put(JSONObject().apply {
                    put("embedding", JSONArray().apply {
                        embedding.embedding.forEach { put(it) }
                    })
                    put("index", embedding.index)
                })
            }
        }
        File(filesDir, "memory_embeddings.json").writeText(jsonArray.toString(2))
    }

    private fun clearHistory(callback: () -> Unit) {
        messages.clear()
        getSharedPreferences("AliceChat", MODE_PRIVATE).edit().clear().apply()
        val embeddingsFile = File(filesDir, "memory_embeddings.json")
        if (embeddingsFile.exists()) embeddingsFile.delete()
        addMessage("system", "历史记录及量化记忆已清除，文件路径${filesDir.absolutePath}", false)
        scope.launch(Dispatchers.Main) { callback() }
    }

    private fun exportHistory(outputPath: String? = null) {
        val jsonArray = JSONArray()
        messages.filter { it.role != "system" }.forEach {
            jsonArray.put(JSONObject().apply {
                put("role", it.role)
                put("content", it.content)
                put("timeStamp", it.timeStamp)
            })
        }
        val file = File(outputPath ?: "${filesDir}/alice_chat_history.json")
        file.writeText(jsonArray.toString(2))
        addMessage("system", "历史记录已导出到: ${file.absolutePath}", false)
    }

    private fun importHistory(uri: String) {
        scope.launch {
            try {
                val inputStream = contentResolver.openInputStream(android.net.Uri.parse(uri))
                val jsonText = inputStream?.bufferedReader()?.use { it.readText() }
                inputStream?.close()

                messages.clear()
                val jsonArray = JSONArray(jsonText)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    messages.add(
                        Message(
                            obj.getString("role"),
                            obj.getString("content"),
                            obj.optString("timeStamp", "时间未知")
                        )
                    )
                }
                saveHistory()
                addMessage("system", "历史记录已导入", false)
            } catch (e: Exception) {
                addMessage("system", "导入失败: ${e.message}", false)
            }
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(aiMessageReceiver)
        scope.cancel()
    }
}

data class Embedding(val embedding: List<Float>, val index: Int)
data class Similarity(val index: Int, val similarity: Float)
data class SimilarityResult(val user: Message?, val assistant: Message?, val similarity: Float)