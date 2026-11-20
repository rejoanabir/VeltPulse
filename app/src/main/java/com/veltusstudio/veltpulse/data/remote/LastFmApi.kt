package com.veltusstudio.veltpulse.data.remote

import android.content.Context
import com.veltusstudio.veltpulse.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

@Serializable
data class LastFmUserStats(
    val username: String = "",
    val totalScrobbles: Long = 0L,
    val artistCount: Long = 0L,
    val albumCount: Long = 0L,
    val trackCount: Long = 0L,
    val avatarUrl: String = "",
    val listeningHours: Double = 0.0
)

@Serializable
data class LastFmTrack(
    val name: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val playcount: Long = 0L,
    val isNowPlaying: Boolean = false,
    val uts: Long = 0L
) {
    fun toTrack(fallbackIdPrefix: String = "lfm"): Track {
        return Track(
            id = "lfm:${artist} - ${name}",
            title = name,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl
        )
    }
}

@Serializable
data class GenreBreakdown(
    val name: String,
    val count: Int,
    val percentage: Int
)

class LastFmApi(
    context: Context,
    private val client: OkHttpClient
) {
    private val prefs = context.getSharedPreferences("veltpulse_lastfm", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://ws.audioscrobbler.com/2.0/"

    var username: String
        get() = prefs.getString("username", "").orEmpty()
        set(v) = prefs.edit().putString("username", v.trim()).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "").orEmpty()
        set(v) = prefs.edit().putString("api_key", v.trim()).apply()

    var apiSecret: String
        get() = prefs.getString("api_secret", "").orEmpty()
        set(v) = prefs.edit().putString("api_secret", v.trim()).apply()

    var sessionKey: String
        get() = prefs.getString("session_key", "").orEmpty()
        set(v) = prefs.edit().putString("session_key", v.trim()).apply()

    val isConnected: Boolean
        get() = username.isNotBlank() && apiKey.isNotBlank()

    private suspend fun apiGet(params: Map<String, String>): JsonElement? = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isBlank()) return@withContext null

        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("api_key", key)

        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "VeltPulse/1.0 (Android)")
            .get()
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) null else json.parseToJsonElement(body)
            }
        }.getOrNull()
    }

    suspend fun getUserInfo(user: String = username): LastFmUserStats? = withContext(Dispatchers.IO) {
        if (user.isBlank()) return@withContext null
        val root = apiGet(mapOf("method" to "user.getinfo", "user" to user))?.jsonObject ?: return@withContext null
        val u = root["user"]?.jsonObject ?: return@withContext null

        val playcount = u["playcount"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val artistCount = u["artist_count"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val albumCount = u["album_count"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val trackCount = u["track_count"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

        val images = u["image"]?.jsonArray
        val avatar = images?.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull.orEmpty()
        val hours = (playcount * 3.5) / 60.0

        LastFmUserStats(
            username = user,
            totalScrobbles = playcount,
            artistCount = artistCount,
            albumCount = albumCount,
            trackCount = trackCount,
            avatarUrl = avatar,
            listeningHours = hours
        )
    }

    suspend fun getRecentTracks(limit: Int = 30, user: String = username): List<LastFmTrack> = withContext(Dispatchers.IO) {
        if (user.isBlank()) return@withContext emptyList()
        val root = apiGet(
            mapOf("method" to "user.getrecenttracks", "user" to user, "limit" to limit.toString(), "extended" to "1")
        )?.jsonObject ?: return@withContext emptyList()

        val rawTracks = root["recenttracks"]?.jsonObject?.get("track")
        val trackArray = when (rawTracks) {
            is JsonArray -> rawTracks
            is JsonObject -> buildJsonArray { add(rawTracks) }
            else -> return@withContext emptyList()
        }

        trackArray.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val artistObj = obj["artist"]
            val artist = when (artistObj) {
                is JsonObject -> artistObj["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                is JsonPrimitive -> artistObj.content
                else -> "Unknown Artist"
            }
            val albumObj = obj["album"]?.jsonObject
            val album = albumObj?.get("#text")?.jsonPrimitive?.contentOrNull.orEmpty()
            val images = obj["image"]?.jsonArray
            val art = images?.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull.orEmpty()
            val isNowPlaying = obj["@attr"]?.jsonObject?.get("nowplaying")?.jsonPrimitive?.contentOrNull == "true"
            val uts = obj["date"]?.jsonObject?.get("uts")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

            LastFmTrack(
                name = name,
                artist = artist,
                album = album,
                artworkUrl = art,
                isNowPlaying = isNowPlaying,
                uts = uts
            )
        }
    }

    suspend fun getTopTracks(period: String = "overall", limit: Int = 30, user: String = username): List<LastFmTrack> = withContext(Dispatchers.IO) {
        if (user.isBlank()) return@withContext emptyList()
        val root = apiGet(
            mapOf("method" to "user.gettoptracks", "user" to user, "period" to period, "limit" to limit.toString())
        )?.jsonObject ?: return@withContext emptyList()

        val rawTracks = root["toptracks"]?.jsonObject?.get("track")
        val trackArray = when (rawTracks) {
            is JsonArray -> rawTracks
            is JsonObject -> buildJsonArray { add(rawTracks) }
            else -> return@withContext emptyList()
        }

        trackArray.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val artistObj = obj["artist"]?.jsonObject
            val artist = artistObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val playcount = obj["playcount"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            val images = obj["image"]?.jsonArray
            val art = images?.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull.orEmpty()

            LastFmTrack(name = name, artist = artist, artworkUrl = art, playcount = playcount)
        }
    }

    suspend fun getTopArtists(period: String = "overall", limit: Int = 20, user: String = username): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
        if (user.isBlank()) return@withContext emptyList()
        val root = apiGet(
            mapOf("method" to "user.gettopartists", "user" to user, "period" to period, "limit" to limit.toString())
        )?.jsonObject ?: return@withContext emptyList()

        val raw = root["topartists"]?.jsonObject?.get("artist")
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            else -> return@withContext emptyList()
        }

        arr.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val count = obj["playcount"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 1L
            name to count
        }
    }

    suspend fun getArtistTopTags(artist: String): List<String> = withContext(Dispatchers.IO) {
        if (artist.isBlank()) return@withContext emptyList()
        val root = apiGet(
            mapOf("method" to "artist.gettoptags", "artist" to artist)
        )?.jsonObject ?: return@withContext emptyList()

        val raw = root["toptags"]?.jsonObject?.get("tag")
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            else -> return@withContext emptyList()
        }

        arr.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
    }

    suspend fun deriveGenreAnalytics(period: String = "overall", user: String = username): List<GenreBreakdown> = withContext(Dispatchers.IO) {
        val topArtists = getTopArtists(period = period, limit = 15, user = user)
        if (topArtists.isEmpty()) return@withContext emptyList()

        val tagMap = mutableMapOf<String, Int>()
        topArtists.forEach { (artist, weight) ->
            runCatching {
                val tags = getArtistTopTags(artist).take(5)
                val w = (weight.coerceAtMost(50L)).toInt().coerceAtLeast(1)
                tags.forEach { rawTag ->
                    val tag = rawTag.lowercase().trim()
                    if (tag.length >= 3 && tag != "seen live") {
                        tagMap[tag] = (tagMap[tag] ?: 0) + w
                    }
                }
            }
        }

        val sorted = tagMap.entries.sortedByDescending { it.value }.take(15)
        val maxVal = sorted.firstOrNull()?.value ?: 1
        sorted.map { (name, count) ->
            GenreBreakdown(
                name = name.replaceFirstChar { it.uppercase() },
                count = count,
                percentage = ((count.toFloat() / maxVal.toFloat()) * 100f).toInt().coerceIn(4, 100)
            )
        }
    }

    suspend fun getSimilarTracks(track: String, artist: String, limit: Int = 25): List<LastFmTrack> = withContext(Dispatchers.IO) {
        val root = apiGet(
            mapOf("method" to "track.getsimilar", "track" to track, "artist" to artist, "limit" to (limit * 2).toString())
        )?.jsonObject ?: return@withContext emptyList()

        val raw = root["similartracks"]?.jsonObject?.get("track")
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            else -> return@withContext emptyList()
        }

        arr.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val artistObj = obj["artist"]?.jsonObject
            val artName = artistObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val images = obj["image"]?.jsonArray
            val art = images?.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull.orEmpty()
            LastFmTrack(name = name, artist = artName, artworkUrl = art)
        }.distinctBy { "${it.name}|${it.artist}".lowercase() }.take(limit)
    }

    suspend fun getSimilarArtists(artist: String, limit: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val root = apiGet(
            mapOf("method" to "artist.getsimilar", "artist" to artist, "limit" to limit.toString())
        )?.jsonObject ?: return@withContext emptyList()

        val raw = root["similarartists"]?.jsonObject?.get("artist")
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            else -> return@withContext emptyList()
        }

        arr.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
    }

    suspend fun getArtistTopTracks(artist: String, limit: Int = 10): List<LastFmTrack> = withContext(Dispatchers.IO) {
        val root = apiGet(
            mapOf("method" to "artist.gettoptracks", "artist" to artist, "limit" to limit.toString())
        )?.jsonObject ?: return@withContext emptyList()

        val raw = root["toptracks"]?.jsonObject?.get("track")
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            else -> return@withContext emptyList()
        }

        arr.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val artistObj = obj["artist"]?.jsonObject
            val artName = artistObj?.get("name")?.jsonPrimitive?.contentOrNull ?: artist
            val images = obj["image"]?.jsonArray
            val art = images?.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull.orEmpty()
            LastFmTrack(name = name, artist = artName, artworkUrl = art)
        }
    }

    suspend fun getTagTopTracks(tag: String, limit: Int = 30): List<LastFmTrack> = withContext(Dispatchers.IO) {
        val root = apiGet(
            mapOf("method" to "tag.gettoptracks", "tag" to tag.lowercase(), "limit" to limit.toString())
        )?.jsonObject ?: return@withContext emptyList()

        val raw = root["tracks"]?.jsonObject?.get("track")
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            else -> return@withContext emptyList()
        }

        arr.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val artistObj = obj["artist"]?.jsonObject
            val artName = artistObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val images = obj["image"]?.jsonArray
            val art = images?.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull.orEmpty()
            LastFmTrack(name = name, artist = artName, artworkUrl = art)
        }
    }

    suspend fun searchTracks(query: String, limit: Int = 20): List<LastFmTrack> = withContext(Dispatchers.IO) {
        val root = apiGet(
            mapOf("method" to "track.search", "track" to query, "limit" to limit.toString())
        )?.jsonObject ?: return@withContext emptyList()

        val raw = root["results"]?.jsonObject?.get("trackmatches")?.jsonObject?.get("track")
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            else -> return@withContext emptyList()
        }

        arr.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val images = obj["image"]?.jsonArray
            val art = images?.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull.orEmpty()
            LastFmTrack(name = name, artist = artist, artworkUrl = art)
        }
    }

    suspend fun searchArtists(query: String, limit: Int = 15): List<String> = withContext(Dispatchers.IO) {
        val root = apiGet(
            mapOf("method" to "artist.search", "artist" to query, "limit" to limit.toString())
        )?.jsonObject ?: return@withContext emptyList()

        val raw = root["results"]?.jsonObject?.get("artistmatches")?.jsonObject?.get("artist")
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> buildJsonArray { add(raw) }
            else -> return@withContext emptyList()
        }

        arr.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
    }

    suspend fun scrobble(artist: String, track: String) = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiSecret.isBlank() || sessionKey.isBlank()) return@withContext
        val ts = (System.currentTimeMillis() / 1000L).toString()
        val params = sortedMapOf(
            "api_key" to apiKey,
            "artist" to artist,
            "method" to "track.scrobble",
            "sk" to sessionKey,
            "timestamp" to ts,
            "track" to track
        )
        val sigBase = params.entries.joinToString("") { "${it.key}${it.value}" } + apiSecret
        val sig = MessageDigest.getInstance("MD5").digest(sigBase.toByteArray()).joinToString("") { "%02x".format(it) }

        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
            add("api_sig", sig)
            add("format", "json")
        }.build()

        runCatching {
            client.newCall(Request.Builder().url(baseUrl).post(form).build()).execute().close()
        }
    }
}
