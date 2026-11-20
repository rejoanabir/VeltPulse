package com.veltusstudio.veltpulse.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

@Serializable
data class YouTubeAccount(
    val isLoggedIn: Boolean = false,
    val name: String = "",
    val handle: String = "",
    val avatarUrl: String = "",
    val rawCookie: String = ""
)

@Serializable
data class YouTubeRemotePlaylist(
    val id: String,
    val title: String,
    val trackCountText: String = "",
    val thumbnail: String = ""
)

class YouTubeAccountManager(context: Context) {
    private val prefs = context.getSharedPreferences("veltpulse_youtube_auth", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(loadAccount())
    val account: StateFlow<YouTubeAccount> = _account.asStateFlow()

    val isLoggedIn: Boolean get() = _account.value.isLoggedIn
    val name: String get() = _account.value.name
    val rawCookie: String get() = _account.value.rawCookie

    private fun loadAccount(): YouTubeAccount {
        val cookie = prefs.getString("raw_cookie", "").orEmpty()
        val name = prefs.getString("name", "").orEmpty()
        val handle = prefs.getString("handle", "").orEmpty()
        val avatar = prefs.getString("avatar_url", "").orEmpty()
        val loggedIn = cookie.isNotBlank() && (cookie.contains("SAPISID") || cookie.contains("LOGIN_INFO") || cookie.contains("__Secure-3PAPISID"))
        return YouTubeAccount(
            isLoggedIn = loggedIn,
            name = name,
            handle = handle,
            avatarUrl = avatar,
            rawCookie = cookie
        )
    }

    fun saveCookies(cookie: String) {
        val trimmed = cookie.trim()
        val loggedIn = trimmed.isNotBlank() && (trimmed.contains("SAPISID") || trimmed.contains("LOGIN_INFO") || trimmed.contains("__Secure-3PAPISID"))
        prefs.edit()
            .putString("raw_cookie", trimmed)
            .apply()
        _account.update { it.copy(isLoggedIn = loggedIn, rawCookie = trimmed) }
    }

    fun updateProfile(name: String, handle: String, avatarUrl: String) {
        prefs.edit()
            .putString("name", name)
            .putString("handle", handle)
            .putString("avatar_url", avatarUrl)
            .apply()
        _account.update { it.copy(name = name, handle = handle, avatarUrl = avatarUrl) }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _account.value = YouTubeAccount()
        runCatching {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        }
    }

    fun getAuthHeaders(): Map<String, String> {
        val cookie = _account.value.rawCookie
        if (cookie.isBlank()) return emptyMap()

        val headers = mutableMapOf<String, String>()
        headers["Cookie"] = cookie
        headers["Origin"] = "https://music.youtube.com"
        headers["Referer"] = "https://music.youtube.com/"
        headers["X-Origin"] = "https://music.youtube.com"

        val sapisid = extractSapisid(cookie)
        if (!sapisid.isNullOrBlank()) {
            headers["Authorization"] = generateSapisidHash(sapisid)
        }
        return headers
    }

    suspend fun fetchAccountProfile(client: OkHttpClient): Boolean = withContext(Dispatchers.IO) {
        val authHeaders = getAuthHeaders()
        if (authHeaders.isEmpty()) return@withContext false

        val payload = """
            {
              "context": {
                "client": {
                  "clientName": "WEB_REMIX",
                  "clientVersion": "1.20241118.01.00",
                  "hl": "en",
                  "gl": "US"
                }
              }
            }
        """.trimIndent()

        val reqBuilder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

        authHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val responseStr = runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()
            }
        }.getOrNull()

        if (!responseStr.isNullOrBlank()) {
            runCatching {
                val json = Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(responseStr).jsonObject
                val actions = root["actions"]?.jsonArray
                val popup = actions?.firstOrNull()?.jsonObject
                    ?.get("openPopupAction")?.jsonObject
                    ?.get("popup")?.jsonObject
                    ?.get("multiPageMenuRenderer")?.jsonObject
                val header = popup?.get("header")?.jsonObject
                    ?.get("activeAccountHeaderRenderer")?.jsonObject

                if (header != null) {
                    val accName = header["accountName"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: header["accountName"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                        ?: "YouTube User"
                    val handle = header["channelHandle"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: header["email"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: ""
                    val avatar = header["accountPhoto"]?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                        ?: ""
                    updateProfile(accName, handle, avatar)
                    return@withContext true
                }
            }
        }

        // Fallback: If account_menu didn't return header, still keep logged in state
        if (_account.value.name.isBlank()) {
            updateProfile("YouTube Music Member", "", "")
        }
        true
    }

    suspend fun fetchUserPlaylists(client: OkHttpClient): List<YouTubeRemotePlaylist> = withContext(Dispatchers.IO) {
        val authHeaders = getAuthHeaders()
        if (authHeaders.isEmpty()) return@withContext emptyList()

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
              "browseId": "FEmusic_liked_playlists"
            }
        """.trimIndent()

        val reqBuilder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

        authHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val responseStr = runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()
            }
        }.getOrNull() ?: return@withContext emptyList()

        val result = mutableListOf<YouTubeRemotePlaylist>()
        runCatching {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(responseStr).jsonObject
            val renderers = mutableListOf<JsonObject>()
            collectRenderers(root, "musicTwoRowItemRenderer", renderers)
            collectRenderers(root, "musicResponsiveListItemRenderer", renderers)

            for (r in renderers) {
                val title = r["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: r["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                    ?: continue

                val nav = r["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject
                val browseId = nav?.get("browseId")?.jsonPrimitive?.contentOrNull ?: continue

                val subtitle = r["subtitle"]?.jsonObject?.get("runs")?.jsonArray?.joinToString(" • ") {
                    it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                } ?: ""

                val thumb = r["thumbnailRenderer"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                    ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: r["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: ""

                result.add(
                    YouTubeRemotePlaylist(
                        id = browseId,
                        title = title,
                        trackCountText = subtitle,
                        thumbnail = thumb
                    )
                )
            }
        }
        result.distinctBy { it.id }
    }

    private fun collectRenderers(element: JsonElement, targetKey: String, sink: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey(targetKey)) {
                    element[targetKey]?.jsonObject?.let { sink.add(it) }
                }
                for (v in element.values) collectRenderers(v, targetKey, sink)
            }
            is JsonArray -> {
                for (item in element) collectRenderers(item, targetKey, sink)
            }
            else -> {}
        }
    }

    companion object {
        fun extractSapisid(cookie: String): String? {
            val regex = Regex("""(?:^|;\s*)(?:SAPISID|__Secure-3PAPISID)=([^;]+)""")
            return regex.find(cookie)?.groupValues?.get(1)?.trim()
        }

        fun generateSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
            val timestamp = System.currentTimeMillis() / 1000
            val input = "$timestamp $sapisid $origin"
            val sha1 = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            val hash = sha1.joinToString("") { "%02x".format(it) }
            return "SAPISIDHASH ${timestamp}_$hash"
        }
    }
}
