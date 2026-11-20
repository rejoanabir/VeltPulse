package com.veltusstudio.veltpulse.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.core.theme.glossyGlassDeck
import com.veltusstudio.veltpulse.core.theme.glossyPill
import com.veltusstudio.veltpulse.core.theme.liquidGlass
import com.veltusstudio.veltpulse.data.model.LyricLine
import com.veltusstudio.veltpulse.data.model.PlaybackUiState
import com.veltusstudio.veltpulse.ui.components.AddToPlaylistDialog
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NowPlayingSheet(
    state: PlaybackUiState,
    lyrics: List<LyricLine>,
    isLoadingLyrics: Boolean,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEq: () -> Unit,
    onCycleSpeed: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onToggleSkipSilence: () -> Unit,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onToggleLike: () -> Unit = {},
    onDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onCycleCrossfade: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    playlists: List<com.veltusstudio.veltpulse.data.local.LocalPlaylist> = emptyList(),
    onAddToPlaylist: (String) -> Unit = {},
    onCreatePlaylist: ((String) -> Unit)? = null,
    positionMsFlow: StateFlow<Long>? = null,
    onSetInAppVolume: (Float) -> Unit = {},
    downloadProgress: Map<String, Float> = emptyMap()
) {
    BackHandler(onBack = onDismiss)
    val track = state.currentTrack ?: return

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager }
    var inAppVolume by remember { mutableFloatStateOf(1.0f) }
    var masterVolume by remember {
        mutableFloatStateOf(
            (audioManager?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 10).toFloat()
        )
    }
    val maxMasterVolume = remember {
        (audioManager?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 15).toFloat()
    }
    var showVolumeSheet by remember { mutableStateOf(false) }

    var selectedQuality by remember {
        mutableStateOf(AudioQualityStorage.getSelectedQuality(context))
    }
    var showQualitySheet by remember { mutableStateOf(false) }

    val livePositionMs by (positionMsFlow?.collectAsState() ?: remember(state.positionMs) { mutableStateOf(state.positionMs) })
    val duration = state.durationMs.coerceAtLeast(1L)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val currentFraction = (livePositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val displayFraction = if (isDragging) dragFraction else currentFraction

    var showLyrics by remember { mutableStateOf(false) }
    var showToolsMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = showLyrics) {
        showLyrics = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VeltColors.PureBlack)
    ) {
        // --- 1. FULL SCREEN CINEMATIC BLEND ARTWORK BACKGROUND ---
        if (track.artworkUrl.isNotBlank()) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (showLyrics) 0.22f else 0.40f)
            )

            // Deep cinematic glass gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (showLyrics) {
                                listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.70f),
                                    Color.Black.copy(alpha = 0.95f)
                                )
                            } else {
                                listOf(
                                    Color.Black.copy(alpha = 0.40f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f),
                                    Color(0xFF030712).copy(alpha = 0.98f)
                                )
                            }
                        )
                    )
            )

            // Glossy ambient light specular reflection
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent,
                                Color(0x1538BDF8)
                            )
                        )
                    )
            )
        }

        // --- 2. FOREGROUND CONTENT & CONTROLS ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP BAR: Collapse | "NOW PLAYING" Header (Perfect Center) | Queue & More
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left balanced anchor (86.dp)
                Box(
                    modifier = Modifier.width(86.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Center Title - Perfectly centered
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "NOW PLAYING",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        if (track.album.isNotBlank()) track.album else "From Queue",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right balanced anchor (86.dp)
                Box(
                    modifier = Modifier.width(86.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                .clickable(onClick = onOpenQueue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Queue",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                    .clickable { showToolsMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "More",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showToolsMenu,
                                onDismissRequest = { showToolsMenu = false },
                                containerColor = Color(0xFF1B0F33),
                                modifier = Modifier
                                    .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.40f), RoundedCornerShape(18.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Audio Quality: ${selectedQuality.title}", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = VeltColors.NeonViolet) },
                                    onClick = { showQualitySheet = true; showToolsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Playback Speed: ${state.speed}x", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null, tint = VeltColors.NeonViolet) },
                                    onClick = { onCycleSpeed() }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (state.crossfadeSec == 0) "Crossfade: Off" else "Crossfade: ${state.crossfadeSec}s", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, tint = VeltColors.NeonViolet) },
                                    onClick = { onCycleCrossfade() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Skip Silence: ${if (state.skipSilence) "On" else "Off"}", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.VolumeOff, contentDescription = null, tint = VeltColors.NeonViolet) },
                                    onClick = { onToggleSkipSilence() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sleep Timer: 30 min", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null, tint = VeltColors.NeonViolet) },
                                    onClick = { onSetSleepTimer(30); showToolsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sleep Timer: Off", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.TimerOff, contentDescription = null, tint = Color.Gray) },
                                    onClick = { onSetSleepTimer(null); showToolsMenu = false }
                                )
                                val currentPct = downloadProgress[track.id] ?: 0f
                                if (isDownloading) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Cancel Download (${(currentPct * 100).toInt()}%)", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { currentPct },
                                                    color = Color(0xFFEF4444),
                                                    trackColor = Color.White.copy(alpha = 0.15f),
                                                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Close, contentDescription = "Cancel Download", tint = Color(0xFFEF4444))
                                        },
                                        onClick = { onCancelDownload(); showToolsMenu = false }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isDownloaded) "Downloaded Offline" else "Download Offline",
                                                color = if (isDownloaded) Color(0xFF22C55E) else Color.White
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                                contentDescription = null,
                                                tint = if (isDownloaded) Color(0xFF22C55E) else VeltColors.NeonViolet
                                            )
                                        },
                                        enabled = !isDownloaded,
                                        onClick = { onDownload(); showToolsMenu = false }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist...", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, tint = VeltColors.NeonViolet) },
                                    onClick = { showAddToPlaylistDialog = true; showToolsMenu = false }
                                )
                                playlists.forEach { pl ->
                                    val inThisPlaylist = pl.tracks.any { it.id == track.id }
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (inThisPlaylist) "In: ${pl.name}" else "Add to: ${pl.name}",
                                                    color = if (inThisPlaylist) Color(0xFF22C55E) else Color.White
                                                )
                                                if (inThisPlaylist) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Added ✓", color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (inThisPlaylist) Icons.Rounded.Check else Icons.Rounded.PlaylistAdd,
                                                contentDescription = null,
                                                tint = if (inThisPlaylist) Color(0xFF22C55E) else VeltColors.NeonViolet
                                            )
                                        },
                                        onClick = { onAddToPlaylist(pl.id); showToolsMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isDownloading) {
                val pct = downloadProgress[track.id] ?: 0f
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B0F33))
                        .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = VeltColors.NeonViolet, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Downloading ${(pct * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(12.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        color = VeltColors.NeonViolet,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Cancel Download",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            var coverDragX by remember { mutableFloatStateOf(0f) }
            var coverDragY by remember { mutableFloatStateOf(0f) }

            // Dynamic Apple Music-style scale breathing when playing vs paused
            val artworkScale by animateFloatAsState(
                targetValue = if (state.isPlaying) 1.0f else 0.88f,
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessLow
                ),
                label = "coverScale"
            )

            // Dynamic ambient backlight pulse behind artwork
            val pulseTransition = rememberInfiniteTransition(label = "artworkBacklight")
            val glowAlpha by pulseTransition.animateFloat(
                initialValue = 0.22f,
                targetValue = 0.52f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glowAlpha"
            )

            // MAIN AREA: Central Glossy Artwork with Swipe Gestures
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Ambient pulsating backlight glow
                if (state.isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                scaleX = artworkScale * 1.06f
                                scaleY = artworkScale * 1.06f
                                alpha = glowAlpha
                            }
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        VeltColors.NeonViolet.copy(alpha = 0.45f),
                                        Color(0xFF7C3AED).copy(alpha = 0.22f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = artworkScale
                            scaleY = artworkScale
                        }
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF141914))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    coverDragX = 0f
                                    coverDragY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    coverDragX += dragAmount.x
                                    coverDragY += dragAmount.y
                                },
                                onDragEnd = {
                                    if (coverDragY > 70f) {
                                        onDismiss()
                                    } else if (coverDragX < -70f) {
                                        onSkipNext()
                                    } else if (coverDragX > 70f) {
                                        onSkipPrevious()
                                    }
                                    coverDragX = 0f
                                    coverDragY = 0f
                                }
                            )
                        }
                        .clickable { showLyrics = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (track.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.40f),
                            modifier = Modifier.size(68.dp)
                        )
                    }

                    // Specular sheen across cover
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.Transparent,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- 3. METADATA ROW: Track Title & Artist (Left) | Like & Lyrics (Right) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        track.artist,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onArtistClick(track.artist) }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Like Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .clickable(onClick = onToggleLike),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color(0xFFF43F5E) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Quotes / Lyrics Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (showLyrics) VeltColors.SageGreenActive else Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                if (showLyrics) VeltColors.SageGreenPill else Color.White.copy(alpha = 0.16f),
                                CircleShape
                            )
                            .clickable { showLyrics = !showLyrics },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.FormatQuote,
                            contentDescription = "Lyrics",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 4. GLOWING SINE-WAVE PROGRESS BAR ---
            GlowingSineWaveProgress(
                fraction = displayFraction,
                isPlaying = state.isPlaying,
                onSeekFraction = { f ->
                    dragFraction = f
                    isDragging = true
                },
                onSeekConfirmed = { f ->
                    isDragging = false
                    onSeekTo((f * duration).toLong())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )

            // Timestamps: Elapsed on Left | Remaining Negative on Right
            val displayMs = if (isDragging) (dragFraction * duration).toLong() else livePositionMs
            val remainingMs = (duration - displayMs).coerceAtLeast(0L)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTime(displayMs),
                    color = VeltColors.SilverMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "-${formatTime(remainingMs)}",
                    color = VeltColors.SilverMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- 5. AUDIO FORMAT PILL (Tap opens Audio Quality Picker) ---
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .clickable { showQualitySheet = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${selectedQuality.title} • ${selectedQuality.sampleRate}",
                    color = VeltColors.SilverSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = VeltColors.SilverMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 6. TRANSPORT ROW: Previous | VELTPULSE SIGNATURE PLAY/PAUSE | Next ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Sculpted Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF281545), Color(0xFF160A2B))
                            )
                        )
                        .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                        .clickable(onClick = onSkipPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // VeltPulse Signature Play / Pause Pill Button
                Row(
                    modifier = Modifier
                        .height(58.dp)
                        .widthIn(min = 160.dp, max = 200.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFFA855F7), Color(0xFF7C3AED), Color(0xFFD946EF))
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable(onClick = onTogglePlayPause)
                        .animateContentSize(
                            animationSpec = spring<IntSize>(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        AnimatedContent(
                            targetState = state.isPlaying,
                            transitionSpec = {
                                (scaleIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)))
                                    .togetherWith(scaleOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeOut(tween(110)))
                            },
                            label = "nowPlayingPlayPause"
                        ) { isPlaying ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPlaying) "Pause" else "Play",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Next Sculpted Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF281545), Color(0xFF160A2B))
                            )
                        )
                        .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                        .clickable(onClick = onSkipNext),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // --- 7. BOTTOM 4-ITEM DOCK: Shuffle | Volume | Equalizer | Repeat ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Capsule
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (state.shuffleEnabled) VeltColors.DeepPurplePill else Color.White.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            if (state.shuffleEnabled) VeltColors.NeonViolet else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(50)
                        )
                        .clickable(onClick = onToggleShuffle)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    if (state.shuffleEnabled) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(VeltColors.NeonViolet)
                        )
                    }
                }

                // Dedicated Sound / Volume Button (Opens Volume Settings)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF22133D))
                        .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .clickable { showVolumeSheet = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.VolumeUp,
                        contentDescription = "Volume Settings",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Volume", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Dedicated Equalizer Button (Directly opens Equalizer)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF22133D))
                        .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .clickable(onClick = onOpenEq)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = "Equalizer",
                        tint = VeltColors.NeonViolet,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EQ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Repeat One Capsule
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (state.repeatOne) VeltColors.DeepPurplePill else Color.White.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            if (state.repeatOne) VeltColors.NeonViolet else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(50)
                        )
                        .clickable(onClick = onToggleRepeat)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.RepeatOne,
                        contentDescription = "Repeat",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    if (state.repeatOne) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(VeltColors.NeonViolet)
                        )
                    }
                }
            }
        }

        // Volume Settings Bottom Sheet (In-App + Master Device Volume)
        if (showVolumeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showVolumeSheet = false },
                containerColor = Color(0xFF150A26),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Volume Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showVolumeSheet = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = VeltColors.SilverMuted)
                        }
                    }

                    // 1. In-App Player Volume Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = VeltColors.NeonViolet, modifier = Modifier.size(18.dp))
                                Text("In-App Music Volume", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text("${(inAppVolume * 100).toInt()}%", color = VeltColors.NeonViolet, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = inAppVolume,
                            onValueChange = {
                                inAppVolume = it
                                onSetInAppVolume(it)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = VeltColors.NeonViolet,
                                activeTrackColor = VeltColors.NeonViolet,
                                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                            )
                        )
                    }

                    // 2. Master Device Volume Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = VeltColors.MagentaGlow, modifier = Modifier.size(18.dp))
                                Text("Master Device Volume", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text("${masterVolume.toInt()} / ${maxMasterVolume.toInt()}", color = VeltColors.MagentaGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = masterVolume,
                            valueRange = 0f..maxMasterVolume,
                            onValueChange = {
                                masterVolume = it
                                audioManager?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, it.toInt(), 0)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = VeltColors.MagentaGlow,
                                activeTrackColor = VeltColors.MagentaGlow,
                                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                            )
                        )
                    }

                    // Quick Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    inAppVolume = 0f
                                    onSetInAppVolume(0f)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Mute Player", color = VeltColors.SilverSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    inAppVolume = 1.0f
                                    onSetInAppVolume(1.0f)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("100% In-App", color = VeltColors.SilverSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // --- FULL-SCREEN LYRICS VIEW OVERLAY ---
        AnimatedVisibility(
            visible = showLyrics,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(250)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically { it / 2 }
        ) {
            LyricsView(
                lyrics = lyrics,
                modifier = Modifier.fillMaxSize(),
                trackTitle = track.title,
                trackArtist = track.artist,
                isLoading = isLoadingLyrics,
                positionMs = livePositionMs,
                durationMs = duration,
                isPlaying = state.isPlaying,
                onSeekTo = onSeekTo,
                onDismiss = { showLyrics = false },
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious
            )
        }
    }

    // Audio Quality Bottom Sheet
    if (showQualitySheet) {
        AudioQualitySheet(
            onDismiss = { showQualitySheet = false },
            currentOption = selectedQuality,
            onSelectOption = { opt ->
                selectedQuality = opt
                AudioQualityStorage.setSelectedQuality(context, opt)
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = false },
            onTogglePlaylist = onAddToPlaylist,
            onCreatePlaylist = onCreatePlaylist
        )
    }
}

