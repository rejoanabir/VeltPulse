package com.veltusstudio.veltpulse.data.generator

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.veltusstudio.veltpulse.data.model.Track
import java.io.File
import java.net.URLEncoder

object PlaylistExportHelper {

    fun exportToM3u(context: Context, title: String, tracks: List<Track>) {
        val cleanName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(context.cacheDir, "$cleanName.m3u")

        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#PLAYLIST:$title")
        tracks.forEach { t ->
            val durSec = if (t.durationMs > 0L) t.durationMs / 1000L else -1L
            sb.appendLine("#EXTINF:$durSec,${t.artist} - ${t.title}")
            val query = URLEncoder.encode("${t.artist} ${t.title}", "UTF-8")
            sb.appendLine("https://music.youtube.com/search?q=$query")
        }
        file.writeText(sb.toString())

        shareFile(context, file, "audio/x-mpegurl", "Export Playlist ($title) as M3U")
    }

    fun exportToCsv(context: Context, title: String, tracks: List<Track>) {
        val cleanName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(context.cacheDir, "$cleanName.csv")

        val sb = StringBuilder()
        sb.appendLine("Title,Artist,Album,YouTube Search URL")
        tracks.forEach { t ->
            val escTitle = t.title.replace("\"", "\"\"")
            val escArtist = t.artist.replace("\"", "\"\"")
            val escAlbum = t.album.replace("\"", "\"\"")
            val query = URLEncoder.encode("${t.artist} ${t.title}", "UTF-8")
            val url = "https://music.youtube.com/search?q=$query"
            sb.appendLine("\"$escTitle\",\"$escArtist\",\"$escAlbum\",\"$url\"")
        }
        file.writeText(sb.toString())

        shareFile(context, file, "text/csv", "Export Playlist ($title) as CSV")
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
