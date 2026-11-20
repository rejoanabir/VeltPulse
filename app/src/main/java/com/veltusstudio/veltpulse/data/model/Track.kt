package com.veltusstudio.veltpulse.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String = "",
    val streamUrl: String = ""
)

@Immutable
data class PlaybackUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatOne: Boolean = false,
    val speed: Float = 1.0f,
    val skipSilence: Boolean = false,
    val sleepTimerRemainingSec: Int? = null,
    val crossfadeSec: Int = 0
)