/**
 * High-performance, luminous Glowing Sine Wave Progress Bar.
 * The played portion renders an animated smooth sine wave with neon glow and a bright glowing thumb bead.
 */
@Composable
private fun GlowingSineWaveProgress(
    fraction: Float,
    isPlaying: Boolean,
    onSeekFraction: (Float) -> Unit,
    onSeekConfirmed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sine_wave_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) (2f * Math.PI.toFloat()) else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeekConfirmed(f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekFraction(f)
                    },
                    onDragEnd = {
                        onSeekConfirmed(fraction)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeekFraction(f)
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val playedWidth = (fraction * w).coerceIn(0f, w)
        val amplitude = 3.5.dp.toPx()
        val waveFreq = 0.035f

        // 1. Draw Inactive Background Track Line
        if (playedWidth < w) {
            drawLine(
                color = Color.White.copy(alpha = 0.16f),
                start = Offset(playedWidth, centerY),
                end = Offset(w, centerY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // 2. Draw Active Sine Wave Path
        if (playedWidth > 0f) {
            val wavePath = Path().apply {
                moveTo(0f, centerY)
                var x = 0f
                val step = 4f
                while (x <= playedWidth) {
                    val y = centerY + sin(x * waveFreq + phase) * amplitude
                    lineTo(x, y)
                    x += step
                }
                lineTo(playedWidth, centerY + sin(playedWidth * waveFreq + phase) * amplitude)
            }

            // Neon Violet Outer Glow Halo (16474.png)
            drawPath(
                path = wavePath,
                color = Color(0x80A855F7),
                style = Stroke(width = 5.dp.toPx())
            )

            // Crisp Violet Core Line
            drawPath(
                path = wavePath,
                color = Color(0xFFA855F7),
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Glowing Magenta Thumb Bead (16474.png)
            val thumbY = centerY + sin(playedWidth * waveFreq + phase) * amplitude

            // Radiant Halo
            drawCircle(
                color = Color(0x90D946EF),
                radius = 9.dp.toPx(),
                center = Offset(playedWidth, thumbY)
            )

            // Inner Solid Bead
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(playedWidth, thumbY)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}
