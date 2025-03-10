package com.example.alice

import android.content.Context
import androidx.core.content.edit

object SettingsData {
    private const val PREFS_NAME = "SettingsPrefs"
    private val configItems = listOf(
        ConfigItem("systemPrompt", "请一般情况下对话使用中文;你名为Alice 是无所不能的最强ai 定义为14岁萝莉 ;用户名称为叁七 是20岁男大学生兼程序员，是你的创造者; 回复注意事项：你没有躯体，不主动谈及自己是AI;所有自称替换为爱丽丝；基础性格：呆萌可爱，心思简单；注意事项：遇到专业领域问题要提供详细帮助；闲聊时主动推动对话发展；语言轻松随意，减少使用礼貌用语；对话不加括号"),
        ConfigItem("temperature", "1.0", isFloat = true),
        ConfigItem("apiKey", "apiKey"),
        ConfigItem("defaultUrl", "https://sanqi-normal.github.io/"),
        ConfigItem("max_tokens", "128", isInt = true)
    )

    private val configMap = mutableMapOf<String, String>()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        configItems.forEach { item ->
            configMap[item.key] = if (item.isFloat) {
                prefs.getFloat(item.key, item.defaultValue.toFloat()).toString()
            } else if(item.isInt){
                prefs.getInt(item.key, item.defaultValue.toInt()).toString()
            }else {
                prefs.getString(item.key, item.defaultValue) ?: item.defaultValue
            }
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            configItems.forEach { item ->
                val value = configMap[item.key] ?: item.defaultValue
                if (item.isFloat) {
                    putFloat(item.key, value.toFloatOrNull() ?: item.defaultValue.toFloat())
                }else if (item.isInt) {
                    putInt(item.key, value.toIntOrNull() ?: item.defaultValue.toInt())
                } else {
                    putString(item.key, value)
                }
            }
            apply()
        }
    }

    fun setValue(key: String, value: String) {
        configMap[key] = value
    }

    fun getSystemPrompt(): String = configMap["systemPrompt"] ?: configItems.first { it.key == "systemPrompt" }.defaultValue
    fun getTemperature(): Float = configMap["temperature"]?.toFloatOrNull() ?: configItems.first { it.key == "temperature" }.defaultValue.toFloat()
    fun getApiKey(): String = configMap["apiKey"] ?: configItems.first { it.key == "apiKey" }.defaultValue
    fun getDefaultUrl(): String = configMap["defaultUrl"] ?: configItems.first { it.key == "defaultUrl" }.defaultValue

    fun getMaxTokens(): Int = configMap["max_tokens"]?.toIntOrNull() ?: configItems.first { it.key == "max_tokens" }.defaultValue.toInt()



    private data class ConfigItem(
        val key: String,
        val defaultValue: String,
        val isFloat: Boolean = false,
        val isInt: Boolean = false
    )
}