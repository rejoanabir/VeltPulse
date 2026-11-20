package com.veltusstudio.veltpulse

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.core.theme.VeltPulseTheme
import com.veltusstudio.veltpulse.core.theme.liquidGlass
import com.veltusstudio.veltpulse.data.generator.LastWaveEngine
import com.veltusstudio.veltpulse.data.local.LibraryRepository
import com.veltusstudio.veltpulse.data.local.LocalAudioScanner
import com.veltusstudio.veltpulse.data.local.OfflineCacheManager
import com.veltusstudio.veltpulse.data.model.LyricLine
import com.veltusstudio.veltpulse.data.model.Track
import com.veltusstudio.veltpulse.data.remote.LastFmApi
import com.veltusstudio.veltpulse.data.remote.LrcLibApi
import com.veltusstudio.veltpulse.data.remote.MusicSearchApi
import com.veltusstudio.veltpulse.data.remote.SponsorBlockApi
import com.veltusstudio.veltpulse.player.AudioDspManager
import com.veltusstudio.veltpulse.player.PlaybackService
import com.veltusstudio.veltpulse.player.PlayerController
import com.veltusstudio.veltpulse.ui.components.MiniPlayer
import com.veltusstudio.veltpulse.ui.components.SplashScreen
import com.veltusstudio.veltpulse.ui.components.YouTubeLoginSheet
import com.veltusstudio.veltpulse.ui.generator.GeneratorScreen
import com.veltusstudio.veltpulse.ui.genres.GenresScreen
import com.veltusstudio.veltpulse.ui.home.HomeScreen
import com.veltusstudio.veltpulse.ui.library.LibraryScreen
import com.veltusstudio.veltpulse.ui.player.EqBottomSheet
import com.veltusstudio.veltpulse.ui.player.NowPlayingSheet
import com.veltusstudio.veltpulse.ui.player.UpNextSheet
import com.veltusstudio.veltpulse.ui.profile.ProfileScreen
import com.veltusstudio.veltpulse.ui.search.ArtistDetailSheet
import com.veltusstudio.veltpulse.ui.search.SearchScreen
import com.veltusstudio.veltpulse.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var playerController: PlayerController
    private lateinit var dspManager: AudioDspManager

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        dspManager = AudioDspManager(this)
        val app = application as VeltPulseApp
        val searchApi = MusicSearchApi(app.container.okHttpClient)
        val lrcLibApi = LrcLibApi(app.container.okHttpClient)
        val libraryRepo = LibraryRepository(this)
        val offlineCache = OfflineCacheManager(this, app.container.okHttpClient)
        val sponsorBlockApi = SponsorBlockApi(app.container.okHttpClient)
        val lastFmApi = LastFmApi(this, app.container.okHttpClient)
        val generatorEngine = LastWaveEngine(lastFmApi)

        playerController = PlayerController(this) { idOrQuery ->
            if (idOrQuery.startsWith("lfm:")) {
                val query = idOrQuery.removePrefix("lfm:")
                searchApi.resolveTrackByQuery(query)
            } else {
                searchApi.resolveAudio(idOrQuery)
            }
        }.apply {
            localUriProvider = { id -> offlineCache.getLocalUri(id) }
            sponsorBlockProvider = { id -> if (id.startsWith("local_") || id.startsWith("lfm:")) emptyList() else sponsorBlockApi.fetchSkipSegments(id) }
            onTrackStarted = { track ->
                libraryRepo.recordPlay(track)
                lifecycleScope.launch {
                    runCatching {
                        lastFmApi.scrobble(track.artist, track.title)
                    }
                }
            }
            radioProvider = { id -> if (id.startsWith("local_") || id.startsWith("lfm:")) emptyList() else searchApi.getRadioUpNext(id) }
            connect()
        }

        lifecycleScope.launch {
            PlaybackService.audioSessionId.collect { sessionId ->
                if (sessionId != 0) dspManager.attachToSession(sessionId)
            }
        }

        setContent {
            VeltPulseTheme {
                VeltPulseRoot(
                    playerController = playerController,
                    searchApi = searchApi,
                    lrcLibApi = lrcLibApi,
                    dspManager = dspManager,
                    libraryRepo = libraryRepo,
                    offlineCache = offlineCache,
                    lastFmApi = lastFmApi,
                    generatorEngine = generatorEngine
                )
            }
        }
    }

    override fun onDestroy() {
        dspManager.release()
        playerController.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VeltPulseRoot(
    playerController: PlayerController,
    searchApi: MusicSearchApi,
    lrcLibApi: LrcLibApi,
    dspManager: AudioDspManager,
    libraryRepo: LibraryRepository,
    offlineCache: OfflineCacheManager,
    lastFmApi: LastFmApi,
    generatorEngine: LastWaveEngine
) {
    val playbackState by playerController.uiState.collectAsState()
    val eqState by dspManager.state.collectAsState()
    val libraryState by libraryRepo.state.collectAsState()
    val downloadedTracks by offlineCache.downloaded.collectAsState()
    val downloadingIds by offlineCache.downloadingIds.collectAsState()
    val downloadProgressMap by offlineCache.downloadProgress.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("veltpulse_profile", Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(prefs.getString("name", "Listener").orEmpty().ifBlank { "Listener" }) }

    var showSplash by remember { mutableStateOf(false) }
    var localTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { localTracks = LocalAudioScanner.scanDeviceAudio(context) }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showSearchScreen by remember { mutableStateOf(false) }
    var showYouTubeSheetFromLibrary by remember { mutableStateOf(false) }

    var showNowPlaying by remember { mutableStateOf(false) }
    var showEqSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }

    var homeTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isHomeLoading by remember { mutableStateOf(true) }

    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var isLoadingLyrics by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            homeTracks = runCatching { searchApi.getHomeQuickPicks() }.getOrDefault(emptyList())
            isHomeLoading = false
        }
    }

    LaunchedEffect(playbackState.currentTrack?.id) {
        val track = playbackState.currentTrack
        if (track == null) {
            lyrics = emptyList()
            isLoadingLyrics = false
            return@LaunchedEffect
        }
        isLoadingLyrics = true
        val localLrc = offlineCache.readSavedLyrics(track.id)
        lyrics = if (localLrc.isNotEmpty()) localLrc else runCatching { lrcLibApi.fetchLyrics(track.title, track.artist) }.getOrDefault(emptyList())
        isLoadingLyrics = false
    }

    BackHandler(enabled = showNowPlaying) {
        showNowPlaying = false
    }
    BackHandler(enabled = !showNowPlaying && showSettingsScreen) {
        showSettingsScreen = false
    }
    BackHandler(enabled = !showNowPlaying && !showSettingsScreen && showSearchScreen) {
        showSearchScreen = false
    }
    BackHandler(enabled = !showNowPlaying && !showSettingsScreen && !showSearchScreen && selectedTab != 0) {
        selectedTab = 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. FULLSCREEN CONTENT (Smoothly scrolls behind the floating bottom capsule) ---
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(280, easing = FastOutLinearInEasing)) { -it / 3 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(320, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(280, easing = FastOutLinearInEasing)) { it / 3 } + fadeOut(tween(200)))
                    }
                },
                label = "tab_animated_content"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(
                        userName = userName,
                        tracks = homeTracks,
                        isLoading = isHomeLoading,
                        currentTrackId = playbackState.currentTrack?.id,
                        lfmApi = lastFmApi,
                        onOpenSearch = { selectedTab = 1 },
                        onOpenProfile = { showSettingsScreen = true },
                        onOpenGenres = { selectedTab = 2 },
                        onOpenLikedSongs = { selectedTab = 3 },
                        onTrackClick = { clicked ->
                            val idx = homeTracks.indexOfFirst { it.id == clicked.id }.coerceAtLeast(0)
                            playerController.playQueue(homeTracks, idx)
                        }
                    )
                    1 -> SearchScreen(
                        api = searchApi,
                        discoverTracks = homeTracks,
                        currentTrackId = playbackState.currentTrack?.id,
                        onPlayQueue = { list, idx -> playerController.playQueue(list, idx) },
                        onDismiss = { selectedTab = 0 }
                    )
                    2 -> GenresScreen(
                        lfmApi = lastFmApi,
                        onPlayTrack = { track ->
                            playerController.playQueue(listOf(track), 0)
                        },
                        onPlayQueue = { list, idx ->
                            playerController.playQueue(list, idx)
                        },
                        onOpenSettings = {
                            showSettingsScreen = true
                        },
                        onGenerateWithGenre = { _ ->
                            selectedTab = 0
                        }
                    )
                    3 -> LibraryScreen(
                        library = libraryState,
                        downloaded = downloadedTracks,
                        localTracks = localTracks,
                        currentTrackId = playbackState.currentTrack?.id,
                        downloadProgress = downloadProgressMap,
                        onScanLocal = {
                            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                scope.launch { localTracks = LocalAudioScanner.scanDeviceAudio(context) }
                            } else { audioPermLauncher.launch(perm) }
                        },
                        onImportPlaylist = { url ->
                            scope.launch {
                                runCatching { searchApi.importYouTubePlaylist(url) }
                                    .onSuccess { (name, list) -> libraryRepo.saveImportedPlaylist(name, list) }
                            }
                        },
                        onCreatePlaylist = { name, desc, art -> libraryRepo.createPlaylist(name, desc, art) },
                        onPlayList = { list, idx -> playerController.playQueue(list, idx) },
                        onShuffleList = { list -> if (list.isNotEmpty()) playerController.playQueue(list.shuffled(), 0) },
                        onDeleteDownload = { offlineCache.deleteTrack(it) },
                        onDownloadPlaylist = { tracks ->
                            offlineCache.startBackgroundPlaylistDownload(
                                tracks = tracks,
                                resolveUrl = { id -> searchApi.resolveStreamUrl(id) },
                                resolveLyrics = { t -> runCatching { lrcLibApi.fetchLyrics(t.title, t.artist) }.getOrDefault(emptyList()) }
                            )
                        },
                        onCancelDownloadPlaylist = { tracks ->
                            offlineCache.cancelPlaylistDownload(tracks)
                        },
                        onCancelTrackDownload = { id ->
                            offlineCache.cancelDownload(id)
                        },
                        onCancelAllDownloads = {
                            offlineCache.cancelAllDownloads()
                        },
                        onDeletePlaylist = { plId -> libraryRepo.deletePlaylist(plId) },
                        onRemoveFromPlaylist = { plId, trackId -> libraryRepo.removeFromPlaylist(plId, trackId) },
                        onToggleLike = { track -> libraryRepo.toggleLike(track) },
                        onRemoveHistoryTrack = { trackId ->
                            if (playbackState.currentTrack?.id == trackId) {
                                playerController.stopPlayback()
                            }
                            libraryRepo.removeFromHistory(trackId)
                        },
                        onClearHistory = {
                            libraryRepo.clearHistory()
                        },
                        onOpenProfile = {
                            showSettingsScreen = true
                        },
                        onOpenYouTubeSync = {
                            showYouTubeSheetFromLibrary = true
                        }
                    )
                }
            }
        }

            // --- 2. FLOATING NAVIGATION BAR & DISMISSIBLE MINIPLAYER (Overlaid at bottom) ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Smooth gradient vignette so lists scroll cleanly underneath
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x55080412),
                                    Color(0xC0080412),
                                    Color(0xF5080412)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = playbackState.currentTrack != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        MiniPlayer(
                            state = playbackState,
                            positionMsFlow = playerController.positionMs,
                            onTogglePlayPause = { playerController.togglePlayPause() },
                            onSkipNext = { playerController.skipNext() },
                            onSkipPrevious = { playerController.skipPrevious() },
                            onClose = { playerController.stopPlayback() },
                            onOpenQueue = { showQueueSheet = true },
                            onClick = { showNowPlaying = true },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(36.dp),
                        color = Color(0xF0120A24),
                        border = BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    VeltColors.NeonViolet.copy(alpha = 0.45f),
                                    Color.White.copy(alpha = 0.12f)
                                )
                            )
                        ),
                        shadowElevation = 16.dp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .animateContentSize(
                                    animationSpec = spring<IntSize>(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val navItems = listOf(
                                Triple("Home", Icons.Rounded.Home, 0),
                                Triple("Search", Icons.Rounded.Search, 1),
                                Triple("Radio", Icons.Rounded.BarChart, 2),
                                Triple("Library", Icons.Rounded.LibraryMusic, 3)
                            )
                            navItems.forEach { (label, icon, index) ->
                                val isSelected = selectedTab == index
                                if (isSelected) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(26.dp))
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    listOf(Color(0xFF4C1D95), Color(0xFF2E1065))
                                                )
                                            )
                                            .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.65f), RoundedCornerShape(26.dp))
                                            .clickable { selectedTab = index }
                                            .padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = label,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            label,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { selectedTab = index }
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = label,
                                            tint = VeltColors.LavenderMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        AnimatedVisibility(
            visible = showNowPlaying && playbackState.currentTrack != null,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it } + fadeIn(animationSpec = tween(240)),
            exit = slideOutVertically(
                animationSpec = tween(280, easing = FastOutLinearInEasing)
            ) { it } + fadeOut(animationSpec = tween(200))
        ) {
            NowPlayingSheet(
                state = playbackState,
                positionMsFlow = playerController.positionMs,
                lyrics = lyrics,
                isLoadingLyrics = isLoadingLyrics,
                downloadProgress = downloadProgressMap,
                onDismiss = { showNowPlaying = false },
                onTogglePlayPause = { playerController.togglePlayPause() },
                onSeekTo = { playerController.seekTo(it) },
                onSkipNext = { playerController.skipNext() },
                onSkipPrevious = { playerController.skipPrevious() },
                onToggleShuffle = { playerController.toggleShuffle() },
                onToggleRepeat = { playerController.toggleRepeatOne() },
                onOpenEq = { showEqSheet = true },
                onOpenQueue = { showQueueSheet = true },
                onSetInAppVolume = { playerController.setVolume(it) },
                onSetSleepTimer = { playerController.setSleepTimer(it) },
                onCycleSpeed = {
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)
                    val next = speeds[(speeds.indexOf(playbackState.speed) + 1) % speeds.size]
                    playerController.setSpeed(next)
                },
                onToggleSkipSilence = { playerController.toggleSkipSilence() },
                isLiked = playbackState.currentTrack?.let { t -> libraryState.liked.any { it.id == t.id } } == true,
                isDownloaded = playbackState.currentTrack?.let { offlineCache.isDownloaded(it.id) } == true,
                isDownloading = playbackState.currentTrack?.id in downloadingIds,
                onToggleLike = { playbackState.currentTrack?.let { libraryRepo.toggleLike(it) } },
                onDownload = {
                    val t = playbackState.currentTrack ?: return@NowPlayingSheet
                    scope.launch(Dispatchers.IO) {
                        val url = t.streamUrl.ifBlank { searchApi.resolveStreamUrl(t.id).orEmpty() }
                        offlineCache.startBackgroundDownload(t, url, lyrics)
                    }
                },
                onCancelDownload = {
                    playbackState.currentTrack?.let { offlineCache.cancelDownload(it.id) }
                },
                onCycleCrossfade = { playerController.cycleCrossfade() },
                onArtistClick = { selectedArtist = it },
                playlists = libraryState.playlists,
                onAddToPlaylist = { plId -> playbackState.currentTrack?.let { libraryRepo.togglePlaylist(plId, it) } },
                onCreatePlaylist = { name -> libraryRepo.createPlaylist(name) }
            )
        }

        if (showEqSheet) {
            EqBottomSheet(
                state = eqState,
                bandLabels = dspManager.bandLabels,
                presets = dspManager.presets,
                onDismiss = { showEqSheet = false },
                onToggleEnabled = { dspManager.setEnabled(it) },
                onSelectPreset = { dspManager.applyPreset(it) },
                onBandChange = { idx, gain -> dspManager.setBandGain(idx, gain) }
            )
        }

        selectedArtist?.let { artist ->
            ArtistDetailSheet(
                artistName = artist,
                api = searchApi,
                onDismiss = { selectedArtist = null },
                onPlayQueue = { list, idx -> playerController.playQueue(list, idx) }
            )
        }

        if (showQueueSheet) {
            UpNextSheet(
                queue = playbackState.queue,
                currentIndex = playbackState.currentIndex,
                onDismiss = { showQueueSheet = false },
                onSelectIndex = { playerController.playAtIndex(it) },
                onRemoveIndex = { playerController.removeFromQueue(it) },
                onSaveQueue = {
                    libraryRepo.saveImportedPlaylist("Saved Queue (${playbackState.queue.size})", playbackState.queue)
                }
            )
        }

        AnimatedVisibility(
            visible = showSearchScreen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            SearchScreen(
                api = searchApi,
                discoverTracks = homeTracks,
                currentTrackId = playbackState.currentTrack?.id,
                onPlayQueue = { list, idx -> playerController.playQueue(list, idx) },
                onDismiss = { showSearchScreen = false }
            )
        }

        AnimatedVisibility(
            visible = showSettingsScreen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val app = context.applicationContext as VeltPulseApp
            SettingsScreen(
                onDismiss = { showSettingsScreen = false },
                lfmApi = lastFmApi,
                onOpenEq = { showEqSheet = true },
                downloadedCount = downloadedTracks.size,
                youTubeAccountManager = app.container.youTubeAccountManager,
                okHttpClient = app.container.okHttpClient,
                onImportYouTubePlaylist = { id ->
                    scope.launch(Dispatchers.IO) {
                        runCatching { searchApi.importYouTubePlaylist(id) }
                            .onSuccess { (name, list) -> libraryRepo.saveImportedPlaylist(name, list) }
                    }
                }
            )
        }

        if (showYouTubeSheetFromLibrary) {
            val app = context.applicationContext as VeltPulseApp
            YouTubeLoginSheet(
                accountManager = app.container.youTubeAccountManager,
                okHttpClient = app.container.okHttpClient,
                onDismiss = { showYouTubeSheetFromLibrary = false },
                onImportPlaylist = { id ->
                    scope.launch(Dispatchers.IO) {
                        runCatching { searchApi.importYouTubePlaylist(id) }
                            .onSuccess { (name, list) -> libraryRepo.saveImportedPlaylist(name, list) }
                    }
                }
            )
        }

        AnimatedVisibility(visible = showSplash, exit = fadeOut()) {
            SplashScreen()
        }
    }
}
