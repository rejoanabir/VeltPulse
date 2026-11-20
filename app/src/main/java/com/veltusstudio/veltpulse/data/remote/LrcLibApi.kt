package com.veltusstudio.veltpulse.data.remote

import com.veltusstudio.veltpulse.data.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class LrcLibApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val timeRegex = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]\s*(.*)""")

    private fun sanitizeTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)\(official\s*(music)?\s*(video|audio|lyric\s*video|visualizer)?\)"""), "")
            .replace(Regex("""(?i)\[official\s*(music)?\s*(video|audio|lyric\s*video|visualizer)?\]"""), "")
            .replace(Regex("""(?i)\[(mv|audio|visualizer)\]"""), "")
            .replace(Regex("""(?i)\b(official\s*(video|audio)|visualizer|lyrics?)\b"""), "")
            .replace(Regex("""\s*-\s*Topic$"""), "")
            .trim()
    }

    suspend fun fetchLyrics(title: String, artist: String): List<LyricLine> = withContext(Dispatchers.IO) {
        val cleanTitleStr = sanitizeTitle(title)
        val cleanTitle = URLEncoder.encode(cleanTitleStr.ifBlank { title.trim() }, "UTF-8")
        val cleanArtist = URLEncoder.encode(artist.replace(Regex("""\s*-\s*Topic$"""), "").trim(), "UTF-8")

        val getUrl = "https://lrclib.net/api/get?track_name=$cleanTitle&artist_name=$cleanArtist"
        val direct = fetchJson(getUrl)?.jsonObject
        val synced = direct?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
        if (!synced.isNullOrBlank()) return@withContext parseLrc(synced)

        val searchQuery = URLEncoder.encode("${cleanTitleStr.ifBlank { title }} $artist", "UTF-8")
        val searchUrl = "https://lrclib.net/api/search?q=$searchQuery"
        val arr = fetchJson(searchUrl)?.jsonArray
        val first = arr?.firstOrNull()?.jsonObject
        val fallbackSynced = first?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
        if (!fallbackSynced.isNullOrBlank()) return@withContext parseLrc(fallbackSynced)

        val plain = direct?.get("plainLyrics")?.jsonPrimitive?.contentOrNull
            ?: first?.get("plainLyrics")?.jsonPrimitive?.contentOrNull
        if (!plain.isNullOrBlank()) {
            return@withContext plain.lines().filter { it.isNotBlank() }.map { LyricLine(-1L, it.trim()) }
        }
        emptyList()
    }

    private fun fetchJson(url: String) = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", "VeltPulse/1.0").get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) null else json.parseToJsonElement(body)
        }
    }.getOrNull()

    private val wordTimeRegex = Regex("""<(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?>\s*([^<]+)""")
    private val wordTagStripRegex = Regex("""<\d{1,3}:\d{2}(?:\.\d{1,3})?>""")

    private fun parseLrc(lrc: String): List<LyricLine> {
        return lrc.lines().mapNotNull { line ->
            val match = timeRegex.find(line.trim()) ?: return@mapNotNull null
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val fracStr = match.groupValues[3]
            val ms = if (fracStr.isBlank()) 0L else if (fracStr.length == 2) (fracStr.toLongOrNull() ?: 0L) * 10L else (fracStr.toLongOrNull() ?: 0L)
            val lineStartMs = min * 60_000L + sec * 1_000L + ms
            val rawText = match.groupValues[4].trim()
            if (rawText.isBlank()) return@mapNotNull null

            val parsedWords = mutableListOf<com.veltusstudio.veltpulse.data.model.LyricWord>()
            val wordMatches = wordTimeRegex.findAll(rawText).toList()
            if (wordMatches.isNotEmpty()) {
                wordMatches.forEachIndexed { idx, wm ->
                    val wMin = wm.groupValues[1].toLongOrNull() ?: 0L
                    val wSec = wm.groupValues[2].toLongOrNull() ?: 0L
                    val wFrac = wm.groupValues[3]
                    val wMs = if (wFrac.isBlank()) 0L else if (wFrac.length == 2) (wFrac.toLongOrNull() ?: 0L) * 10L else (wFrac.toLongOrNull() ?: 0L)
                    val wStart = wMin * 60_000L + wSec * 1_000L + wMs
                    val wordContent = wm.groupValues[4].trim()

                    val nextStart = wordMatches.getOrNull(idx + 1)?.let { nwm ->
                        val nMin = nwm.groupValues[1].toLongOrNull() ?: 0L
                        val nSec = nwm.groupValues[2].toLongOrNull() ?: 0L
                        val nFrac = nwm.groupValues[3]
                        val nMs = if (nFrac.isBlank()) 0L else if (nFrac.length == 2) (nFrac.toLongOrNull() ?: 0L) * 10L else (nFrac.toLongOrNull() ?: 0L)
                        nMin * 60_000L + nSec * 1_000L + nMs
                    } ?: (wStart + (wordContent.length * 70L).coerceIn(200L, 1000L))

                    if (wordContent.isNotBlank()) {
                        parsedWords.add(com.veltusstudio.veltpulse.data.model.LyricWord(word = wordContent, startMs = wStart, endMs = nextStart))
                    }
                }
            }

            val cleanText = rawText.replace(wordTagStripRegex, "").trim()
            if (cleanText.isBlank()) null else LyricLine(timeMs = lineStartMs, text = cleanText, words = parsedWords)
        }
    }
}
