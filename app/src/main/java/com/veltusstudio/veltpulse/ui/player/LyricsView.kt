package com.veltusstudio.veltpulse.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.data.local.LyricsPreferences
import com.veltusstudio.veltpulse.data.model.LyricLine
import com.veltusstudio.veltpulse.data.remote.Romanizer
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    trackTitle: String = "VeltPulse",
    trackArtist: String = "",
    isLoading: Boolean,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 0L,
    isPlaying: Boolean = true,
    onDismiss: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { LyricsPreferences(context) }
    val listState = rememberLazyListState()

    val effectivePositionMs = (positionMs + prefs.offsetMs).coerceAtLeast(0L)
    val activeIndex = remember(lyrics, effectivePositionMs) {
        lyrics.indexOfLast { it.timeMs in 0..effectivePositionMs }
    }

    var romanize by remember { mutableStateOf(prefs.autoRomanize) }
    val hasNonLatin = remember(lyrics) { lyrics.any { Romanizer.hasNonLatin(it.text) } }
    var showOffsetControl by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF261244), // Velvet dark purple
                        Color(0xFF140A26),
                        Color(0xFF080410)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            var topDragY by remember { mutableFloatStateOf(0f) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { topDragY = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                topDragY += dragAmount.y
                            },
                            onDragEnd = {
                                if (topDragY > 60f) onDismiss()
                                topDragY = 0f
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left balanced anchor (92.dp)
                Box(
                    modifier = Modifier.width(92.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Center Title & LINE SYNC Capsule - Mathematically Centered
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "LYRICS",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (trackArtist.isNotBlank()) "$trackTitle • $trackArtist" else trackTitle,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "LINE SYNC",
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Right Actions: Timer & More - Exactly 92.dp
                Box(
                    modifier = Modifier.width(92.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { showOffsetControl = !showOffsetControl },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                Icons.Rounded.Timer,
                                contentDescription = "Sync",
                                tint = if (prefs.offsetMs != 0L) VeltColors.AmberProgress else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box {
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "More",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = Color(0xFF1B0F33)
                        ) {
                            if (hasNonLatin) {
                                DropdownMenuItem(
                                    text = { Text(if (romanize) "Show Original Script" else "Romanize (Latin Script)", color = Color.White) },
                                    onClick = {
                                        romanize = !romanize
                                        prefs.setRomanize(romanize)
                                        showMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (prefs.wordByWordEnabled) "Word Spotlight: ON" else "Word Spotlight: OFF", color = Color.White) },
                                onClick = {
                                    prefs.setWordByWord(!prefs.wordByWordEnabled)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

            // Sync Offset Bar (When triggered)
            if (showOffsetControl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("-500ms", color = Color.White, fontSize = 12.sp, modifier = Modifier.clickable { prefs.setOffset(prefs.offsetMs - 500L) })
                    Text("-100ms", color = Color.White, fontSize = 12.sp, modifier = Modifier.clickable { prefs.setOffset(prefs.offsetMs - 100L) })
                    Text("Sync: ${prefs.offsetMs}ms", color = VeltColors.AmberProgress, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { prefs.setOffset(0L) })
                    Text("+100ms", color = Color.White, fontSize = 12.sp, modifier = Modifier.clickable { prefs.setOffset(prefs.offsetMs + 100L) })
                    Text("+500ms", color = Color.White, fontSize = 12.sp, modifier = Modifier.clickable { prefs.setOffset(prefs.offsetMs + 500L) })
                }
            }

            // --- MAIN LYRICS SCROLL AREA ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VeltColors.AmberProgress, strokeWidth = 3.dp)
                        }
                    }
                    lyrics.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No synced lyrics found", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Check title and artist spelling", color = Color.White.copy(alpha = 0.50f), fontSize = 12.sp)
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 24.dp, bottom = 64.dp, start = 24.dp, end = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(lyrics) { index, line ->
                                val isCurrent = index == activeIndex
                                val displayText = if (romanize) Romanizer.romanize(line.text) else line.text

                                // Active Line Lighting: Pure Brilliant White + Luminous Text Glow Halo (frame_010.jpg)
                                Text(
                                    text = displayText,
                                    style = if (isCurrent) {
                                        TextStyle(
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 34.sp,
                                            shadow = Shadow(
                                                color = Color(0xFFC084FC).copy(alpha = 0.95f),
                                                blurRadius = 26f,
                                                offset = Offset.Zero
                                            )
                                        )
                                    } else {
                                        TextStyle(
                                            color = VeltColors.SilverSecondary.copy(alpha = 0.35f),
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 28.sp
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { if (line.timeMs >= 0L) onSeekTo(line.timeMs) },
                                            onLongClick = {
                                                LyricShareHelper.shareLyricCard(context, line.text, trackTitle, trackArtist)
                                            }
                                        )
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }

                        // Top & Bottom Atmospheric Vignettes
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .align(Alignment.TopCenter)
                                .background(Brush.verticalGradient(listOf(Color(0xFF261244), Color.Transparent)))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .align(Alignment.BottomCenter)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF080410))))
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}
