package com.veltusstudio.veltpulse.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.veltusstudio.veltpulse.data.model.PlaybackUiState
import com.veltusstudio.veltpulse.data.model.Track
import com.veltusstudio.veltpulse.data.remote.MusicSearchApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.pow

class PlayerController(
    private val context: Context,
    private val resolveAudio: suspend (String) -> MusicSearchApi.ResolvedAudio?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var playJob: Job? = null
    private var baseTrackVolume: Float = 1.0f
    private var skipSegments: List<com.veltusstudio.veltpulse.data.remote.SkipSegment> = emptyList()
    private var consecutiveErrors = 0
    private var consecutiveResolutionFailures = 0
    var onTrackStarted: ((Track) -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null
    var localUriProvider: ((String) -> String?)? = null
    var sponsorBlockProvider: (suspend (String) -> List<com.veltusstudio.veltpulse.data.remote.SkipSegment>)? = null
    var radioProvider: (suspend (String) -> List<Track>)? = null

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val dur = player.duration.coerceAtLeast(0L)
            val pos = player.currentPosition.coerceAtLeast(0L)
            _positionMs.value = pos
            _uiState.update {
                it.copy(
                    isPlaying = player.isPlaying,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING,
                    durationMs = dur,
                    positionMs = pos
                )
            }
            if (player.isPlaying) startPositionTicker() else stopPositionTicker()
        }

        override fun onPlayerError(error: PlaybackException) {
            consecutiveErrors++
            val curTrack = _uiState.value.currentTrack
            val queueSize = _uiState.value.queue.size
            val msg = "Playback error on ${curTrack?.title ?: "track"}"
            onPlaybackError?.invoke(msg)
            if (consecutiveErrors < queueSize && queueSize > 1) {
                Toast.makeText(context, "$msg. Skipping to next...", Toast.LENGTH_SHORT).show()
                scope.launch {
                    delay(300L)
                    skipNext()
                }
            } else {
                Toast.makeText(context, "$msg. Playback stopped.", Toast.LENGTH_SHORT).show()
                consecutiveErrors = 0
                stopPlayback()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                consecutiveErrors = 0
                consecutiveResolutionFailures = 0
            } else if (playbackState == Player.STATE_ENDED) {
                if (_uiState.value.repeatOne) {
                    seekTo(0L)
                    mediaController?.play()
                } else {
                    skipNext()
                }
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { controller ->
                mediaController = controller
                PlaybackService.onSkipNext = { skipNext() }
                PlaybackService.onSkipPrevious = { skipPrevious() }
                val existingItem = controller.currentMediaItem
                if (existingItem != null && _uiState.value.currentTrack == null) {
                    val m = existingItem.mediaMetadata
                    val restored = Track(
                        id = existingItem.mediaId,
                        title = m.title?.toString().orEmpty().ifBlank { "Playing" },
                        artist = m.artist?.toString().orEmpty(),
                        album = m.albumTitle?.toString().orEmpty(),
                        artworkUrl = m.artworkUri?.toString().orEmpty()
                    )
                    _uiState.update {
                        it.copy(
                            currentTrack = restored,
                            queue = if (it.queue.isEmpty()) listOf(restored) else it.queue,
                            currentIndex = if (it.currentIndex < 0) 0 else it.currentIndex,
                            isPlaying = controller.isPlaying,
                            durationMs = controller.duration.coerceAtLeast(0L),
                            positionMs = controller.currentPosition.coerceAtLeast(0L)
                        )
                    }
                    if (controller.isPlaying) startPositionTicker()
                }
                controller.addListener(playerListener)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return
        _uiState.update { it.copy(queue = tracks) }
        playAtIndex(startIndex)
    }

    fun playTrack(track: Track) = playQueue(listOf(track), 0)

    fun playAtIndex(index: Int) {
        val queue = _uiState.value.queue
        val track = queue.getOrNull(index) ?: return
        _positionMs.value = 0L
        _uiState.update {
            it.copy(currentTrack = track, currentIndex = index, isBuffering = true, positionMs = 0L, durationMs = track.durationMs)
        }

        skipSegments = emptyList()
        playJob?.cancel()
        playJob = scope.launch {
            val localUri = localUriProvider?.invoke(track.id)
            val resolvedAudio = when {
                localUri != null -> MusicSearchApi.ResolvedAudio(localUri, null)
                track.streamUrl.startsWith("content://") || track.streamUrl.startsWith("file://") -> MusicSearchApi.ResolvedAudio(track.streamUrl, null)
                track.id.startsWith("local_") -> {
                    val audioId = track.id.removePrefix("local_").toLongOrNull() ?: 0L
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId).toString()
                    MusicSearchApi.ResolvedAudio(contentUri, null)
                }
                else -> runCatching { resolveAudio(track.id) }.getOrNull()
            }
            skipSegments = sponsorBlockProvider?.let { runCatching { it(track.id) }.getOrDefault(emptyList()) } ?: emptyList()
            if (resolvedAudio == null || resolvedAudio.url.isBlank()) {
                _uiState.update { it.copy(isBuffering = false) }
                consecutiveResolutionFailures++
                val queueSize = _uiState.value.queue.size
                val msg = "Unable to play '${track.title}'"
                onPlaybackError?.invoke(msg)
                if (consecutiveResolutionFailures < queueSize && queueSize > 1) {
                    Toast.makeText(context, "$msg. Skipping to next...", Toast.LENGTH_SHORT).show()
                    delay(300L)
                    skipNext()
                } else {
                    Toast.makeText(context, "$msg. Check network connection.", Toast.LENGTH_SHORT).show()
                    consecutiveResolutionFailures = 0
                    mediaController?.stop()
                    mediaController?.clearMediaItems()
                    _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
                }
                return@launch
            }
            consecutiveResolutionFailures = 0
            val resolved = track.copy(streamUrl = resolvedAudio.url)
            val controller = mediaController ?: run {
                if (controllerFuture == null) connect()
                runCatching {
                    withContext(Dispatchers.IO) {
                        controllerFuture?.get(3, java.util.concurrent.TimeUnit.SECONDS)
                    }
                }.getOrNull()?.also { mediaController = it }
            }
            if (controller == null) {
                _uiState.update { it.copy(isBuffering = false) }
                return@launch
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(resolved.title)
                .setArtist(resolved.artist)
                .setAlbumTitle(resolved.album)
                .setArtworkUri(if (resolved.artworkUrl.isNotBlank()) Uri.parse(resolved.artworkUrl) else null)
                .build()
            val mediaItem = MediaItem.Builder()
                .setMediaId(resolved.id)
                .setUri(resolved.streamUrl)
                .setMediaMetadata(metadata)
                .build()

            // Loudness normalization targeting -14 LUFS
            val targetVolume = resolvedAudio.loudnessDb?.let { db ->
                10f.pow((-14f - db.toFloat()) / 20f).coerceIn(0.25f, 1.0f)
            } ?: 1.0f
            baseTrackVolume = targetVolume
            controller.volume = targetVolume

            _uiState.update { it.copy(currentTrack = resolved) }
            controller.setMediaItem(mediaItem)
            controller.playbackParameters = PlaybackParameters(_uiState.value.speed)
            controller.prepare()
            controller.play()
            onTrackStarted?.invoke(resolved)
        }
    }

    fun removeFromQueue(index: Int) {
        val cur = _uiState.value
        if (cur.queue.isEmpty()) return
        if (index !in cur.queue.indices || index == cur.currentIndex) return
        val updated = cur.queue.toMutableList().apply { removeAt(index) }
        val newIndex = if (index < cur.currentIndex) cur.currentIndex - 1 else cur.currentIndex
        _uiState.update { it.copy(queue = updated, currentIndex = newIndex) }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        val p = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(p)
        _positionMs.value = p
        _uiState.update { it.copy(positionMs = p) }
    }

    fun toggleShuffle() = _uiState.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
    fun toggleRepeatOne() = _uiState.update { it.copy(repeatOne = !it.repeatOne) }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _uiState.update { it.copy(speed = clamped) }
        mediaController?.playbackParameters = PlaybackParameters(clamped)
    }

    @OptIn(UnstableApi::class)
    fun cycleCrossfade() {
        val options = listOf(0, 3, 6)
        _uiState.update { cur -> cur.copy(crossfadeSec = options[(options.indexOf(cur.crossfadeSec) + 1) % options.size]) }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun toggleSkipSilence() {
        val next = !_uiState.value.skipSilence
        _uiState.update { it.copy(skipSilence = next) }
        PlaybackService.exoPlayer?.skipSilenceEnabled = next
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _uiState.update { it.copy(sleepTimerRemainingSec = null) }
            return
        }
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                _uiState.update { it.copy(sleepTimerRemainingSec = remaining) }
                delay(1000L)
                remaining--
            }
            _uiState.update { it.copy(sleepTimerRemainingSec = null) }
            mediaController?.pause()
        }
    }

    fun skipNext() {
        val cur = _uiState.value
        if (cur.queue.isEmpty()) return
        if (cur.currentIndex >= cur.queue.lastIndex && !cur.shuffleEnabled) {
            val currentId = cur.currentTrack?.id
            if (currentId != null && radioProvider != null) {
                scope.launch {
                    val more = runCatching { radioProvider?.invoke(currentId) }.getOrNull()
                        ?.filter { t -> cur.queue.none { it.id == t.id } } ?: emptyList()
                    if (more.isNotEmpty()) {
                        val nextIdx = _uiState.value.queue.size
                        _uiState.update { it.copy(queue = it.queue + more) }
                        playAtIndex(nextIdx)
                        return@launch
                    }
                    val next = (cur.currentIndex + 1) % _uiState.value.queue.size
                    playAtIndex(next)
                }
                return
            }
        }
        val next = if (cur.shuffleEnabled && cur.queue.size > 1) {
            cur.queue.indices.filter { it != cur.currentIndex }.random()
        } else {
            (cur.currentIndex + 1) % cur.queue.size
        }
        playAtIndex(next)
    }

    fun skipPrevious() {
        val cur = _uiState.value
        if (cur.queue.isEmpty()) return
        if ((mediaController?.currentPosition ?: 0L) > 3000L) {
            seekTo(0L)
            return
        }
        val prev = if (cur.currentIndex - 1 < 0) cur.queue.lastIndex else cur.currentIndex - 1
        playAtIndex(prev)
    }

    fun stopPlayback() {
        consecutiveErrors = 0
        consecutiveResolutionFailures = 0
        playJob?.cancel()
        stopPositionTicker()
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _positionMs.value = 0L
        _uiState.update {
            it.copy(
                currentTrack = null,
                isPlaying = false,
                isBuffering = false,
                positionMs = 0L,
                durationMs = 0L
            )
        }
    }

    fun setVolume(volume: Float) {
        baseTrackVolume = volume.coerceIn(0f, 1f)
        mediaController?.volume = baseTrackVolume
    }

    fun getVolume(): Float = baseTrackVolume

    private fun startPositionTicker() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (true) {
                mediaController?.let { c ->
                    val pos = c.currentPosition.coerceAtLeast(0L)
                    val fadeMs = _uiState.value.crossfadeSec * 1000L
                    val dur = c.duration.coerceAtLeast(0L)
                    if (fadeMs > 0L && dur > fadeMs && pos >= dur - fadeMs) {
                        c.volume = (baseTrackVolume * ((dur - pos).toFloat() / fadeMs.toFloat())).coerceIn(0.05f, baseTrackVolume)
                    } else if (kotlin.math.abs(c.volume - baseTrackVolume) > 0.01f) {
                        c.volume = baseTrackVolume
                    }
                    val hit = skipSegments.firstOrNull { pos in it.startMs until it.endMs }
                    if (hit != null) c.seekTo(hit.endMs)
                    _positionMs.value = pos
                    if (_uiState.value.durationMs != dur && dur > 0L) {
                        _uiState.update { it.copy(durationMs = dur) }
                    }
                }
                delay(200L)
            }
        }
    }

    private fun stopPositionTicker() {
        positionJob?.cancel()
        positionJob = null
    }

    fun release() {
        PlaybackService.onSkipNext = null
        PlaybackService.onSkipPrevious = null
        stopPositionTicker()
        playJob?.cancel()
        sleepTimerJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
