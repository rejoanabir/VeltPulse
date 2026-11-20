package com.veltusstudio.veltpulse.data.local

import android.content.Context
import com.veltusstudio.veltpulse.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String = "",
    val streamUrl: String = ""
) {
    fun toTrack() = Track(id = id, title = title, artist = artist, album = album, durationMs = durationMs, artworkUrl = artworkUrl, streamUrl = streamUrl)
    companion object {
        fun from(t: Track) = SavedTrack(t.id, t.title, t.artist, t.album, t.durationMs, t.artworkUrl, t.streamUrl)
    }
}

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val tracks: List<SavedTrack> = emptyList(),
    val description: String = "",
    val customArtworkUrl: String = ""
)

@Serializable
data class LibrarySnapshot(
    val liked: List<SavedTrack> = emptyList(),
    val history: List<SavedTrack> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList()
)

class LibraryRepository(context: Context) {
    private val file = File(context.filesDir, "veltpulse_library.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _state = MutableStateFlow(loadSync())
    val state: StateFlow<LibrarySnapshot> = _state.asStateFlow()

    private fun loadSync(): LibrarySnapshot = runCatching {
        if (file.exists()) json.decodeFromString<LibrarySnapshot>(file.readText()) else LibrarySnapshot()
    }.getOrDefault(LibrarySnapshot())

    private val ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private fun persist(snapshot: LibrarySnapshot) {
        ioScope.launch { runCatching { file.writeText(json.encodeToString(snapshot)) } }
    }

    fun isLiked(trackId: String): Boolean = _state.value.liked.any { it.id == trackId }

    fun toggleLike(track: Track) {
        val saved = SavedTrack.from(track)
        _state.update { cur ->
            val exists = cur.liked.any { it.id == track.id }
            val nextLiked = if (exists) cur.liked.filterNot { it.id == track.id } else listOf(saved) + cur.liked
            cur.copy(liked = nextLiked).also(::persist)
        }
    }

    fun recordPlay(track: Track) {
        val saved = SavedTrack.from(track)
        _state.update { cur ->
            val nextHistory = (listOf(saved) + cur.history.filterNot { it.id == track.id }).take(50)
            cur.copy(history = nextHistory).also(::persist)
        }
    }

    fun createPlaylist(name: String, description: String = "", customArtworkUrl: String = "") {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.update { cur ->
            val next = cur.playlists + LocalPlaylist(
                id = "pl_${System.currentTimeMillis()}",
                name = trimmed,
                description = description.trim(),
                customArtworkUrl = customArtworkUrl.trim()
            )
            cur.copy(playlists = next).also(::persist)
        }
    }

    fun saveImportedPlaylist(name: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val saved = tracks.map { SavedTrack.from(it) }
        _state.update { cur ->
            val next = cur.playlists + LocalPlaylist(id = "pl_${System.currentTimeMillis()}", name = name, tracks = saved)
            cur.copy(playlists = next).also(::persist)
        }
    }

    fun clearHistory() {
        _state.update { cur -> cur.copy(history = emptyList()).also(::persist) }
    }

    fun removeFromHistory(trackId: String) {
        _state.update { cur -> cur.copy(history = cur.history.filterNot { it.id == trackId }).also(::persist) }
    }

    fun deletePlaylist(playlistId: String) {
        _state.update { cur -> cur.copy(playlists = cur.playlists.filterNot { it.id == playlistId }).also(::persist) }
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        val saved = SavedTrack.from(track)
        _state.update { cur ->
            val next = cur.playlists.map { pl ->
                if (pl.id == playlistId && pl.tracks.none { it.id == track.id }) pl.copy(tracks = pl.tracks + saved) else pl
            }
            cur.copy(playlists = next).also(::persist)
        }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        _state.update { cur ->
            val next = cur.playlists.map { pl ->
                if (pl.id == playlistId) pl.copy(tracks = pl.tracks.filterNot { it.id == trackId }) else pl
            }
            cur.copy(playlists = next).also(::persist)
        }
    }

    fun togglePlaylist(playlistId: String, track: Track) {
        val inPl = _state.value.playlists.firstOrNull { it.id == playlistId }?.tracks?.any { it.id == track.id } == true
        if (inPl) {
            removeFromPlaylist(playlistId, track.id)
        } else {
            addToPlaylist(playlistId, track)
        }
    }
}
