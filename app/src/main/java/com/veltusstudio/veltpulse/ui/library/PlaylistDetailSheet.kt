package com.veltusstudio.veltpulse.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltusstudio.veltpulse.core.theme.VeltBackdrop
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.core.theme.liquidGlass
import com.veltusstudio.veltpulse.data.model.Track

@Composable
fun PlaylistDetailSheet(
    title: String,
    tracks: List<Track>,
    currentTrackId: String?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    downloadingIds: Set<String> = emptySet(),
    downloadedIds: Set<String> = emptySet(),
    onDownloadPlaylist: () -> Unit = {},
    onCancelDownloadPlaylist: () -> Unit = {},
    onCancelTrackDownload: (String) -> Unit = {},
    onDeletePlaylist: (() -> Unit)? = null,
    onRemoveTrack: ((String) -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    val allDownloaded = tracks.isNotEmpty() && tracks.all { it.id in downloadedIds }
    val isDownloadingPlaylist = tracks.any { it.id in downloadingIds }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog && onDeletePlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF1B0F33),
            title = { Text("Delete Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '$title'?", color = VeltColors.SilverSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePlaylist()
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = VeltColors.SilverMuted)
                }
            }
        )
    }

    VeltBackdrop {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = VeltColors.SilverPrimary)
                    }
                    Text("PLAYLIST", color = VeltColors.SilverMuted, fontSize = 11.sp, letterSpacing = 1.6.sp)
                    if (onDeletePlaylist != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Playlist", tint = Color(0xFFEF4444))
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val cover = tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty()
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .liquidGlass(24.dp, 0.08f, 0.24f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(VeltColors.SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cover.isNotBlank()) {
                            AsyncImage(model = cover, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = VeltColors.SilverMuted, modifier = Modifier.size(56.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = VeltColors.SilverPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${tracks.size} songs", color = VeltColors.SilverMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onShufflePlay, modifier = Modifier.size(44.dp).liquidGlass(22.dp, 0.08f, 0.18f)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = VeltColors.SilverPrimary, modifier = Modifier.size(20.dp))
                        }
                        Box(
                            modifier = Modifier.size(58.dp).clip(CircleShape).background(VeltColors.SilverPrimary).clickable(enabled = tracks.isNotEmpty(), onClick = onPlayAll),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play All", tint = VeltColors.PureBlack, modifier = Modifier.size(30.dp))
                        }
                        // Download Playlist Button
                        IconButton(
                            onClick = {
                                if (isDownloadingPlaylist) onCancelDownloadPlaylist()
                                else if (!allDownloaded) onDownloadPlaylist()
                            },
                            modifier = Modifier.size(44.dp).liquidGlass(22.dp, 0.08f, 0.18f)
                        ) {
                            if (isDownloadingPlaylist) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = VeltColors.NeonViolet
                                )
                            } else {
                                Icon(
                                    imageVector = if (allDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                    contentDescription = if (allDownloaded) "Downloaded Offline" else "Download Playlist",
                                    tint = if (allDownloaded) Color(0xFF22C55E) else VeltColors.SilverPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            itemsIndexed(tracks) { idx, track ->
                val isActive = track.id == currentTrackId
                val isTrackDownloading = track.id in downloadingIds
                val isTrackDownloaded = track.id in downloadedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(16.dp, if (isActive) 0.14f else 0.05f, if (isActive) 0.28f else 0.12f)
                        .clickable { onPlayTrack(idx) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(VeltColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.artworkUrl.isNotBlank()) {
                            AsyncImage(model = track.artworkUrl, contentDescription = track.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = VeltColors.SilverSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = VeltColors.SilverMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isTrackDownloading) {
                        IconButton(
                            onClick = { onCancelTrackDownload(track.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Cancel Download",
                                tint = VeltColors.NeonViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (isTrackDownloaded) {
                        Icon(
                            Icons.Rounded.DownloadDone,
                            contentDescription = "Downloaded",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (isActive) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = VeltColors.SilverPrimary, modifier = Modifier.size(18.dp))
                    }
                    if (onRemoveTrack != null) {
                        IconButton(
                            onClick = { onRemoveTrack(track.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Remove from playlist",
                                tint = VeltColors.SilverMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
