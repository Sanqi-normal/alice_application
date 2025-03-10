package com.example.alice

import org.json.JSONArray
import org.json.JSONObject

object Tools {
    val scheduleTools = JSONArray().apply {
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "addSchedule")
                put("description", "添加一个新日程，在用户请求创建日程时调用")
                put("parameters", JSONObject().apply {
                    put("time", JSONObject().apply {
                        put("type", "number")
                        put("content", "提醒时间（毫秒时间戳）")
                    })
                    put("event", JSONObject().apply {
                        put("type", "string")
                        put("content", "事件名称")
                    })
                    put("remindType", JSONObject().apply {
                        put("type", "string")
                        put("content", "提醒类型（可选：每天定时、当天定时、事件发生前、不提醒）")
                        put("default", "当天定时")
                    })
                    put("remindValue", JSONObject().apply {
                        put("type", "number")
                        put("content", "提醒值（根据类型：时间或提前分钟数）")
                    })
                    put("eventTime", JSONObject().apply {
                        put("type", "number")
                        put("content", "事件发生时间（可选，仅事件发生前使用）")
                    })
                    put("remindMethods", JSONObject().apply {
                        put("type", "array")
                        put("items", JSONObject().apply {
                            put("type", "string")
                            put("content", "提醒方式（notify, ai, ring, vibrate）")
                        })
                        put("default", JSONArray().apply { put("notify") })
                    })
                    put("note", JSONObject().apply {
                        put("type", "string")
                        put("content", "备注（可选）")
                    })
                })
                put("arguments", "time, event, remindType, remindValue, eventTime, remindMethods, note")
            })
        })
        put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "deleteSchedule")
                put("description", "删除一个日程，在用户请求删除日程时调用")
                put("parameters", JSONObject().apply {
                    put("event", JSONObject().apply {
                        put("type", "string")
                        put("content", "事件名称")
                    })
                    put("time", JSONObject().apply {
                        put("type", "number")
                        put("content", "提醒时间（可选，毫秒时间戳）")
                    })
                })
                put("arguments", "event, time")
            })
        })
    }

    fun getToolsJson(): String = scheduleTools.toString()
}