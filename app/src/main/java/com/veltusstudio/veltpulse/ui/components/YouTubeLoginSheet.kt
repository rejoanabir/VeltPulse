package com.veltusstudio.veltpulse.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.data.remote.YouTubeAccountManager
import com.veltusstudio.veltpulse.data.remote.YouTubeRemotePlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLoginSheet(
    accountManager: YouTubeAccountManager,
    okHttpClient: OkHttpClient,
    onDismiss: () -> Unit,
    onImportPlaylist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by accountManager.account.collectAsState()

    var showWebView by remember { mutableStateOf(false) }
    var showManualCookie by remember { mutableStateOf(false) }
    var manualCookieText by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }

    var playlists by remember { mutableStateOf<List<YouTubeRemotePlaylist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    var importedPlaylistIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importingPlaylistIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Automatically load playlists when connected
    LaunchedEffect(account.isLoggedIn) {
        if (account.isLoggedIn) {
            isLoadingPlaylists = true
            accountManager.fetchAccountProfile(okHttpClient)
            playlists = runCatching { accountManager.fetchUserPlaylists(okHttpClient) }.getOrDefault(emptyList())
            isLoadingPlaylists = false
        }
    }

    if (showWebView) {
        Dialog(
            onDismissRequest = { showWebView = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F071D)
            ) {
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    // Top App Bar for WebView
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showWebView = false }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Sign in to YouTube Music",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showWebView = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Log in with your Google Account. Once signed in, your session cookies will be captured securely and saved on your device.",
                            color = VeltColors.SilverSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false

                                // Remove WebView identifier tokens so Google sign-in works smoothly
                                val originalUa = settings.userAgentString
                                settings.userAgentString = originalUa
                                    .replace("; wv", "")
                                    .replace("Version/4.0 ", "")

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val host = request?.url?.host?.lowercase() ?: return true
                                        val isAllowed = host == "accounts.google.com" ||
                                                host == "music.youtube.com" ||
                                                host == "youtube.com" ||
                                                host.endsWith(".youtube.com") ||
                                                host.endsWith(".google.com")
                                        return !isAllowed
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        val uri = if (url != null) android.net.Uri.parse(url) else null
                                        val host = uri?.host?.lowercase() ?: return true
                                        val isAllowed = host == "accounts.google.com" ||
                                                host == "music.youtube.com" ||
                                                host == "youtube.com" ||
                                                host.endsWith(".youtube.com") ||
                                                host.endsWith(".google.com")
                                        return !isAllowed
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val curUrl = url ?: "https://music.youtube.com"
                                        val cookies = cookieManager.getCookie(curUrl).orEmpty()
                                        val hasAuth = cookies.contains("SAPISID") ||
                                                cookies.contains("__Secure-3PAPISID") ||
                                                (cookies.contains("LOGIN_INFO") && cookies.contains("SID"))

                                        if (hasAuth && !isConnecting) {
                                            isConnecting = true
                                            scope.launch(Dispatchers.Main) {
                                                accountManager.saveCookies(cookies)
                                                scope.launch(Dispatchers.IO) {
                                                    accountManager.fetchAccountProfile(okHttpClient)
                                                }
                                                Toast.makeText(context, "YouTube Music Connected!", Toast.LENGTH_SHORT).show()
                                                showWebView = false
                                                isConnecting = false
                                            }
                                        }
                                    }
                                }

                                loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F")
                            }
                        }
                    )
                }
            }
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
            color = Color(0xFF140A26)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFCC0000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.SmartDisplay,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "YouTube Music",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (account.isLoggedIn) "Account Connected" else "Account & Library Sync",
                                color = if (account.isLoggedIn) Color(0xFF22C55E) else VeltColors.SilverMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = VeltColors.SilverSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (account.isLoggedIn) {
                    // --- LOGGED IN VIEW ---
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Profile Banner Card
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            listOf(Color(0xFF2B134E), Color(0xFF1D0E35))
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (account.avatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = account.avatarUrl,
                                            contentDescription = "Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, Color(0xFF22C55E), CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(VeltColors.SurfaceElevated),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = account.name.ifBlank { "YouTube Music User" },
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0xFF22C55E).copy(alpha = 0.20f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("ACTIVE", color = Color(0xFF22C55E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (account.handle.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = account.handle,
                                                color = VeltColors.SilverMuted,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Quick Sync Buttons
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        importingPlaylistIds = importingPlaylistIds + "VLLM"
                                        scope.launch(Dispatchers.IO) {
                                            onImportPlaylist("VLLM")
                                            importedPlaylistIds = importedPlaylistIds + "VLLM"
                                            importingPlaylistIds = importingPlaylistIds - "VLLM"
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1752))
                                ) {
                                    if ("VLLM" in importingPlaylistIds) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            if ("VLLM" in importedPlaylistIds) Icons.Rounded.Check else Icons.Rounded.Favorite,
                                            contentDescription = null,
                                            tint = if ("VLLM" in importedPlaylistIds) Color(0xFF22C55E) else Color(0xFFEC4899),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if ("VLLM" in importedPlaylistIds) "Synced" else "Sync Liked",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isLoadingPlaylists = true
                                            playlists = runCatching { accountManager.fetchUserPlaylists(okHttpClient) }.getOrDefault(emptyList())
                                            isLoadingPlaylists = false
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24153E))
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = VeltColors.SilverSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Refresh", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        // Playlists Section Header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Your YouTube Playlists",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${playlists.size} found",
                                    color = VeltColors.SilverMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (isLoadingPlaylists) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = VeltColors.NeonViolet, strokeWidth = 2.dp)
                                }
                            }
                        } else if (playlists.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No playlists found in your YouTube library.", color = VeltColors.SilverMuted, fontSize = 12.sp)
                                }
                            }
                        } else {
                            items(playlists, key = { it.id }) { pl ->
                                val isImported = pl.id in importedPlaylistIds
                                val isImporting = pl.id in importingPlaylistIds

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1B0F33))
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(VeltColors.SurfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (pl.thumbnail.isNotBlank()) {
                                            AsyncImage(
                                                model = pl.thumbnail,
                                                contentDescription = pl.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = VeltColors.SilverMuted)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (pl.trackCountText.isNotBlank()) {
                                            Text(
                                                text = pl.trackCountText,
                                                color = VeltColors.SilverMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (!isImported && !isImporting) {
                                                importingPlaylistIds = importingPlaylistIds + pl.id
                                                scope.launch(Dispatchers.IO) {
                                                    onImportPlaylist(pl.id)
                                                    importedPlaylistIds = importedPlaylistIds + pl.id
                                                    importingPlaylistIds = importingPlaylistIds - pl.id
                                                }
                                            }
                                        },
                                        enabled = !isImported && !isImporting,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isImported) Color(0xFF22C55E).copy(alpha = 0.25f) else VeltColors.NeonViolet
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        if (isImporting) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else if (isImported) {
                                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Imported", color = Color(0xFF22C55E), fontSize = 11.sp)
                                        } else {
                                            Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Import", color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Disconnect / Log Out Button
                    OutlinedButton(
                        onClick = {
                            accountManager.logout()
                            Toast.makeText(context, "Logged out of YouTube Music", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.40f))
                    ) {
                        Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect Account", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                } else {
                    // --- LOGGED OUT VIEW ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFCC0000).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFCC0000).copy(alpha = 0.40f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.SmartDisplay,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            "Connect YouTube Music",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Log in with your Google account to automatically sync your playlists, import Liked Songs, and personalize your recommendations.",
                            color = VeltColors.SilverSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Primary In-App Login Button
                        Button(
                            onClick = { showWebView = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000))
                        ) {
                            Icon(Icons.Rounded.Login, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Sign in with Google / YouTube", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Manual Cookie Option
                        TextButton(
                            onClick = { showManualCookie = !showManualCookie }
                        ) {
                            Text(
                                if (showManualCookie) "Hide manual cookie option" else "Advanced: Paste cookie manually",
                                color = VeltColors.LavenderMuted,
                                fontSize = 12.sp
                            )
                        }

                        if (showManualCookie) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualCookieText,
                                onValueChange = { manualCookieText = it },
                                placeholder = { Text("Paste 'Cookie:' header containing SAPISID...", color = VeltColors.SilverMuted, fontSize = 11.sp) },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (manualCookieText.isNotBlank()) {
                                        accountManager.saveCookies(manualCookieText)
                                        scope.launch(Dispatchers.IO) {
                                            accountManager.fetchAccountProfile(okHttpClient)
                                        }
                                        Toast.makeText(context, "Cookie saved!", Toast.LENGTH_SHORT).show()
                                        showManualCookie = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VeltColors.NeonViolet)
                            ) {
                                Text("Save & Connect Cookie", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
