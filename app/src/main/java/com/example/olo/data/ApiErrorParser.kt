package com.example.olo.data

import com.google.gson.Gson
import com.google.gson.JsonObject

internal object ApiErrorParser {
    fun message(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val json = Gson().fromJson(body, JsonObject::class.java) ?: return@runCatching null
            listOf("message", "detail", "error")
                .asSequence()
                .mapNotNull { key -> json.get(key)?.takeIf { it.isJsonPrimitive }?.asString }
                .firstOrNull(String::isNotBlank)
        }.getOrNull()
    }
}
