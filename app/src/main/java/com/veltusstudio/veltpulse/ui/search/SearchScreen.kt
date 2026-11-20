package com.veltusstudio.veltpulse.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.data.model.Track
import com.veltusstudio.veltpulse.data.remote.MusicSearchApi
import com.veltusstudio.veltpulse.ui.components.AnimatedEqualizerIndicator
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    api: MusicSearchApi,
    discoverTracks: List<Track> = emptyList(),
    currentTrackId: String?,
    onPlayQueue: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    if (onDismiss != null) {
        androidx.activity.compose.BackHandler(onBack = onDismiss)
    }
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var fallbackDiscover by remember { mutableStateOf<List<Track>>(emptyList()) }

    // Search History persistence
    val prefs = remember { context.getSharedPreferences("veltpulse_search_prefs", Context.MODE_PRIVATE) }
    var searchHistory by remember {
        val saved = prefs.getString("search_history", "") ?: ""
        mutableStateOf(if (saved.isBlank()) emptyList<String>() else saved.split("|||").filter { it.isNotBlank() })
    }

    fun saveHistory(term: String) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return
        val updated = (listOf(trimmed) + searchHistory.filterNot { it.equals(trimmed, ignoreCase = true) }).take(10)
        searchHistory = updated
        prefs.edit().putString("search_history", updated.joinToString("|||")).apply()
    }

    fun removeHistoryItem(term: String) {
        val updated = searchHistory.filterNot { it == term }
        searchHistory = updated
        prefs.edit().putString("search_history", updated.joinToString("|||")).apply()
    }

    fun clearAllHistory() {
        searchHistory = emptyList()
        prefs.edit().remove("search_history").apply()
    }

    // Discover fallback when discoverTracks is empty
    LaunchedEffect(discoverTracks) {
        if (discoverTracks.isEmpty()) {
            runCatching { api.searchTracks("Trending Global Hits") }
                .onSuccess { fallbackDiscover = it }
        }
    }

    val genres = listOf("Chill", "Workout", "Focus", "Party", "Sad", "Indie", "Lo-fi", "Rock")
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Real-time search with debounce
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            isSearching = false
            errorMsg = null
            return@LaunchedEffect
        }
        delay(400)
        isSearching = true
        errorMsg = null
        runCatching { api.searchTracks(q) }
            .onSuccess {
                results = it
                saveHistory(q)
            }
            .onFailure {
                errorMsg = "Search failed: ${it.localizedMessage}"
            }
        isSearching = false
    }

    fun runSearch(target: String = query) {
        val q = target.trim()
        if (q.isEmpty()) return
        query = q
        keyboard?.hide()
        saveHistory(q)
    }

    Box(modifier = modifier.fillMaxSize().background(VeltColors.DeepBg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(VeltColors.SurfaceElevated)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Find songs, artists, albums", color = VeltColors.SilverMuted, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search songs, artists, albums...", color = VeltColors.SilverMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = VeltColors.SilverSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; results = emptyList() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = VeltColors.SilverMuted)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF130E22),
                    unfocusedContainerColor = Color(0xFF130E22),
                    focusedBorderColor = VeltColors.NeonViolet,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Recent Searches Chips (Mockup 16474.png & user request)
            if (query.isBlank() && searchHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Searches",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Clear all",
                        color = VeltColors.LavenderMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { clearAllHistory() }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    searchHistory.forEach { item ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF1E1438))
                                .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.25f), RoundedCornerShape(50))
                                .clickable { query = item }
                                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item, color = Color.White, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove",
                                tint = VeltColors.SilverMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { removeHistoryItem(item) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEach { genre ->
                    val selected = query.equals(genre, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) VeltColors.NeonViolet else Color(0xFF1C1332))
                            .border(1.dp, if (selected) VeltColors.NeonViolet else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                            .clickable { runSearch(genre) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(genre, color = if (selected) Color.White else VeltColors.SilverSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = VeltColors.SilverPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (results.isEmpty()) "Discover" else "Results", color = VeltColors.SilverPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))

            val effectiveDiscover = if (discoverTracks.isNotEmpty()) discoverTracks else fallbackDiscover
            val listToShow = if (results.isEmpty() && query.isBlank()) effectiveDiscover else results

            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VeltColors.SilverPrimary)
                }
            } else if (errorMsg != null) {
                Text(errorMsg.orEmpty(), color = VeltColors.SilverMuted, fontSize = 14.sp)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 160.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(listToShow, key = { idx, track -> "${track.id}_$idx" }) { idx, track ->
                        val isActive = track.id == currentTrackId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isActive) Color(0xFF2E1752) else Color(0xFF130E22))
                                .border(1.dp, if (isActive) VeltColors.NeonViolet.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                                .clickable {
                                    onPlayQueue(listToShow, idx)
                                }
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
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = VeltColors.SilverMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
