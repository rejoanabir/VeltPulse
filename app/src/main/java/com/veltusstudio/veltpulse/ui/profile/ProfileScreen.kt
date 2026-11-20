package com.veltusstudio.veltpulse.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.veltpulse.core.theme.VeltBackdrop
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.core.theme.liquidGlass
import com.veltusstudio.veltpulse.data.local.LibrarySnapshot
import com.veltusstudio.veltpulse.data.local.LyricsPreferences
import com.veltusstudio.veltpulse.data.remote.LastFmApi

@Composable
fun ProfileScreen(
    userName: String,
    library: LibrarySnapshot,
    downloadedCount: Int,
    lfmApi: LastFmApi,
    onUpdateName: (String) -> Unit,
    onOpenEq: () -> Unit,
    onClearHistory: () -> Unit = {},
    showLfmDialogInitially: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lyricsPrefs = remember { LyricsPreferences(context) }

    var showNameDialog by remember { mutableStateOf(false) }
    var showJourneyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLastFmDialog by remember { mutableStateOf(showLfmDialogInitially) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var draftName by remember(userName) { mutableStateOf(userName) }

    var lfmUsernameInput by remember { mutableStateOf(lfmApi.username) }
    var lfmApiKeyInput by remember { mutableStateOf(lfmApi.apiKey) }
    var lfmApiSecretInput by remember { mutableStateOf(lfmApi.apiSecret) }

    VeltBackdrop(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Box(
                    modifier = Modifier.size(88.dp).clip(CircleShape).liquidGlass(44.dp, 0.12f, 0.32f).clickable { showNameDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(userName.take(1).uppercase(), style = MaterialTheme.typography.displaySmall, color = VeltColors.SilverPrimary)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(userName, style = MaterialTheme.typography.headlineMedium, color = VeltColors.SilverPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text("VeltPulse · LastWave Glossy Edition", color = VeltColors.SilverMuted, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(22.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatColumn("${library.playlists.size}", "Playlists")
                    StatColumn("${library.liked.size}", "Liked")
                    StatColumn("${library.history.size}", "Played")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().liquidGlass(20.dp, 0.08f, 0.22f).clickable { showJourneyDialog = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = VeltColors.SilverPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your VeltPulse Journey", color = VeltColors.SilverPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${library.history.size} tracks played · $downloadedCount saved offline", color = VeltColors.SilverMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = VeltColors.SilverMuted, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.height(22.dp))

                SettingsRow(
                    icon = Icons.Rounded.CloudSync,
                    title = "Last.fm Profile & Login",
                    subtitle = if (lfmApi.isConnected) "Connected as @${lfmApi.username} • Auto-scrobbling" else "Tap to log in and sync stats"
                ) {
                    lfmUsernameInput = lfmApi.username
                    lfmApiKeyInput = lfmApi.apiKey
                    lfmApiSecretInput = lfmApi.apiSecret
                    showLastFmDialog = true
                }

                SettingsRow(
                    icon = Icons.Rounded.Lyrics,
                    title = "Time-Synced Lyrics & Karaoke",
                    subtitle = "Spotlight: ${if (lyricsPrefs.wordByWordEnabled) "On" else "Off"} · Sync: ${if (lyricsPrefs.offsetMs > 0) "+" else ""}${lyricsPrefs.offsetMs}ms"
                ) {
                    showLyricsDialog = true
                }

                SettingsRow(Icons.Rounded.Tune, "Audio & Equalizer", "10-Band DSP · Stereo Processing", onOpenEq)
                SettingsRow(Icons.Rounded.PersonOutline, "Display Name", userName) { showNameDialog = true }
                SettingsRow(Icons.Rounded.History, "Clear Play History", "${library.history.size} items", onClearHistory)
                SettingsRow(Icons.Rounded.Info, "About VeltPulse × LastWave", "v1.2 · Glossy Edition", onClick = { showAboutDialog = true })
            }
        }
    }

    if (showLastFmDialog) {
        AlertDialog(
            onDismissRequest = { showLastFmDialog = false },
            containerColor = VeltColors.Obsidian,
            title = {
                Text("Last.fm Profile Login", color = VeltColors.SilverPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Connect your Last.fm account to activate live scrobbling, listening stats, genre analytics, and the 8-mode smart playlist generator.",
                        color = VeltColors.SilverMuted,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = lfmUsernameInput,
                        onValueChange = { lfmUsernameInput = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lfmApiKeyInput,
                        onValueChange = { lfmApiKeyInput = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lfmApiSecretInput,
                        onValueChange = { lfmApiSecretInput = it },
                        label = { Text("API Secret / Session Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lfmApi.username = lfmUsernameInput.trim()
                        lfmApi.apiKey = lfmApiKeyInput.trim()
                        lfmApi.apiSecret = lfmApiSecretInput.trim()
                        showLastFmDialog = false
                    }
                ) {
                    Text("Connect & Save", color = VeltColors.SilverPrimary, fontWeight = FontWeight.Bold)
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
                        showLastFmDialog = false
                    }
                ) {
                    Text("Disconnect", color = Color(0xFFEF4444))
                }
            }
        )
    }

    if (showLyricsDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            containerColor = VeltColors.Obsidian,
            title = {
                Text("Time-Synced Lyrics & Karaoke", color = VeltColors.SilverPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Word-by-Word Spotlight", color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Luminous karaoke illumination as words are sung", color = VeltColors.SilverMuted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = lyricsPrefs.wordByWordEnabled,
                            onCheckedChange = { lyricsPrefs.setWordByWord(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Romanize Scripts", color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Convert Japanese, Korean, Chinese & Cyrillic to Latin", color = VeltColors.SilverMuted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = lyricsPrefs.autoRomanize,
                            onCheckedChange = { lyricsPrefs.setRomanize(it) }
                        )
                    }

                    Column {
                        Text("Time Sync Calibration (${if (lyricsPrefs.offsetMs > 0) "+" else ""}${lyricsPrefs.offsetMs}ms)", color = VeltColors.SilverPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Calibrate if lyrics lead or trail the audio", color = VeltColors.SilverMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("-500ms", color = VeltColors.SilverSecondary, fontSize = 12.sp, modifier = Modifier.clickable { lyricsPrefs.setOffset(lyricsPrefs.offsetMs - 500L) })
                            Text("-200ms", color = VeltColors.SilverSecondary, fontSize = 12.sp, modifier = Modifier.clickable { lyricsPrefs.setOffset(lyricsPrefs.offsetMs - 200L) })
                            Text("0ms (Reset)", color = VeltColors.PulseCyanHint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { lyricsPrefs.setOffset(0L) })
                            Text("+200ms", color = VeltColors.SilverSecondary, fontSize = 12.sp, modifier = Modifier.clickable { lyricsPrefs.setOffset(lyricsPrefs.offsetMs + 200L) })
                            Text("+500ms", color = VeltColors.SilverSecondary, fontSize = 12.sp, modifier = Modifier.clickable { lyricsPrefs.setOffset(lyricsPrefs.offsetMs + 500L) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricsDialog = false }) {
                    Text("Done", color = VeltColors.SilverPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = VeltColors.Obsidian,
            title = { Text("Display Name", color = VeltColors.SilverPrimary) },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VeltColors.SilverPrimary,
                        unfocusedTextColor = VeltColors.SilverPrimary,
                        focusedBorderColor = VeltColors.SilverPrimary,
                        unfocusedBorderColor = VeltColors.Hairline
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftName.isNotBlank()) onUpdateName(draftName.trim())
                    showNameDialog = false
                }) { Text("Save", color = VeltColors.SilverPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel", color = VeltColors.SilverMuted) }
            }
        )
    }

    if (showJourneyDialog) {
        val topArtist = library.history.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "None yet"
        AlertDialog(
            onDismissRequest = { showJourneyDialog = false },
            containerColor = VeltColors.Obsidian,
            title = { Text("Your VeltPulse Journey", color = VeltColors.SilverPrimary) },
            text = { Text("Tracks Played: ${library.history.size}\nLiked Songs: ${library.liked.size}\nOffline Saved: $downloadedCount\nTop Artist: $topArtist", color = VeltColors.SilverSecondary) },
            confirmButton = { TextButton(onClick = { showJourneyDialog = false }) { Text("Close", color = VeltColors.SilverPrimary) } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = VeltColors.Obsidian,
            title = { Text("VeltPulse × LastWave", color = VeltColors.SilverPrimary) },
            text = { Text("Version 1.2 · Glossy Edition\nEngineered by Veltus Studio\nFull-Bleed Cover Art · Luminous Karaoke Spotlight · Media3 · ExoPlayer · LastWave Engine", color = VeltColors.SilverSecondary) },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("OK", color = VeltColors.SilverPrimary) } }
        )
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = VeltColors.SilverPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = VeltColors.SilverMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = VeltColors.SilverSecondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = VeltColors.SilverPrimary, fontSize = 15.sp)
            Text(subtitle, color = VeltColors.SilverMuted, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = VeltColors.SilverMuted, modifier = Modifier.size(18.dp))
    }
}
