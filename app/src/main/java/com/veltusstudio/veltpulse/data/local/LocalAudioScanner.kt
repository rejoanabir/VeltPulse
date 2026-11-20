package com.veltusstudio.veltpulse.data.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.veltusstudio.veltpulse.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalAudioScanner {
    suspend fun scanDeviceAudio(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
        runCatching {
            context.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                if (idCol == -1) return@use

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    val albumId = if (albumIdCol != -1) cursor.getLong(albumIdCol) else -1L
                    val artUri = if (albumId > 0) {
                        ContentUris.withAppendedId(
                            android.net.Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    } else ""

                    val title = if (titleCol != -1) cursor.getString(titleCol).orEmpty() else ""
                    val artist = if (artistCol != -1) cursor.getString(artistCol).orEmpty() else ""
                    val album = if (albumCol != -1) cursor.getString(albumCol).orEmpty() else ""
                    val dur = if (durCol != -1) cursor.getLong(durCol) else 0L

                    tracks.add(
                        Track(
                            id = "local_$id",
                            title = title.ifBlank { "Unknown Track" },
                            artist = artist.ifBlank { "Unknown Artist" },
                            album = album,
                            durationMs = dur,
                            artworkUrl = artUri,
                            streamUrl = contentUri
                        )
                    )
                }
            }
        }
        tracks
    }
}
