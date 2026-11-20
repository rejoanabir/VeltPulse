package com.veltusstudio.veltpulse.data.remote

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient
import com.veltusstudio.veltpulse.data.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

class MusicSearchApi(okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val innerTubeApi = InnerTubeApi(okHttpClient)

    private val ktorClient = HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
        install(ContentNegotiation) { json(json) }
    }

    private val innerTube = InnerTube(httpClient = ktorClient)
    private val configStore = RemotePlayerConfigStore(
        httpClient = innerTube.httpClient,
        repository = PlayerConfigRepository.disabled()
    )
    private val cipherService = YouTubeCipherService(innerTube.httpClient, configStore)
    private val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(
            httpClient = innerTube.httpClient,
            innerTube = innerTube,
            remotePlayerConfigStore = configStore,
            cipherService = cipherService
        ),
        cipherService = cipherService,
        innerTube = innerTube
    )

    data class ResolvedAudio(val url: String, val loudnessDb: Double?)

    suspend fun getRadioUpNext(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        val responseText = innerTube.next(client = YouTubeClient.WEB_REMIX, videoId = videoId, playlistId = "RDAMVM$videoId").bodyAsText()
        val root = json.parseToJsonElement(responseText).jsonObject
        val items = mutableListOf<JsonObject>()
        collectRenderers(root, "playlistPanelVideoRenderer", items)
        items.mapNotNull { r ->
            val id = r["videoId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = r["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: r["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val artist = r["shortBylineText"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: r["shortBylineText"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                ?: "Unknown Artist"
            val rawArt = r["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
            val art = upgradeArtworkQuality(rawArt)
            Track(id = id, title = title, artist = artist, artworkUrl = art)
        }.filter { it.id != videoId }.distinctBy { it.id }
    }

    suspend fun importYouTubePlaylist(urlOrId: String): Pair<String, List<Track>> = withContext(Dispatchers.IO) {
        val rawId = urlOrId.substringAfter("list=", urlOrId).substringBefore("&").trim()
        val browseId = if (rawId.startsWith("VL")) rawId else "VL$rawId"
        val responseText = innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = browseId).bodyAsText()
        val root = json.parseToJsonElement(responseText).jsonObject
        val items = mutableListOf<JsonObject>()
        collectRenderers(root, "musicResponsiveListItemRenderer", items)
        val tracks = items.mapNotNull(::parseTrack).distinctBy { it.id }
        "Imported Playlist (${tracks.size})" to tracks
    }

    suspend fun getHomeQuickPicks(moodQuery: String? = null): List<Track> = withContext(Dispatchers.IO) {
        if (!moodQuery.isNullOrBlank()) return@withContext searchTracks(moodQuery)
        val responseText = innerTube.browse(client = YouTubeClient.WEB_REMIX, browseId = "FEmusic_home").bodyAsText()
        val root = json.parseToJsonElement(responseText).jsonObject
        val items = mutableListOf<JsonObject>()
        collectRenderers(root, "musicResponsiveListItemRenderer", items)
        items.mapNotNull(::parseTrack).distinctBy { it.id }.take(25)
    }

    suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val responseText = innerTube.search(
            client = YouTubeClient.WEB_REMIX,
            query = query.trim(),
            params = "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D"
        ).bodyAsText()

        val root = json.parseToJsonElement(responseText).jsonObject
        val items = mutableListOf<JsonObject>()
        collectRenderers(root, "musicResponsiveListItemRenderer", items)
        items.mapNotNull(::parseTrack).distinctBy { it.id }
    }

    suspend fun resolveAudio(videoId: String): ResolvedAudio? = withContext(Dispatchers.IO) {
        val safeVideoId = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (safeVideoId.isBlank()) return@withContext null

        // Tier 1 (Primary): Ultra-fast VISIONOS client with visitor ID cache (~300ms, direct opus/m4a streams)
        val visionOsResult = runCatching {
            innerTubeApi.resolveVisionOsStream(safeVideoId)
        }.getOrNull()

        if (visionOsResult != null && visionOsResult.first.isNotBlank()) {
            return@withContext ResolvedAudio(url = visionOsResult.first, loudnessDb = visionOsResult.second)
        }

        // Tier 2: Multi-client InnerTubeApi fallback (IOS, WEB_REMIX)
        val fallbackUrl = runCatching {
            innerTubeApi.resolveFallbackStream(safeVideoId)
        }.getOrNull()

        if (!fallbackUrl.isNullOrBlank()) {
            return@withContext ResolvedAudio(url = fallbackUrl, loudnessDb = null)
        }

        // Tier 3: InnerTubeExtractor fallback
        val extractorResult = runCatching {
            extractor.extract(
                videoId = safeVideoId,
                hints = ContentHints(wantVideo = false),
                audioQuality = AudioQuality.HIGH
            )
        }.getOrNull()

        if (extractorResult != null && extractorResult.audioUrl.isNotBlank()) {
            return@withContext ResolvedAudio(url = extractorResult.audioUrl, loudnessDb = extractorResult.loudnessDb)
        }

        null
    }

    suspend fun resolveTrackByQuery(query: String): ResolvedAudio? = withContext(Dispatchers.IO) {
        val results = searchTracks(query)
        val first = results.firstOrNull() ?: return@withContext null
        resolveAudio(first.id)
    }

    suspend fun resolveStreamUrl(videoId: String): String? = resolveAudio(videoId)?.url

    companion object {
        fun upgradeArtworkQuality(url: String): String {
            if (url.isBlank()) return url
            var result = url
            if (result.contains("googleusercontent.com") || result.contains("ggpht.com") || result.contains("yt3.ggpht.com")) {
                result = if (result.contains(Regex("=w\\d+-h\\d+[^&?]*"))) {
                    result.replace(Regex("=w\\d+-h\\d+[^&?]*"), "=w800-h800-l90-rj")
                } else if (result.contains(Regex("=s\\d+[^&?]*"))) {
                    result.replace(Regex("=s\\d+[^&?]*"), "=s800")
                } else {
                    result.substringBefore("=") + "=w800-h800-l90-rj"
                }
            } else if (result.contains("i.ytimg.com/vi/")) {
                result = result.replace("/default.jpg", "/hqdefault.jpg")
                    .replace("/mqdefault.jpg", "/hqdefault.jpg")
            } else if (result.contains("lastfm.freetls.fastly.net/i/u/")) {
                result = result.replace("/34s/", "/300x300/")
                    .replace("/64s/", "/300x300/")
                    .replace("/174s/", "/300x300/")
            }
            return result
        }
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
        val title = flexColumns.getOrNull(0)?.let(::extractColumnText) ?: return null
        val subtitle = flexColumns.getOrNull(1)?.let(::extractColumnText).orEmpty()
        val artist = subtitle.substringBefore(" • ").ifBlank { "Unknown Artist" }

        val thumbnails = renderer["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray

        val rawArtworkUrl = thumbnails?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
            .orEmpty()
        val artworkUrl = upgradeArtworkQuality(rawArtworkUrl)

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
}
