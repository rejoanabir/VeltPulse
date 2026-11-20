package com.veltusstudio.veltpulse.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltusstudio.veltpulse.core.theme.VeltBackdrop
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.data.local.LibrarySnapshot
import com.veltusstudio.veltpulse.data.local.SavedTrack
import com.veltusstudio.veltpulse.data.model.Track
import com.veltusstudio.veltpulse.ui.components.AnimatedEqualizerIndicator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    library: LibrarySnapshot,
    downloaded: List<SavedTrack>,
    localTracks: List<Track>,
    currentTrackId: String?,
    onScanLocal: () -> Unit,
    onImportPlaylist: (String) -> Unit,
    onCreatePlaylist: (String, String, String) -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onShuffleList: (List<Track>) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onOpenProfile: (() -> Unit)? = null,
    onOpenYouTubeSync: (() -> Unit)? = null,
    downloadProgress: Map<String, Float> = emptyMap(),
    onDownloadPlaylist: (List<Track>) -> Unit = {},
    onCancelDownloadPlaylist: (List<Track>) -> Unit = {},
    onCancelTrackDownload: (String) -> Unit = {},
    onCancelAllDownloads: () -> Unit = {},
    onDeletePlaylist: (String) -> Unit = {},
    onRemoveFromPlaylist: (String, String) -> Unit = { _, _ -> },
    onToggleLike: (Track) -> Unit = {},
    onRemoveHistoryTrack: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sections = listOf("Playlists", "Liked", "Downloads", "History", "On Device")
    var selectedSection by remember { mutableStateOf("Playlists") }
    var openedPlaylistRef by remember { mutableStateOf<OpenedPlaylistRef?>(null) }
    var playlistToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistInput by remember { mutableStateOf("") }
    var newPlaylistDescription by remember { mutableStateOf("") }
    var newPlaylistArtworkUrl by remember { mutableStateOf("") }

    val playlistImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val dest = File(context.filesDir, "pl_art_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    newPlaylistArtworkUrl = dest.absolutePath
                }
            }
        }
    }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = Color(0xFF1B0F33),
            title = { Text("Delete Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${playlistToDelete?.second}'?", color = VeltColors.SilverSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistToDelete?.first?.let { onDeletePlaylist(it) }
                        playlistToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel", color = VeltColors.SilverMuted)
                }
            }
        )
    }

    openedPlaylistRef?.let { ref ->
        val liveTracks = when {
            ref.id == "__liked__" -> library.liked.map { it.toTrack() }
            else -> library.playlists.firstOrNull { it.id == ref.id }?.tracks?.map { it.toTrack() }
        }
        if (liveTracks == null && ref.isCustom) {
            openedPlaylistRef = null
            return
        }
        val tracks = liveTracks ?: emptyList()
        PlaylistDetailSheet(
            title = ref.title,
            tracks = tracks,
            currentTrackId = currentTrackId,
            onBack = { openedPlaylistRef = null },
            onPlayAll = { if (tracks.isNotEmpty()) onPlayList(tracks, 0) },
            onShufflePlay = { if (tracks.isNotEmpty()) onShuffleList(tracks) },
            onPlayTrack = { idx -> onPlayList(tracks, idx) },
            downloadingIds = downloadProgress.keys,
            downloadedIds = downloaded.map { it.id }.toSet(),
            onDownloadPlaylist = { onDownloadPlaylist(tracks) },
            onCancelDownloadPlaylist = { onCancelDownloadPlaylist(tracks) },
            onCancelTrackDownload = onCancelTrackDownload,
            onDeletePlaylist = if (ref.isCustom) {
                {
                    onDeletePlaylist(ref.id)
                    openedPlaylistRef = null
                }
            } else null,
            onRemoveTrack = { trackId ->
                if (ref.id == "__liked__") {
                    tracks.firstOrNull { it.id == trackId }?.let { onToggleLike(it) }
                } else if (ref.isCustom) {
                    onRemoveFromPlaylist(ref.id, trackId)
                }
            }
        )
        return
    }

    VeltBackdrop(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onOpenYouTubeSync != null) {
                        IconButton(onClick = onOpenYouTubeSync) {
                            Icon(Icons.Rounded.SmartDisplay, contentDescription = "YouTube Music Account", tint = Color(0xFFEF4444))
                        }
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Playlist", tint = VeltColors.SilverPrimary)
                    }
                    if (onOpenProfile != null) {
                        IconButton(onClick = onOpenProfile) {
                            Icon(Icons.Rounded.Person, contentDescription = "Profile & Settings", tint = VeltColors.SilverPrimary)
                        }
                    }
                }
            }

            if (downloadProgress.isNotEmpty()) {
                val activeDownloads = downloadProgress.toList()
                val avgPct = activeDownloads.map { it.second }.average().toFloat().coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF2E1752), Color(0xFF1B0D36))
                            )
                        )
                        .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = VeltColors.NeonViolet, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Downloading offline audio (${activeDownloads.size})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${(avgPct * 100).toInt()}%", color = VeltColors.NeonViolet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { avgPct },
                            color = VeltColors.NeonViolet,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onCancelAllDownloads,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Cancel All Downloads",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections) { sec ->
                    val selected = selectedSection == sec
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) VeltColors.NeonViolet else Color(0xFF1A122C))
                            .border(1.dp, if (selected) VeltColors.NeonViolet else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                            .clickable {
                                selectedSection = sec
                                if (sec == "On Device") onScanLocal()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(sec, color = if (selected) Color.White else VeltColors.SilverSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedSection) {
                "Playlists" -> {
                    val cards = buildList {
                        add(Triple("__create__", "Create\nPlaylist", ""))
                        add(Triple("__liked__", "Liked Songs", "${library.liked.size} songs"))
                        library.playlists.forEach { pl -> add(Triple(pl.id, pl.name, "${pl.tracks.size} songs")) }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 160.dp)
                    ) {
                        items(cards, key = { it.first }) { (id, title, sub) ->
                            val cover = when (id) {
                                "__liked__" -> library.liked.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty()
                                "__create__" -> ""
                                else -> {
                                    val pl = library.playlists.firstOrNull { it.id == id }
                                    if (pl?.customArtworkUrl?.isNotBlank() == true) pl.customArtworkUrl
                                    else pl?.tracks?.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty()
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFF130E22))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                                    .clickable {
                                        when (id) {
                                            "__create__" -> showCreateDialog = true
                                            "__liked__" -> openedPlaylistRef = OpenedPlaylistRef("__liked__", "Liked Songs", false)
                                            else -> library.playlists.firstOrNull { it.id == id }?.let { pl ->
                                                openedPlaylistRef = OpenedPlaylistRef(pl.id, pl.name, true)
                                            }
                                        }
                                    }
                            ) {
                                if (cover.isNotBlank()) {
                                    AsyncImage(model = cover, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.55f))
                                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, VeltColors.PureBlack.copy(alpha = 0.88f)))))
                                }
                                if (id == "__create__") {
                                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, tint = VeltColors.NeonViolet, modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(title, color = VeltColors.SilverSecondary, fontSize = 13.sp)
                                    }
                                } else {
                                    if (id == "__liked__") {
                                        Box(
                                            modifier = Modifier
                                                .padding(14.dp)
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(VeltColors.NeonViolet.copy(alpha = 0.40f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { playlistToDelete = id to title },
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete Playlist",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                                        Text(title, color = VeltColors.SilverPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(sub, color = VeltColors.SilverMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                "History" -> {
                    val list = library.history.map { it.toTrack() }
                    Column {
                        if (list.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${list.size} recently played", color = VeltColors.SilverMuted, fontSize = 12.sp)
                                TextButton(onClick = onClearHistory) {
                                    Text("Clear All", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        TrackListView(
                            tracks = list,
                            currentTrackId = currentTrackId,
                            emptyText = "No recently played tracks.",
                            showDelete = true,
                            onPlay = { onPlayList(list, it) },
                            onDelete = onRemoveHistoryTrack
                        )
                    }
                }
                "On Device" -> TrackListView(localTracks, currentTrackId, "No local audio files found.", false, { onPlayList(localTracks, it) }, {})
                else -> {
                    val list = when (selectedSection) {
                        "Liked" -> library.liked.map { it.toTrack() }
                        else -> downloaded.map { it.toTrack() }
                    }
                    TrackListView(list, currentTrackId, "No tracks in $selectedSection yet.", selectedSection == "Downloads", { onPlayList(list, it) }, onDeleteDownload)
                }
            }
        }
    }

    if (showCreateDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCreateDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF26124A),
                                Color(0xFF140A26),
                                Color(0xFF0C0618)
                            )
                        )
                    )
                    .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.40f), RoundedCornerShape(26.dp))
                    .padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(VeltColors.NeonViolet.copy(alpha = 0.20f))
                                .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.50f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = VeltColors.NeonViolet, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text("New Playlist", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Create or import your playlist", color = VeltColors.LavenderMuted, fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = newPlaylistInput,
                        onValueChange = { newPlaylistInput = it },
                        placeholder = { Text("Playlist name or YouTube URL", color = VeltColors.SilverMuted, fontSize = 13.sp) },
                        label = { Text("Name or URL", color = VeltColors.SilverSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = VeltColors.NeonViolet,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color(0xFF130E22),
                            unfocusedContainerColor = Color(0xFF130E22)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPlaylistDescription,
                        onValueChange = { newPlaylistDescription = it },
                        placeholder = { Text("Vibe or notes (optional)", color = VeltColors.SilverMuted, fontSize = 13.sp) },
                        label = { Text("Description (Optional)", color = VeltColors.SilverSecondary) },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = VeltColors.NeonViolet,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color(0xFF130E22),
                            unfocusedContainerColor = Color(0xFF130E22)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (newPlaylistArtworkUrl.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = newPlaylistArtworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Button(
                            onClick = { playlistImagePicker.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF261545)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = VeltColors.NeonViolet, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (newPlaylistArtworkUrl.isNotBlank()) "Change Cover Photo" else "Upload Cover from Phone",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }

                        if (newPlaylistArtworkUrl.isNotBlank()) {
                            IconButton(
                                onClick = { newPlaylistArtworkUrl = "" },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (onOpenYouTubeSync != null) {
                        TextButton(
                            onClick = {
                                showCreateDialog = false
                                onOpenYouTubeSync()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Rounded.SmartDisplay, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync from your YouTube Account", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                newPlaylistInput = ""
                                newPlaylistDescription = ""
                                newPlaylistArtworkUrl = ""
                                showCreateDialog = false
                            }
                        ) {
                            Text("Cancel", color = VeltColors.SilverMuted)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFF9333EA), Color(0xFF7C3AED))
                                    )
                                )
                                .clickable {
                                    val v = newPlaylistInput.trim()
                                    if (v.isNotBlank()) {
                                        if (v.contains("list=") || v.startsWith("http")) {
                                            onImportPlaylist(v)
                                        } else {
                                            onCreatePlaylist(v, newPlaylistDescription.trim(), newPlaylistArtworkUrl.trim())
                                        }
                                    }
                                    newPlaylistInput = ""
                                    newPlaylistDescription = ""
                                    newPlaylistArtworkUrl = ""
                                    showCreateDialog = false
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text("Create", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackListView(
    tracks: List<Track>,
    currentTrackId: String?,
    emptyText: String,
    showDelete: Boolean,
    onPlay: (Int) -> Unit,
    onDelete: (String) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = VeltColors.SilverMuted, fontSize = 14.sp)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(tracks.indices.toList(), key = { "${tracks[it].id}_$it" }) { idx ->
            val track = tracks[idx]
            val isActive = track.id == currentTrackId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isActive) VeltColors.SageGreenActive.copy(alpha = 0.55f) else Color(0xFF141814))
                    .then(
                        if (isActive) Modifier.border(1.dp, VeltColors.SageGreenPill, RoundedCornerShape(14.dp))
                        else Modifier.border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    )
                    .clickable { onPlay(idx) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(VeltColors.SurfaceElevated), contentAlignment = Alignment.Center) {
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
                if (isActive) {
                    AnimatedEqualizerIndicator()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (showDelete) {
                    IconButton(onClick = { onDelete(track.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = VeltColors.SilverMuted)
                    }
                }
            }
        }
    }
}

private data class OpenedPlaylistRef(val id: String, val title: String, val isCustom: Boolean)

