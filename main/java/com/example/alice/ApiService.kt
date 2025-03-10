package com.example.alice

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 非流式响应，用于工具调用（Qwen 模型）
    suspend fun sendMessage(
        systemPrompt: String,
        message: String,
        temperature: Float,
        apiKey: String,
        recentHistory: List<Message>
    ): String {
        Log.d("TEST", "sendMessage: 添加消息块")
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            recentHistory.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", message)
            })
        }

        val json = JSONObject().apply {
            put("model", "Qwen/Qwen2.5-7B-Instruct") // 工具调用使用 Qwen 模型
            put("messages", messagesArray)
            put("temperature", temperature)
            put("max_tokens", 4096)
            put("stream", false) // 非流式
            put("tools", Tools.scheduleTools) // 添加工具定义
        }
        val request = Request.Builder()
            .url("https://api.siliconflow.cn/v1/chat/completions")
            .header("Authorization", "Bearer <token>")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d("TEST", "sendMessage: 发送请求")
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("TEST", "sendMessage: 请求失败，code=${response.code}")
                return "服务器错误: ${response.code}"
            }
            val responseBody = response.body?.string() ?: return "错误: 响应为空"
            Log.d("TEST", "Tool response: $responseBody")
            return responseBody
        } catch (e: SocketTimeoutException) {
            Log.e("TEST", "sendMessage: 请求超时", e)
            return "错误: 请求超时，请检查网络或稍后重试"
        } catch (e: Exception) {
            Log.e("TEST", "sendMessage: 请求失败", e)
            throw e
        }
    }

    // 流式响应，用于 Alice（DeepSeek-V3 模型）
    suspend fun sendMessageStream(
        systemPrompt: String,
        message: String,
        temperature: Float,
        apiKey: String,
        recentHistory: List<Message>,
        max_tokens: Int
    ): Flow<String> = flow {
        Log.d("TEST", "sendMessageStream: 添加消息块")
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            recentHistory.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", message)
            })
        }

        val json = JSONObject().apply {
            put("model", "deepseek-chat") // Alice 使用 DeepSeek-V3 模型
            put("messages", messagesArray)
            put("temperature", temperature)
            put("max_tokens", max_tokens)
            put("frequency_penalty", 1.5)
            put("presence_penalty", 0.5)
            put("stream", true) // 流式
        }

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer <token>")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d("TEST", "sendMessageStream: 发送请求")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e("TEST", "sendMessageStream: 请求失败，code=${response.code}")
            throw Exception("服务器错误: ${response.code}")
        }

        val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
        var accumulatedResult = ""
        reader.use { r ->
            var line: String?
            Log.d("TEST", "sendMessageStream: 开始读取消息")
            while (r.readLine().also { line = it } != null) {
                val data = line?.takeIf { it.startsWith("data: ") }?.substring(6) ?: continue
                if (data == "[DONE]") break
                try {
                    val jsonPart = JSONObject(data)
                    val content = jsonPart.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("delta")
                        .optString("content", "")
                    if (content.isNotEmpty()) {
                        accumulatedResult += content
                        emit(accumulatedResult)
                    }
                } catch (e: Exception) {
                    Log.e("TEST", "sendMessageStream: 解析错误", e)
                }
            }
        }
    }.flowOn(Dispatchers.IO)
        .catch { e ->
            Log.e("TEST", "sendMessageStream: 请求失败", e)
            when (e) {
                is SocketTimeoutException -> emit("错误: 请求超时，请检查网络或稍后重试")
                else -> emit("错误: ${e.message}")
            }
        }
}