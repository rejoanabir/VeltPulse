package com.veltusstudio.veltpulse.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.veltusstudio.veltpulse.data.model.LyricLine
import com.veltusstudio.veltpulse.data.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class OfflineCacheManager(
    private val context: Context,
    private val client: OkHttpClient
) {
    companion object {
        const val CHANNEL_ID = "veltpulse_downloads"
    }

    private val dir = File(context.filesDir, "offline_audio").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val indexMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val playlistTrackMap = ConcurrentHashMap<Job, Set<String>>()

    private val _downloaded = MutableStateFlow(loadIndex())
    val downloaded: StateFlow<List<SavedTrack>> = _downloaded.asStateFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private fun sanitizeTrackId(trackId: String): String {
        return trackId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    private fun isPathSafe(file: File): Boolean {
        return runCatching {
            file.canonicalPath.startsWith(dir.canonicalPath)
        }.getOrDefault(false)
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Offline track download progress"
                setShowBadge(false)
            }
            notifManager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(track: Track, progressPercent: Int, isDone: Boolean) {
        if (notifManager == null) return
        val notifId = (track.id.hashCode() and 0x7FFFFFFF)
        if (isDone) {
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Downloaded: ${track.title}")
                .setContentText("${track.artist} • Ready for offline listening")
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
            notifManager.notify(notifId, notif)
            downloadScope.launch {
                delay(4000L)
                notifManager.cancel(notifId)
            }
        } else {
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading: ${track.title}")
                .setContentText("${track.artist} • $progressPercent%")
                .setProgress(100, progressPercent, progressPercent <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            notifManager.notify(notifId, notif)
        }
    }

    private fun loadIndex(): List<SavedTrack> = runCatching {
        if (indexFile.exists()) json.decodeFromString<List<SavedTrack>>(indexFile.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private fun saveIndexAtomic(list: List<SavedTrack>) {
        runCatching {
            val tmpFile = File(dir, "index.json.tmp")
            if (isPathSafe(tmpFile)) {
                tmpFile.writeText(json.encodeToString(list))
                if (tmpFile.exists()) {
                    if (indexFile.exists()) indexFile.delete()
                    tmpFile.renameTo(indexFile)
                }
            }
        }
    }

    private suspend fun saveIndex(list: List<SavedTrack>) {
        indexMutex.withLock {
            saveIndexAtomic(list)
        }
    }

    fun isDownloaded(trackId: String): Boolean {
        val safeId = sanitizeTrackId(trackId)
        if (safeId.isBlank()) return false
        val f = File(dir, "$safeId.audio")
        return isPathSafe(f) && f.exists()
    }

    fun getLocalUri(trackId: String): String? {
        val safeId = sanitizeTrackId(trackId)
        if (safeId.isBlank()) return null
        val f = File(dir, "$safeId.audio")
        return if (isPathSafe(f) && f.exists() && f.length() > 0L) Uri.fromFile(f).toString() else null
    }

    fun saveLyrics(trackId: String, lyrics: List<LyricLine>) {
        if (lyrics.isEmpty()) return
        val safeId = sanitizeTrackId(trackId)
        if (safeId.isBlank()) return
        val targetFile = File(dir, "$safeId.lrc")
        if (!isPathSafe(targetFile)) return
        val content = lyrics.joinToString("\n") { line ->
            if (line.timeMs < 0L) line.text
            else {
                val m = (line.timeMs / 60_000L)
                val s = (line.timeMs % 60_000L) / 1000L
                val cs = (line.timeMs % 1000L) / 10L
                "[%02d:%02d.%02d]%s".format(m, s, cs, line.text)
            }
        }
        runCatching { targetFile.writeText(content) }
    }

    fun cancelDownload(trackId: String) {
        val safeId = sanitizeTrackId(trackId)
        activeCalls.remove(safeId)?.cancel()
        activeCalls.remove(trackId)?.cancel()
        downloadJobs.remove(safeId)?.cancel()
        downloadJobs.remove(trackId)?.cancel()
        _downloadingIds.update { it - safeId - trackId }
        _downloadProgress.update { it - safeId - trackId }
        runCatching {
            val tempFile = File(dir, "$safeId.tmp")
            if (isPathSafe(tempFile) && tempFile.exists()) tempFile.delete()
            val notifId = (safeId.hashCode() and 0x7FFFFFFF)
            notifManager?.cancel(notifId)
        }
    }

    fun cancelPlaylistDownload(tracks: List<Track>) {
        val targetIds = tracks.map { sanitizeTrackId(it.id) }.toSet()
        playlistTrackMap.entries.filter { it.value.any { id -> sanitizeTrackId(id) in targetIds } }.forEach { entry ->
            entry.key.cancel()
            playlistTrackMap.remove(entry.key)
        }
        targetIds.forEach { cancelDownload(it) }
    }

    fun cancelAllDownloads() {
        playlistTrackMap.keys.forEach { it.cancel() }
        playlistTrackMap.clear()
        val ids = _downloadingIds.value.toList()
        ids.forEach { cancelDownload(it) }
        activeCalls.values.forEach { runCatching { it.cancel() } }
        activeCalls.clear()
        downloadJobs.values.forEach { runCatching { it.cancel() } }
        downloadJobs.clear()
        _downloadingIds.update { emptySet() }
        _downloadProgress.update { emptyMap() }
    }

    fun startBackgroundDownload(track: Track, streamUrl: String, lyrics: List<LyricLine>): Job {
        val safeId = sanitizeTrackId(track.id)
        val job = downloadScope.launch {
            downloadTrack(track, streamUrl, lyrics)
        }
        downloadJobs[safeId] = job
        job.invokeOnCompletion { downloadJobs.remove(safeId) }
        return job
    }

    fun startBackgroundPlaylistDownload(
        tracks: List<Track>,
        resolveUrl: suspend (String) -> String?,
        resolveLyrics: (suspend (Track) -> List<LyricLine>)? = null
    ): Job {
        val trackIds = tracks.map { sanitizeTrackId(it.id) }.toSet()
        val job = downloadScope.launch {
            val toDownload = tracks.filterNot { isDownloaded(it.id) || sanitizeTrackId(it.id) in _downloadingIds.value }
            for (track in toDownload) {
                if (!isActive) break
                val url = track.streamUrl.ifBlank { resolveUrl(track.id).orEmpty() }
                if (url.isNotBlank() && isActive) {
                    val lyrics = resolveLyrics?.invoke(track) ?: readSavedLyrics(track.id)
                    downloadTrack(track, url, lyrics)
                }
            }
        }
        playlistTrackMap[job] = trackIds
        job.invokeOnCompletion { playlistTrackMap.remove(job) }
        return job
    }

    suspend fun downloadTrack(track: Track, streamUrl: String, lyrics: List<LyricLine>): Boolean = withContext(Dispatchers.IO) {
        val safeId = sanitizeTrackId(track.id)
        if (safeId.isBlank() || streamUrl.isBlank()) return@withContext false

        val targetFile = File(dir, "$safeId.audio")
        val tempFile = File(dir, "$safeId.tmp")
        if (!isPathSafe(targetFile) || !isPathSafe(tempFile)) return@withContext false

        _downloadingIds.update { it + safeId }
        _downloadProgress.update { it + (safeId to 0f) }
        updateNotification(track, 0, false)

        if (tempFile.exists()) tempFile.delete()

        var success = false
        try {
            val CHUNK_SIZE = 1024 * 1024L // 1MB chunks to bypass YouTube playback bitrate throttling
            var offset = 0L
            var totalBytes = -1L
            var isRangeSupported = true
            var lastReportedPct = -1
            var lastNotifTime = 0L
            var reachedEof = false

            while (isActive && !reachedEof && (totalBytes == -1L || offset < totalBytes)) {
                val reqBuilder = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Connection", "keep-alive")

                if (isRangeSupported) {
                    reqBuilder.header("Range", "bytes=$offset-${offset + CHUNK_SIZE - 1}")
                }

                val req = reqBuilder.build()
                val call = client.newCall(req)
                activeCalls[safeId] = call

                val response = try {
                    call.execute()
                } catch (e: Exception) {
                    activeCalls.remove(safeId)
                    throw e
                }

                response.use { resp ->
                    activeCalls.remove(safeId)
                    if (resp.code == 416) {
                        // Range Not Satisfiable -> reached EOF
                        reachedEof = true
                        return@use
                    }
                    if (!resp.isSuccessful) {
                        return@withContext false
                    }

                    val body = resp.body ?: return@withContext false

                    // If server ignored Range and returned 200 OK on first chunk, fallback to single stream
                    if (resp.code == 200 && isRangeSupported && offset == 0L) {
                        isRangeSupported = false
                        totalBytes = body.contentLength()
                        body.byteStream().use { input ->
                            tempFile.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var read = 0
                                while (isActive && input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    offset += read
                                    if (totalBytes > 0L) {
                                        val p = (offset.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                        val pct = (p * 100).toInt()
                                        if (pct != lastReportedPct) {
                                            lastReportedPct = pct
                                            _downloadProgress.update { it + (safeId to p) }
                                        }
                                        val now = System.currentTimeMillis()
                                        if (now - lastNotifTime > 400L) {
                                            lastNotifTime = now
                                            updateNotification(track, pct, false)
                                        }
                                    }
                                }
                            }
                        }
                        reachedEof = true
                        return@use
                    }

                    // 206 Partial Content
                    if (totalBytes == -1L) {
                        val cr = resp.header("Content-Range")
                        val parsedTotal = cr?.substringAfterLast('/', "")?.toLongOrNull()
                        totalBytes = parsedTotal ?: body.contentLength()
                    }

                    var chunkBytesRead = 0L
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(tempFile, true).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read = 0
                            while (isActive && input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                chunkBytesRead += read
                                offset += read
                                if (totalBytes > 0L) {
                                    val p = (offset.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    val pct = (p * 100).toInt()
                                    if (pct != lastReportedPct) {
                                        lastReportedPct = pct
                                        _downloadProgress.update { it + (safeId to p) }
                                    }
                                    val now = System.currentTimeMillis()
                                    if (now - lastNotifTime > 400L) {
                                        lastNotifTime = now
                                        updateNotification(track, pct, false)
                                    }
                                }
                            }
                        }
                    }

                    if (chunkBytesRead == 0L) {
                        reachedEof = true
                        return@use
                    }
                }
            }

            if (isActive && tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                saveLyrics(safeId, lyrics)
                updateNotification(track, 100, true)
                val saved = SavedTrack.from(track)
                indexMutex.withLock {
                    val cur = _downloaded.value
                    val next = listOf(saved) + cur.filterNot { it.id == track.id || it.id == safeId }
                    _downloaded.value = next
                    saveIndexAtomic(next)
                }
                success = true
            } else {
                if (isPathSafe(tempFile) && tempFile.exists()) tempFile.delete()
            }
        } catch (e: Exception) {
            if (isPathSafe(tempFile) && tempFile.exists()) tempFile.delete()
            val notifId = (safeId.hashCode() and 0x7FFFFFFF)
            notifManager?.cancel(notifId)
            success = false
        } finally {
            activeCalls.remove(safeId)
            _downloadProgress.update { it - safeId }
            _downloadingIds.update { it - safeId }
        }
        success
    }

    fun readSavedLyrics(trackId: String): List<LyricLine> {
        val safeId = sanitizeTrackId(trackId)
        if (safeId.isBlank()) return emptyList()
        val f = File(dir, "$safeId.lrc")
        if (!isPathSafe(f) || !f.exists()) return emptyList()
        val regex = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]\s*(.*)""")
        return f.readLines().mapNotNull { line ->
            val m = regex.find(line.trim())
            if (m != null) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val frac = m.groupValues[3]
                val ms = if (frac.isBlank()) 0L else if (frac.length == 2) (frac.toLongOrNull() ?: 0L) * 10L else (frac.toLongOrNull() ?: 0L)
                val txt = m.groupValues[4].trim()
                if (txt.isBlank()) null else LyricLine(min * 60_000L + sec * 1_000L + ms, txt)
            } else if (line.isNotBlank()) LyricLine(-1L, line.trim()) else null
        }
    }

    fun deleteTrack(trackId: String) {
        val safeId = sanitizeTrackId(trackId)
        if (safeId.isBlank()) return
        cancelDownload(safeId)
        val audioFile = File(dir, "$safeId.audio")
        val lrcFile = File(dir, "$safeId.lrc")
        if (isPathSafe(audioFile) && audioFile.exists()) audioFile.delete()
        if (isPathSafe(lrcFile) && lrcFile.exists()) lrcFile.delete()
        _downloaded.update { cur -> cur.filterNot { it.id == trackId || it.id == safeId } }
        downloadScope.launch {
            indexMutex.withLock {
                saveIndexAtomic(_downloaded.value)
            }
        }
    }
}
