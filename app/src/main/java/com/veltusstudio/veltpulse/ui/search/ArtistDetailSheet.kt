package com.veltusstudio.veltpulse.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.core.theme.liquidGlass
import com.veltusstudio.veltpulse.data.model.Track
import com.veltusstudio.veltpulse.data.remote.MusicSearchApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailSheet(
    artistName: String,
    api: MusicSearchApi,
    onDismiss: () -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artistName) {
        isLoading = true
        tracks = runCatching { api.searchTracks(artistName) }.getOrDefault(emptyList())
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VeltColors.Obsidian,
        contentColor = VeltColors.SilverPrimary
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(artistName, style = MaterialTheme.typography.headlineMedium, color = VeltColors.SilverPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (tracks.isNotEmpty()) {
                    Button(
                        onClick = { onPlayQueue(tracks, 0); onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = VeltColors.SilverPrimary, contentColor = VeltColors.PureBlack)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play All")
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VeltColors.PulseCyanHint)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(tracks) { idx, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(14.dp, 0.06f, 0.12f)
                                .clickable { onPlayQueue(tracks, idx); onDismiss() }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${idx + 1}", color = VeltColors.SilverMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = VeltColors.SilverMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
