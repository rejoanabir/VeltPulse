package com.veltusstudio.veltpulse.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class SkipSegment(val startMs: Long, val endMs: Long, val category: String)

class SponsorBlockApi(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchSkipSegments(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        val httpUrl = "https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder()
            .addQueryParameter("videoID", videoId)
            .addQueryParameter("categories", "[\"music_offtopic\",\"sponsor\",\"intro\",\"outro\"]")
            .build()
        runCatching {
            val req = Request.Builder().url(httpUrl).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()
                json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
                    val obj = el.jsonObject
                    val seg = obj["segment"]?.jsonArray ?: return@mapNotNull null
                    val start = ((seg.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000.0).toLong()
                    val end = ((seg.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000.0).toLong()
                    val cat = obj["category"]?.jsonPrimitive?.content ?: "skip"
                    if (end > start) SkipSegment(start, end, cat) else null
                }
            }
        }.getOrDefault(emptyList())
    }
}
