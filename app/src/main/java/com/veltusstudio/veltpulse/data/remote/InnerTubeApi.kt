package com.veltusstudio.veltpulse.data.remote

import com.veltusstudio.veltpulse.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class InnerTubeApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private var cachedVisitorId: String? = null
        private var cachedVisitorIdTimestamp: Long = 0L
        private const val VISITOR_ID_TTL_MS = 12 * 60 * 60 * 1000L // 12 hours
    }

    suspend fun getVisitorId(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!cachedVisitorId.isNullOrBlank() && (now - cachedVisitorIdTimestamp < VISITOR_ID_TTL_MS)) {
            return@withContext cachedVisitorId
        }

        // Tier A: Scrape from sw.js_data
        val fromSw = runCatching {
            val request = Request.Builder()
                .url("https://www.youtube.com/sw.js_data")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                .header("Accept", "*/*")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                val match = Regex("""(Cgt[a-zA-Z0-9%_-]{20,})""").find(body)
                match?.groupValues?.get(1)
            }
        }.getOrNull()

        if (!fromSw.isNullOrBlank()) {
            cachedVisitorId = fromSw
            cachedVisitorIdTimestamp = now
            return@withContext fromSw
        }

        // Tier B: Fallback scrape from youtube.com homepage
        val fromHtml = runCatching {
            val request = Request.Builder()
                .url("https://www.youtube.com")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                val match = Regex("""["']VISITOR_DATA["']\s*:\s*["']([^"']+)["']""").find(body)
                    ?: Regex("""(Cg[ts][a-zA-Z0-9%_-]{20,})""").find(body)
                match?.groupValues?.get(1)
            }
        }.getOrNull()

        if (!fromHtml.isNullOrBlank()) {
            cachedVisitorId = fromHtml
            cachedVisitorIdTimestamp = now
            return@withContext fromHtml
        }

        cachedVisitorId
    }

    suspend fun resolveVisionOsStream(videoId: String): Pair<String, Double?>? = withContext(Dispatchers.IO) {
        val safeVideoId = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (safeVideoId.isBlank()) return@withContext null

        val visitorId = getVisitorId()
        val visitorField = if (!visitorId.isNullOrBlank()) {
            """, "visitorData": ${jsonString(visitorId)}"""
        } else ""

        val payload = """
            {
              "context": {
                "client": {
                  "clientName": "VISIONOS",
                  "clientVersion": "1.02",
                  "hl": "en",
                  "gl": "US"
                  $visitorField
                }
              },
              "videoId": ${jsonString(safeVideoId)}
            }
        """.trimIndent()

        val reqBuilder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(payload.toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .header("X-YouTube-Client-Name", "101")
            .header("X-YouTube-Client-Version", "1.02")

        if (!visitorId.isNullOrBlank()) {
            reqBuilder.header("X-Goog-Visitor-Id", visitorId)
        }

        val bodyStr = runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        if (bodyStr.isBlank()) return@withContext null

        val root = runCatching { json.parseToJsonElement(bodyStr).jsonObject }.getOrNull() ?: return@withContext null
        val playabilityStatus = root["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
        if (playabilityStatus != null && playabilityStatus != "OK") {
            return@withContext null
        }

        val formats = root["streamingData"]?.jsonObject
            ?.get("adaptiveFormats")?.jsonArray
            ?: return@withContext null

        val audioFormats = formats.mapNotNull { it.jsonObject }.filter { fmt ->
            val mime = fmt["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
            mime.startsWith("audio/") && !fmt["url"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
        }

        if (audioFormats.isEmpty()) return@withContext null

        // Priority: itag 251 (Opus ~160kbps) > itag 140 (m4a ~128kbps) > highest bitrate audio stream
        val bestFormat = audioFormats.firstOrNull { it["itag"]?.jsonPrimitive?.intOrNull == 251 }
            ?: audioFormats.firstOrNull { it["itag"]?.jsonPrimitive?.intOrNull == 140 }
            ?: audioFormats.maxByOrNull { it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 }
            ?: return@withContext null

        val streamUrl = bestFormat["url"]?.jsonPrimitive?.contentOrNull ?: return@withContext null

        val loudnessDb = root["playerConfig"]?.jsonObject
            ?.get("audioConfig")?.jsonObject
            ?.get("perceptualLoudnessDb")?.jsonPrimitive?.doubleOrNull
            ?: root["playerConfig"]?.jsonObject
                ?.get("audioConfig")?.jsonObject
                ?.get("trackAbsoluteLoudnessLkfs")?.jsonPrimitive?.doubleOrNull

        streamUrl to loudnessDb
    }

    suspend fun resolveFallbackStream(videoId: String): String? = withContext(Dispatchers.IO) {
        val safeVideoId = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (safeVideoId.isBlank()) return@withContext null

        val visitorId = getVisitorId()
        val clients = listOf(
            Triple("IOS", "19.45.4", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; US)"),
            Triple("WEB_REMIX", "1.20241118.01.00", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        )
        for ((name, version, ua) in clients) {
            val url = fetchFallbackPlayerStream(safeVideoId, name, version, ua, visitorId)
            if (!url.isNullOrBlank()) return@withContext url
        }
        null
    }

    private fun fetchFallbackPlayerStream(
        videoId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        visitorId: String?
    ): String? {
        val safeVideoId = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (safeVideoId.isBlank()) return null

        val visitorField = if (!visitorId.isNullOrBlank()) {
            """, "visitorData": ${jsonString(visitorId)}"""
        } else ""

        val payload = """
            {
              "context": {
                "client": {
                  "clientName": "$clientName",
                  "clientVersion": "$clientVersion",
                  "hl": "en",
                  "gl": "US"
                  $visitorField
                }
              },
              "videoId": ${jsonString(safeVideoId)}
            }
        """.trimIndent()

        val clientNumber = when (clientName) {
            "IOS" -> "5"
            "WEB_REMIX" -> "67"
            "ANDROID" -> "3"
            "VISIONOS" -> "101"
            else -> clientName
        }

        val reqBuilder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(payload.toRequestBody(jsonMediaType))
            .header("User-Agent", userAgent)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .header("X-YouTube-Client-Name", clientNumber)
            .header("X-YouTube-Client-Version", clientVersion)

        if (!visitorId.isNullOrBlank()) {
            reqBuilder.header("X-Goog-Visitor-Id", visitorId)
        }

        val bodyStr = runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
        }.getOrNull() ?: return null

        if (bodyStr.isBlank()) return null

        val root = runCatching { json.parseToJsonElement(bodyStr).jsonObject }.getOrNull() ?: return null
        val playabilityStatus = root["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
        if (playabilityStatus != null && playabilityStatus != "OK") {
            return null
        }

        val formats = root["streamingData"]?.jsonObject
            ?.get("adaptiveFormats")?.jsonArray
            ?: return null

        val audioFormats = formats.mapNotNull { it.jsonObject }.filter { fmt ->
            val mime = fmt["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
            mime.startsWith("audio/") && !fmt["url"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
        }

        val best = audioFormats.firstOrNull { it["itag"]?.jsonPrimitive?.intOrNull == 251 }
            ?: audioFormats.maxByOrNull { fmt -> fmt["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 }
        return best?.get("url")?.jsonPrimitive?.contentOrNull
    }

    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val safeVideoId = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (safeVideoId.isBlank()) return@withContext null
        resolveVisionOsStream(safeVideoId)?.first ?: resolveFallbackStream(safeVideoId)
    }

    suspend fun searchSongs(query: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val payload = """
            {
              "context": {
                "client": {
                  "clientName": "WEB_REMIX",
                  "clientVersion": "1.20241118.01.00",
                  "hl": "en",
                  "gl": "US"
                }
              },
              "query": ${jsonString(query)},
              "params": "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .post(payload.toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .build()

        val bodyStr = runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string().orEmpty()
            }
        }.getOrNull() ?: return@withContext emptyList()

        if (bodyStr.isBlank()) return@withContext emptyList()

        val root = runCatching { json.parseToJsonElement(bodyStr).jsonObject }.getOrNull() ?: return@withContext emptyList()
        val items = mutableListOf<JsonObject>()
        collectRenderers(root, "musicResponsiveListItemRenderer", items)

        items.mapNotNull { renderer -> parseTrack(renderer) }
    }

    private fun parseTrack(renderer: JsonObject): Track? {
        val videoId = renderer["playlistItemData"]?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: renderer["overlay"]?.jsonObject
                ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicPlayButtonRenderer")?.jsonObject
                ?.get("playNavigationEndpoint")?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: return null

        val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
        val title = flexColumns.getOrNull(0)?.let { extractColumnText(it) } ?: return null
        val subtitle = flexColumns.getOrNull(1)?.let { extractColumnText(it) }.orEmpty()
        val artist = subtitle.substringBefore(" • ").ifBlank { "Unknown Artist" }

        val thumbnails = renderer["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray

        val rawArtworkUrl = thumbnails?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
            .orEmpty()
        val artworkUrl = MusicSearchApi.upgradeArtworkQuality(rawArtworkUrl)

        return Track(
            id = videoId,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl
        )
    }

    private fun extractColumnText(element: JsonElement): String {
        val runs = element.jsonObject["musicResponsiveListItemFlexColumnRenderer"]
            ?.jsonObject?.get("text")
            ?.jsonObject?.get("runs")?.jsonArray
            ?: return ""
        return runs.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
    }

    private fun collectRenderers(element: JsonElement, key: String, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                element[key]?.let { if (it is JsonObject) out.add(it) }
                element.values.forEach { collectRenderers(it, key, out) }
            }
            is JsonArray -> element.forEach { collectRenderers(it, key, out) }
            else -> Unit
        }
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
        return "\"$escaped\""
    }
}
