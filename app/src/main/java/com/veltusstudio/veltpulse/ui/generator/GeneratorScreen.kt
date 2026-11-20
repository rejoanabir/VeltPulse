package com.veltusstudio.veltpulse.ui.generator

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.veltpulse.core.theme.VeltBackdrop
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.core.theme.liquidGlass
import com.veltusstudio.veltpulse.data.generator.GeneratedPlaylist
import com.veltusstudio.veltpulse.data.generator.LastWaveEngine
import com.veltusstudio.veltpulse.data.generator.LastWaveMode
import com.veltusstudio.veltpulse.data.generator.PlaylistExportHelper
import com.veltusstudio.veltpulse.data.model.Track
import com.veltusstudio.veltpulse.data.remote.LastFmApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    lfmApi: LastFmApi,
    engine: LastWaveEngine,
    existingPlaylistTitles: Set<String>,
    onPlayPlaylist: (List<Track>, Int) -> Unit,
    onSavePlaylist: (String, List<Track>) -> Unit,
    onOpenSettings: () -> Unit,
    initialTag: String = "",
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(if (initialTag.isNotBlank()) LastWaveMode.BY_TAG else LastWaveMode.MY_MIX) }
    var trackCount by remember { mutableFloatStateOf(25f) }
    var selectedPeriod by remember { mutableStateOf("overall") }
    var seedTrack by remember { mutableStateOf("") }
    var seedArtist by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf(initialTag.ifBlank { "Indie" }) }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedResult by remember { mutableStateOf<GeneratedPlaylist?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val quickTags = listOf("Indie", "Rock", "Lo-Fi", "Synthwave", "Electronic", "Hip-Hop", "Jazz", "Metal", "Ambient", "Classical")

    fun triggerGeneration() {
        if (!lfmApi.isConnected) {
            Toast.makeText(context, "Please configure your Last.fm API key in Settings first", Toast.LENGTH_SHORT).show()
            onOpenSettings()
            return
        }
        scope.launch {
            isGenerating = true
            val playlist = runCatching {
                engine.generatePlaylist(
                    mode = selectedMode,
                    limit = trackCount.toInt(),
                    period = selectedPeriod,
                    seedTrack = seedTrack.trim(),
                    seedArtist = seedArtist.trim(),
                    tag = tagInput.trim(),
                    existingTitles = existingPlaylistTitles
                )
            }.getOrNull()
            isGenerating = false
            if (playlist != null && playlist.tracks.isNotEmpty()) {
                generatedResult = playlist
            } else {
                Toast.makeText(context, "Could not generate playlist. Try different seeds or tags.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    VeltBackdrop(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Playlist Generator", style = MaterialTheme.typography.displaySmall, color = VeltColors.SilverPrimary, fontSize = 28.sp)
                        Text("8 smart modes powered by your Last.fm data", color = VeltColors.SilverMuted, fontSize = 12.sp)
                    }
                    Box(modifier = Modifier.size(38.dp).liquidGlass(19.dp, 0.10f, 0.20f), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = VeltColors.SilverPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            item {
                Text("Select Mode", color = VeltColors.SilverPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LastWaveMode.values().forEach { mode ->
                        val isSelected = selectedMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlass(16.dp, if (isSelected) 0.14f else 0.05f, if (isSelected) 0.30f else 0.12f)
                                .clickable { selectedMode = mode }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) VeltColors.SilverPrimary else VeltColors.SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        LastWaveMode.TOP_TRACKS -> Icons.Rounded.TrendingUp
                                        LastWaveMode.RECENT_TRACKS -> Icons.Rounded.History
                                        LastWaveMode.SIMILAR_TRACKS -> Icons.Rounded.GraphicEq
                                        LastWaveMode.SIMILAR_ARTISTS -> Icons.Rounded.People
                                        LastWaveMode.BY_TAG -> Icons.Rounded.Tag
                                        LastWaveMode.MY_MIX -> Icons.Rounded.Shuffle
                                        LastWaveMode.RECOMMENDATIONS -> Icons.Rounded.AutoAwesome
                                        LastWaveMode.MY_LIBRARY -> Icons.Rounded.LibraryMusic
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) VeltColors.PureBlack else VeltColors.SilverPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.title, color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(mode.subtitle, color = VeltColors.SilverMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedMode = mode },
                                colors = RadioButtonDefaults.colors(selectedColor = VeltColors.SilverPrimary, unselectedColor = VeltColors.SilverMuted)
                            )
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Track Count", color = VeltColors.SilverPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${trackCount.toInt()} tracks", color = VeltColors.SilverSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = trackCount,
                    onValueChange = { trackCount = it },
                    valueRange = 5f..50f,
                    steps = 8,
                    colors = SliderDefaults.colors(thumbColor = VeltColors.SilverPrimary, activeTrackColor = VeltColors.SilverPrimary, inactiveTrackColor = VeltColors.Hairline),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (selectedMode) {
                LastWaveMode.SIMILAR_TRACKS -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Seed Track & Artist", color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = seedTrack,
                                onValueChange = { seedTrack = it },
                                placeholder = { Text("Track name (e.g. Starboy)", color = VeltColors.SilverMuted, fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = seedArtist,
                                onValueChange = { seedArtist = it },
                                placeholder = { Text("Artist name (e.g. The Weeknd)", color = VeltColors.SilverMuted, fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                LastWaveMode.SIMILAR_ARTISTS -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Seed Artist", color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = seedArtist,
                                onValueChange = { seedArtist = it },
                                placeholder = { Text("Artist name (e.g. Daft Punk)", color = VeltColors.SilverMuted, fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                LastWaveMode.BY_TAG -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Genre or Tag", color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = tagInput,
                                onValueChange = { tagInput = it },
                                placeholder = { Text("Enter genre or tag", color = VeltColors.SilverMuted, fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(quickTags) { tag ->
                                    val isSelected = tagInput.equals(tag, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isSelected) VeltColors.SilverPrimary else VeltColors.SurfaceDark)
                                            .clickable { tagInput = tag }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(tag, color = if (isSelected) VeltColors.PureBlack else VeltColors.SilverSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                LastWaveMode.TOP_TRACKS -> {
                    item {
                        Text("Time Period", color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val options = listOf("7day" to "7 Days", "1month" to "1 Month", "12month" to "1 Year", "overall" to "All-Time")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.forEach { (code, label) ->
                                val sel = selectedPeriod == code
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (sel) VeltColors.SilverPrimary else VeltColors.SurfaceDark)
                                        .clickable { selectedPeriod = code }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(label, color = if (sel) VeltColors.PureBlack else VeltColors.SilverSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }

            item {
                Button(
                    onClick = { triggerGeneration() },
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = VeltColors.SilverPrimary, contentColor = VeltColors.PureBlack),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = VeltColors.PureBlack, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Composing Playlist…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate ${selectedMode.title}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        generatedResult?.let { pl ->
            GeneratedResultModalSheet(
                playlist = pl,
                onDismiss = { generatedResult = null },
                onPlayAll = {
                    onPlayPlaylist(pl.tracks, 0)
                    generatedResult = null
                },
                onPlayTrack = { idx ->
                    onPlayPlaylist(pl.tracks, idx)
                    generatedResult = null
                },
                onSave = {
                    onSavePlaylist(pl.title, pl.tracks)
                    Toast.makeText(context, "Saved to library as \"${pl.title}\"", Toast.LENGTH_SHORT).show()
                },
                onExportM3u = { PlaylistExportHelper.exportToM3u(context, pl.title, pl.tracks) },
                onExportCsv = { PlaylistExportHelper.exportToCsv(context, pl.title, pl.tracks) },
                onRegenerate = { triggerGeneration() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratedResultModalSheet(
    playlist: GeneratedPlaylist,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onSave: () -> Unit,
    onExportM3u: () -> Unit,
    onExportCsv: () -> Unit,
    onRegenerate: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VeltColors.Obsidian,
        contentColor = VeltColors.SilverPrimary
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.title, style = MaterialTheme.typography.displaySmall, color = VeltColors.SilverPrimary, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(playlist.subtitle, color = VeltColors.SilverMuted, fontSize = 12.sp)
                }
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = VeltColors.SilverPrimary, contentColor = VeltColors.PureBlack)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play All")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = VeltColors.SurfaceElevated, contentColor = VeltColors.SilverPrimary)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontSize = 12.sp)
                }
                FilledTonalButton(
                    onClick = onExportM3u,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = VeltColors.SurfaceElevated, contentColor = VeltColors.SilverPrimary)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("M3U", fontSize = 12.sp)
                }
                FilledTonalButton(
                    onClick = onExportCsv,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = VeltColors.SurfaceElevated, contentColor = VeltColors.SilverPrimary)
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CSV", fontSize = 12.sp)
                }
                IconButton(onClick = onRegenerate, modifier = Modifier.liquidGlass(20.dp, 0.10f, 0.20f)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate", tint = VeltColors.SilverPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(playlist.tracks, key = { idx, t -> "res_${t.id}_$idx" }) { idx, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(14.dp, 0.05f, 0.12f)
                            .clickable { onPlayTrack(idx) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${idx + 1}", color = VeltColors.SilverMuted, fontSize = 12.sp, modifier = Modifier.width(26.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = VeltColors.SilverMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = VeltColors.SilverSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
