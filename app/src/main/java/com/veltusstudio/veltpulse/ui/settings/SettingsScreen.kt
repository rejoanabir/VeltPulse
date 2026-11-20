package com.veltusstudio.veltpulse.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.ui.components.VeltPulseLogo
import com.veltusstudio.veltpulse.data.remote.LastFmApi
import com.veltusstudio.veltpulse.ui.player.AudioQualityOption
import com.veltusstudio.veltpulse.ui.player.AudioQualitySheet
import com.veltusstudio.veltpulse.ui.player.AudioQualityStorage
import com.veltusstudio.veltpulse.data.remote.YouTubeAccountManager
import com.veltusstudio.veltpulse.ui.components.YouTubeLoginSheet
import okhttp3.OkHttpClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    lfmApi: LastFmApi,
    onOpenEq: () -> Unit,
    downloadedCount: Int = 16,
    youTubeAccountManager: YouTubeAccountManager? = null,
    okHttpClient: OkHttpClient? = null,
    onImportYouTubePlaylist: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profilePrefs = remember { context.getSharedPreferences("veltpulse_profile", Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(profilePrefs.getString("name", "Listener").orEmpty().ifBlank { "Listener" }) }
    var userPhotoUrl by remember { mutableStateOf(profilePrefs.getString("photo_url", "").orEmpty()) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showYouTubeDialog by remember { mutableStateOf(false) }

    var tempPhotoPath by remember { mutableStateOf("") }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val dest = File(context.filesDir, "user_avatar.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempPhotoPath = dest.absolutePath
                }
            }
        }
    }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var showLfmDialog by remember { mutableStateOf(false) }
    var lfmUsernameInput by remember { mutableStateOf(lfmApi.username) }
    var lfmApiKeyInput by remember { mutableStateOf(lfmApi.apiKey) }
    var lfmApiSecretInput by remember { mutableStateOf(lfmApi.apiSecret) }

    var showQualityPicker by remember { mutableStateOf(false) }
    var currentQuality by remember { mutableStateOf(AudioQualityStorage.getSelectedQuality(context)) }

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Appearance State toggles with SharedPreferences persistence
    val appearancePrefs = remember { context.getSharedPreferences("veltpulse_appearance", Context.MODE_PRIVATE) }
    var selectedAccent by remember { mutableStateOf(appearancePrefs.getString("accent", "Violet") ?: "Violet") }
    var dynamicNowPlaying by remember { mutableStateOf(appearancePrefs.getBoolean("dynamic_now_playing", true)) }
    var wordByWordLyrics by remember { mutableStateOf(appearancePrefs.getBoolean("word_by_word_lyrics", true)) }
    var wavySeekbar by remember { mutableStateOf(appearancePrefs.getBoolean("wavy_seekbar", true)) }
    var rotatingBackground by remember { mutableStateOf(appearancePrefs.getBoolean("rotating_background", true)) }
    var hapticFeedback by remember { mutableStateOf(appearancePrefs.getBoolean("haptic_feedback", true)) }

    // Audio State toggles with SharedPreferences persistence
    val audioPrefs = remember { context.getSharedPreferences("veltpulse_audio_prefs", Context.MODE_PRIVATE) }
    var internalDirectOutput by remember { mutableStateOf(audioPrefs.getBoolean("internal_direct_output", false)) }
    var softwareVolume by remember { mutableStateOf(audioPrefs.getBoolean("software_volume", false)) }
    var loudnessNormalization by remember { mutableStateOf(audioPrefs.getBoolean("loudness_normalization", false)) }
    var systemAudioEffects by remember { mutableStateOf(audioPrefs.getBoolean("system_audio_effects", false)) }
    var studioMasterClarity by remember { mutableStateOf(audioPrefs.getBoolean("studio_master_clarity", false)) }
    var showAutoEqInVolumePanel by remember { mutableStateOf(audioPrefs.getBoolean("show_auto_eq", true)) }

    val q = searchQuery.trim().lowercase()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VeltColors.DeepBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // --- TOP HEADER: Back | Settings | Search Toggle ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(VeltColors.SurfaceElevated)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = VeltColors.SilverPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Settings",
                                color = VeltColors.SilverPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isSearchActive) VeltColors.NeonViolet.copy(alpha = 0.35f) else VeltColors.SurfaceElevated)
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search Settings",
                                tint = VeltColors.SilverPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search settings (e.g. Audio, Equalizer, Theme)...", color = VeltColors.SilverMuted, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = VeltColors.NeonViolet) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = VeltColors.SilverMuted)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VeltColors.NeonViolet,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color(0xFF130E22),
                                unfocusedContainerColor = Color(0xFF130E22),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // --- USER PROFILE CARD (Replacing VeltPulse Bar) ---
            if (q.isEmpty() || "profile".contains(q) || userName.lowercase().contains(q)) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF2E1752),
                                        Color(0xFF1E0E38),
                                        Color(0xFF120824)
                                    )
                                )
                            )
                            .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.40f), RoundedCornerShape(26.dp))
                            .clickable { showEditProfileDialog = true }
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(
                                            brush = Brush.linearGradient(
                                                listOf(Color(0xFF7C3AED), Color(0xFFD946EF))
                                            )
                                        )
                                        .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (userPhotoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = userPhotoUrl,
                                            contentDescription = "User Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = userName.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            userName,
                                            color = Color.White,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(VeltColors.NeonViolet.copy(alpha = 0.25f))
                                                .border(0.8.dp, VeltColors.NeonViolet.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "Hi-Res Pro",
                                                color = VeltColors.NeonViolet,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        "Tap to edit profile & photo",
                                        color = VeltColors.LavenderMuted,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "$downloadedCount offline tracks",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 11.sp
                                        )
                                        Text("•", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)
                                        Text(
                                            "32-bit DSP",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Edit Profile",
                                tint = VeltColors.SilverSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // --- SECTION 1: Connected Services ---
            val matchesServices = q.isEmpty() || "last.fm".contains(q) || "scrobble".contains(q) || "sync".contains(q) || "youtube".contains(q) || "spotify".contains(q)
            if (matchesServices) {
                item {
                    Text(
                        "Connected Services",
                        color = VeltColors.SilverMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF130E22))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
                    ) {
                        if (q.isEmpty() || "last.fm".contains(q) || "scrobble".contains(q)) {
                            SettingsRowItem(
                                icon = Icons.Rounded.Schedule,
                                iconBg = Color(0xFF4C1D95),
                                title = "Last.fm Scrobbler",
                                subtitle = if (lfmApi.isConnected) "Connected as ${lfmApi.username} • Scrobbling active" else "Connect your Last.fm account",
                                trailingDot = lfmApi.isConnected,
                                onClick = { showLfmDialog = true }
                            )
                        }
                        if (q.isEmpty() || "sync".contains(q) || "youtube".contains(q)) {
                            HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                            val ytAccount = youTubeAccountManager?.account?.collectAsState()?.value
                            val isYtConnected = ytAccount?.isLoggedIn == true
                            val ytName = ytAccount?.name?.ifBlank { "YouTube User" } ?: "YouTube User"
                            SettingsRowItem(
                                icon = Icons.Rounded.SmartDisplay,
                                iconBg = if (isYtConnected) Color(0xFF8B0000) else Color(0xFF2E1752),
                                title = "YouTube Music Account",
                                subtitle = if (isYtConnected) "Connected as $ytName • Sync & Library active" else "Log in to sync your YouTube Music library & playlists",
                                trailingDot = isYtConnected,
                                onClick = { showYouTubeDialog = true }
                            )
                        }
                        if (q.isEmpty() || "spotify".contains(q)) {
                            HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                            SettingsRowItem(
                                icon = Icons.Rounded.PlayCircle,
                                iconBg = Color(0xFF143820),
                                title = "Spotify Import",
                                subtitle = "Import your playlists directly into VeltPulse",
                                onClick = { }
                            )
                        }
                    }
                }
            }

            // --- SECTION 2: Playback & UI ---
            val matchesPlayback = q.isEmpty() || "appearance".contains(q) || "visuals".contains(q) || "theme".contains(q) || "accent".contains(q) || "audio".contains(q) || "equalizer".contains(q) || "dsp".contains(q) || "quality".contains(q) || "library".contains(q) || "download".contains(q)
            if (matchesPlayback) {
                item {
                    Text(
                        "Playback & UI",
                        color = VeltColors.SilverMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF130E22))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
                    ) {
                        if (q.isEmpty() || "appearance".contains(q) || "visuals".contains(q) || "theme".contains(q) || "accent".contains(q)) {
                            SettingsRowItem(
                                icon = Icons.Rounded.Palette,
                                iconBg = Color(0xFF3B1E48),
                                title = "Appearance & Visuals",
                                subtitle = "Accent ($selectedAccent), Dynamic colors, Wavy seekbar, Lyrics glow",
                                trailingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    listOf(Color(0xFF3B1D6E), Color(0xFF6B21A8))
                                                )
                                            )
                                            .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                },
                                onClick = { showAppearanceSheet = true }
                            )
                        }
                        if (q.isEmpty() || "audio".contains(q) || "equalizer".contains(q) || "dsp".contains(q) || "quality".contains(q)) {
                            HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                            SettingsRowItem(
                                icon = Icons.Rounded.GraphicEq,
                                iconBg = Color(0xFF4C1D95),
                                title = "Audio & Playback",
                                subtitle = "${currentQuality.title} • 32-bit DSP Equalizer, Volume Panel",
                                onClick = { showAudioSheet = true }
                            )
                        }
                        if (q.isEmpty() || "library".contains(q) || "download".contains(q)) {
                            HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                            SettingsRowItem(
                                icon = Icons.Rounded.LibraryMusic,
                                iconBg = Color(0xFF241C44),
                                title = "Library & Content",
                                subtitle = "$downloadedCount offline songs • Playlist organization",
                                onClick = { }
                            )
                        }
                    }
                }
            }

            // --- SECTION 3: System & About ---
            val matchesSystem = q.isEmpty() || "data".contains(q) || "storage".contains(q) || "cache".contains(q) || "backup".contains(q) || "about".contains(q) || "version".contains(q)
            if (matchesSystem) {
                item {
                    Text(
                        "System & About",
                        color = VeltColors.SilverMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF130E22))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
                    ) {
                        if (q.isEmpty() || "data".contains(q) || "storage".contains(q) || "cache".contains(q) || "backup".contains(q)) {
                            SettingsRowItem(
                                icon = Icons.Rounded.CloudUpload,
                                iconBg = Color(0xFF281C38),
                                title = "Data & Storage",
                                subtitle = "Cache clearing, Offline downloads, Backup",
                                onClick = { }
                            )
                        }
                        if (q.isEmpty() || "about".contains(q) || "version".contains(q)) {
                            HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                            SettingsRowItem(
                                icon = Icons.Rounded.Info,
                                iconBg = Color(0xFF301648),
                                title = "About VeltPulse",
                                subtitle = "v5.2.1-pro • Media3 ExoPlayer & 32-bit DSP",
                                onClick = { showAboutDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- SUB-SCREEN: Appearance & Visuals Bottom Sheet ---
    if (showAppearanceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppearanceSheet = false },
            containerColor = Color(0xFF0F091E)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        "Appearance & Visuals",
                        color = VeltColors.SilverPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Accent Palette Grid
                item {
                    Text("Signature Accent", color = VeltColors.SilverMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val accents = listOf(
                        "Violet" to Color(0xFFA855F7),
                        "Crimson" to Color(0xFFD8B4B4),
                        "Ocean" to Color(0xFF38BDF8),
                        "Sage" to Color(0xFF6B8E62),
                        "Amber" to Color(0xFFEAB308),
                        "Rose" to Color(0xFFEC4899),
                        "Mono" to Color(0xFFE2E8F0)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        accents.take(4).forEach { (name, color) ->
                            val isSel = selectedAccent == name
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color.copy(alpha = 0.35f))
                                    .border(2.dp, if (isSel) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedAccent = name
                                        appearancePrefs.edit().putString("accent", name).apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Functional Toggles with persistence
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF160E28))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    ) {
                        SettingsToggleRow("Dynamic Now Playing", "Match player background with album cover artwork", dynamicNowPlaying) {
                            dynamicNowPlaying = it
                            appearancePrefs.edit().putBoolean("dynamic_now_playing", it).apply()
                        }
                        HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                        SettingsToggleRow("Word-by-word lyrics", "Real-time syllable sync & neon halo lyrics glow", wordByWordLyrics) {
                            wordByWordLyrics = it
                            appearancePrefs.edit().putBoolean("word_by_word_lyrics", it).apply()
                        }
                        HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                        SettingsToggleRow("Wavy Seekbar", "Multi-layer fluid wavy progress slider on Now Playing", wavySeekbar) {
                            wavySeekbar = it
                            appearancePrefs.edit().putBoolean("wavy_seekbar", it).apply()
                        }
                        HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                        SettingsToggleRow("Rotating Artwork Background", "Fluid ambient motion in Now Playing background", rotatingBackground) {
                            rotatingBackground = it
                            appearancePrefs.edit().putBoolean("rotating_background", it).apply()
                        }
                        HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                        SettingsToggleRow("Haptic Feedback", "Subtle vibration on playback buttons and toggles", hapticFeedback) {
                            hapticFeedback = it
                            appearancePrefs.edit().putBoolean("haptic_feedback", it).apply()
                        }
                    }
                }
            }
        }
    }

    // --- SUB-SCREEN: Audio & Playback Bottom Sheet ---
    if (showAudioSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAudioSheet = false },
            containerColor = Color(0xFF0F091E)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        "Audio & Playback",
                        color = VeltColors.SilverPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Streaming Quality Card
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF160E28))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                            .clickable { showQualityPicker = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wi-Fi Streaming Quality", color = VeltColors.SilverPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("${currentQuality.title} (${currentQuality.badge}) • ${currentQuality.sampleRate}", color = VeltColors.SilverMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = VeltColors.SilverMuted)
                    }
                }

                // Equalizer Quick Access Card
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF3B1D6E), Color(0xFF26124A))
                                )
                            )
                            .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .clickable {
                                showAudioSheet = false
                                onOpenEq()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(VeltColors.NeonViolet.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("32-bit DSP Equalizer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Shape frequencies, Bass boost & Presets", color = VeltColors.LavenderMuted, fontSize = 12.sp)
                            }
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                    }
                }

                // Audio Engine Controls with persistence
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF160E28))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    ) {
                        SettingsToggleRow("Internal Direct Output", "Direct low-latency rendering to audio hardware", internalDirectOutput) {
                            internalDirectOutput = it
                            audioPrefs.edit().putBoolean("internal_direct_output", it).apply()
                        }
                        HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                        SettingsToggleRow("Software Volume (USB DAC)", "DAC hardware volume synchronization when available", softwareVolume) {
                            softwareVolume = it
                            audioPrefs.edit().putBoolean("software_volume", it).apply()
                        }
                        HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                        SettingsToggleRow("Loudness Normalization", if (loudnessNormalization) "On • -14 LUFS target audio level" else "Off • Play original recording volume", loudnessNormalization) {
                            loudnessNormalization = it
                            audioPrefs.edit().putBoolean("loudness_normalization", it).apply()
                        }
                        HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                        SettingsToggleRow("System Audio Effects", "Allow Dolby Atmos & system equalizer to process sound", systemAudioEffects) {
                            systemAudioEffects = it
                            audioPrefs.edit().putBoolean("system_audio_effects", it).apply()
                        }
                        HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                        SettingsToggleRow("Studio Master Clarity", "Lossless unshaped bit-perfect output", studioMasterClarity) {
                            studioMasterClarity = it
                            audioPrefs.edit().putBoolean("studio_master_clarity", it).apply()
                        }
                        HorizontalDivider(color = VeltColors.Hairline, thickness = 0.8.dp)
                        SettingsToggleRow("Show Auto EQ in Volume Panel", "Include equalizer shortcut in player volume sheet", showAutoEqInVolumePanel) {
                            showAutoEqInVolumePanel = it
                            audioPrefs.edit().putBoolean("show_auto_eq", it).apply()
                        }
                    }
                }
            }
        }
    }

    // --- Quality Picker Sheet ---
    if (showQualityPicker) {
        AudioQualitySheet(
            onDismiss = { showQualityPicker = false },
            currentOption = currentQuality,
            onSelectOption = { opt ->
                currentQuality = opt
                AudioQualityStorage.setSelectedQuality(context, opt)
            }
        )
    }

    // --- Edit Profile Dialog ---
    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(userName) }
        var tempPhoto by remember { mutableStateOf(userPhotoUrl) }
        LaunchedEffect(tempPhotoPath) {
            if (tempPhotoPath.isNotBlank()) {
                tempPhoto = tempPhotoPath
                tempPhotoPath = ""
            }
        }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = Color(0xFF160E28),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Edit Profile", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Upload your photo directly from your phone and set your listener display name.",
                        color = VeltColors.LavenderMuted,
                        fontSize = 13.sp
                    )

                    // Avatar Circle Preview
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF7C3AED), Color(0xFFD946EF))
                                )
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (tempPhoto.isNotBlank()) {
                            AsyncImage(
                                model = tempPhoto,
                                contentDescription = "User Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = tempName.take(1).uppercase().ifBlank { "L" },
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Choose Image from Phone button
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1752)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = VeltColors.NeonViolet, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Photo from Phone", color = Color.White, fontSize = 12.sp)
                    }

                    if (tempPhoto.isNotBlank()) {
                        TextButton(onClick = { tempPhoto = "" }) {
                            Text("Remove Photo", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Listener Name", color = VeltColors.SilverSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VeltColors.NeonViolet,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = tempName.trim().ifBlank { "Listener" }
                        val cleanPhoto = tempPhoto.trim()
                        userName = cleanName
                        userPhotoUrl = cleanPhoto
                        profilePrefs.edit()
                            .putString("name", cleanName)
                            .putString("photo_url", cleanPhoto)
                            .apply()
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VeltColors.NeonViolet
                    )
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = VeltColors.SilverMuted)
                }
            }
        )
    }

    // --- Last.fm Dialog ---
    if (showLfmDialog) {
        AlertDialog(
            onDismissRequest = { showLfmDialog = false },
            containerColor = Color(0xFF160E28),
            shape = RoundedCornerShape(24.dp),
            title = { Text("Last.fm Scrobbler", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Connect your Last.fm account to scrobble tracks in real-time.",
                        color = VeltColors.LavenderMuted,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = lfmUsernameInput,
                        onValueChange = { lfmUsernameInput = it },
                        label = { Text("Username", color = VeltColors.SilverSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VeltColors.NeonViolet,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lfmApiKeyInput,
                        onValueChange = { lfmApiKeyInput = it },
                        label = { Text("API Key", color = VeltColors.SilverSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VeltColors.NeonViolet,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lfmApiSecretInput,
                        onValueChange = { lfmApiSecretInput = it },
                        label = { Text("API Secret", color = VeltColors.SilverSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VeltColors.NeonViolet,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        lfmApi.username = lfmUsernameInput.trim()
                        lfmApi.apiKey = lfmApiKeyInput.trim()
                        lfmApi.apiSecret = lfmApiSecretInput.trim()
                        showLfmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VeltColors.NeonViolet)
                ) {
                    Text("Connect", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        lfmApi.username = ""
                        lfmApi.apiKey = ""
                        lfmApi.apiSecret = ""
                        lfmUsernameInput = ""
                        lfmApiKeyInput = ""
                        lfmApiSecretInput = ""
                        showLfmDialog = false
                    }
                ) {
                    Text("Disconnect", color = Color(0xFFEF4444))
                }
            }
        )
    }

    // --- Authentic VeltPulse About Dialog ---
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = Color(0xFF160E28),
            shape = RoundedCornerShape(26.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VeltPulseLogo(size = 38.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("VeltPulse Studio", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("v5.2.1-pro • Stable", color = VeltColors.NeonViolet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "VeltPulse is a high-fidelity lossless streaming music player engineered with a custom 32-bit DSP audio engine, real-time synchronized floating lyrics, and a signature velvet neon aesthetic.",
                        color = VeltColors.SilverSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Playback Engine", color = VeltColors.SilverMuted, fontSize = 12.sp)
                        Text("Media3 ExoPlayer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Audio Processing", color = VeltColors.SilverMuted, fontSize = 12.sp)
                        Text("32-bit DSP Equalizer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lyrics Engine", color = VeltColors.SilverMuted, fontSize = 12.sp)
                        Text("LRCLIB Synced API", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Scrobbler", color = VeltColors.SilverMuted, fontSize = 12.sp)
                        Text("Last.fm 2.0 API", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = VeltColors.NeonViolet)
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showYouTubeDialog && youTubeAccountManager != null && okHttpClient != null) {
        YouTubeLoginSheet(
            accountManager = youTubeAccountManager,
            okHttpClient = okHttpClient,
            onDismiss = { showYouTubeDialog = false },
            onImportPlaylist = onImportYouTubePlaylist
        )
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    trailingDot: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = VeltColors.SilverPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = VeltColors.SilverMuted, fontSize = 12.sp, maxLines = 1)
        }
        if (trailingDot) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(VeltColors.NeonViolet)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        if (trailingContent != null) {
            trailingContent()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = VeltColors.SilverMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = VeltColors.SilverMuted, fontSize = 11.sp, maxLines = 1)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VeltColors.NeonViolet
            )
        )
    }
}
