package com.veltusstudio.veltpulse.ui.genres

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.veltpulse.core.theme.VeltBackdrop
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.data.model.Track
import com.veltusstudio.veltpulse.data.remote.GenreBreakdown
import com.veltusstudio.veltpulse.data.remote.LastFmApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    lfmApi: LastFmApi,
    onPlayTrack: (Track) -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onOpenSettings: () -> Unit,
    onGenerateWithGenre: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPeriod by remember { mutableStateOf("overall") }
    var genres by remember { mutableStateOf<List<GenreBreakdown>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedGenreForDetail by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val periods = listOf(
        "7day" to "Past 7 Days",
        "1month" to "This Month",
        "12month" to "Last 12 Months",
        "overall" to "Overall"
    )

    fun loadGenres(period: String = selectedPeriod) {
        if (!lfmApi.isConnected) return
        scope.launch {
            isLoading = true
            errorMessage = null
            runCatching { lfmApi.deriveGenreAnalytics(period) }
                .onSuccess {
                    genres = it
                    if (it.isEmpty()) errorMessage = "No genre scrobbles found for this period."
                }
                .onFailure {
                    errorMessage = "Failed to load genres: ${it.localizedMessage}"
                }
            isLoading = false
        }
    }

    LaunchedEffect(selectedPeriod, lfmApi.username, lfmApi.apiKey) {
        if (lfmApi.isConnected) {
            loadGenres(selectedPeriod)
        }
    }

    VeltBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Genre Analytics",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (lfmApi.username.isNotBlank()) "Based on ${lfmApi.username}'s scrobbles" else "Connect Last.fm in Settings",
                        color = VeltColors.SilverMuted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { loadGenres() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = VeltColors.SilverPrimary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(periods) { (code, label) ->
                    val isSelected = selectedPeriod == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) VeltColors.SilverPrimary else VeltColors.SurfaceDark)
                            .border(1.dp, if (isSelected) VeltColors.SilverPrimary else VeltColors.Hairline, RoundedCornerShape(50))
                            .clickable { selectedPeriod = code }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) VeltColors.PureBlack else VeltColors.SilverSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when {
                !lfmApi.isConnected -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(Icons.Rounded.BarChart, contentDescription = null, tint = VeltColors.SilverMuted, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Last.fm Not Connected", style = MaterialTheme.typography.headlineMedium, color = VeltColors.SilverPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Connect your Last.fm account with your API key to view your full genre breakdown and listening analytics.", color = VeltColors.SilverMuted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = onOpenSettings,
                                colors = ButtonDefaults.buttonColors(containerColor = VeltColors.SilverPrimary, contentColor = VeltColors.PureBlack)
                            ) {
                                Text("Configure Last.fm")
                            }
                        }
                    }
                }
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = VeltColors.SilverPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Analyzing your listening tags…", color = VeltColors.SilverMuted, fontSize = 13.sp)
                        }
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage.orEmpty(), color = VeltColors.SilverMuted, fontSize = 14.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(genres, key = { idx, g -> "${g.name}_$idx" }) { index, genre ->
                            GenreRowItem(
                                rank = index + 1,
                                genre = genre,
                                onClick = { selectedGenreForDetail = genre.name }
                            )
                        }
                    }
                }
            }
        }

        selectedGenreForDetail?.let { genreName ->
            GenreDetailModalSheet(
                genreName = genreName,
                lfmApi = lfmApi,
                onDismiss = { selectedGenreForDetail = null },
                onPlayQueue = onPlayQueue,
                onGenerateWithGenre = {
                    selectedGenreForDetail = null
                    onGenerateWithGenre(it)
                }
            )
        }
    }
}

@Composable
private fun GenreRowItem(
    rank: Int,
    genre: GenreBreakdown,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF130E22))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#$rank",
                        color = VeltColors.SilverMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp)
                    )
                    Text(
                        text = genre.name,
                        color = VeltColors.SilverPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "${genre.percentage}%",
                    color = VeltColors.SilverSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(VeltColors.SurfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (genre.percentage / 100f).coerceIn(0.04f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    VeltColors.SilverPrimary.copy(alpha = 0.75f),
                                    VeltColors.SilverPrimary
                                )
                            )
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreDetailModalSheet(
    genreName: String,
    lfmApi: LastFmApi,
    onDismiss: () -> Unit,
    onPlayQueue: (List<Track>, Int) -> Unit,
    onGenerateWithGenre: (String) -> Unit
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(genreName) {
        isLoading = true
        tracks = runCatching {
            lfmApi.getTagTopTracks(genreName, limit = 25).map { it.toTrack() }
        }.getOrDefault(emptyList())
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VeltColors.Obsidian,
        contentColor = VeltColors.SilverPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(genreName, style = MaterialTheme.typography.headlineMedium, color = VeltColors.SilverPrimary)
                    Text("Top tracks in $genreName", color = VeltColors.SilverMuted, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onGenerateWithGenre(genreName) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF261642))
                            .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "Generate", tint = VeltColors.SilverPrimary, modifier = Modifier.size(18.dp))
                    }
                    if (tracks.isNotEmpty()) {
                        Button(
                            onClick = { onPlayQueue(tracks, 0); onDismiss() },
                            colors = ButtonDefaults.buttonColors(containerColor = VeltColors.NeonViolet, contentColor = Color.White)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VeltColors.NeonViolet)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(tracks, key = { idx, t -> "gt_${t.id}_$idx" }) { idx, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF130E22))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .clickable { onPlayQueue(tracks, idx); onDismiss() }
                                .padding(12.dp),
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
}
