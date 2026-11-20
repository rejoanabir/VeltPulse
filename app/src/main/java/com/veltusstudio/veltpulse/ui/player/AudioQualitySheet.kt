package com.veltusstudio.veltpulse.ui.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.core.theme.glossyGlassDeck

enum class AudioQualityOption(
    val id: String,
    val title: String,
    val badge: String,
    val description: String,
    val sampleRate: String,
    val icon: ImageVector
) {
    DOLBY_ATMOS(
        id = "dolby_atmos",
        title = "Dolby Atmos",
        badge = "ATMOS",
        description = "Spatial audio profile with wide dynamic head-stage",
        sampleRate = "Spatial Multichannel",
        icon = Icons.Rounded.SurroundSound
    ),
    HI_RES_LOSSLESS(
        id = "hi_res",
        title = "Hi-Res Lossless",
        badge = "HI-RES",
        description = "Bit-perfect studio audio with pristine transients",
        sampleRate = "24-bit / 192 kHz FLAC",
        icon = Icons.Rounded.HighQuality
    ),
    CD_LOSSLESS(
        id = "cd_lossless",
        title = "CD Lossless",
        badge = "CD",
        description = "Original disc fidelity without psychoacoustic loss",
        sampleRate = "16-bit / 44.1 kHz FLAC",
        icon = Icons.Rounded.GraphicEq
    ),
    STANDARD_320(
        id = "standard_320",
        title = "Standard Quality",
        badge = "320K",
        description = "High bitrate AAC/Opus balanced for speed and clarity",
        sampleRate = "320 kbps Stereo",
        icon = Icons.Rounded.Speed
    ),
    YOUTUBE_MUSIC(
        id = "yt_music",
        title = "YouTube Music",
        badge = "YT",
        description = "Official cloud direct stream stream source",
        sampleRate = "Adaptive Opus 160-256 kbps",
        icon = Icons.Rounded.MusicNote
    ),
    DATA_SAVER(
        id = "data_saver",
        title = "Data Saver",
        badge = "SAVER",
        description = "Optimized for low bandwidth and mobile data roaming",
        sampleRate = "96 kbps Opus",
        icon = Icons.Rounded.Speed
    )
}

object AudioQualityStorage {
    private const val PREFS_NAME = "veltpulse_audio_quality"
    private const val KEY_SELECTED_ID = "selected_quality_id"

    fun getSelectedQuality(context: Context): AudioQualityOption {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = sp.getString(KEY_SELECTED_ID, AudioQualityOption.HI_RES_LOSSLESS.id)
        return AudioQualityOption.values().firstOrNull { it.id == id } ?: AudioQualityOption.HI_RES_LOSSLESS
    }

    fun setSelectedQuality(context: Context, option: AudioQualityOption) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SELECTED_ID, option.id).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualitySheet(
    onDismiss: () -> Unit,
    currentOption: AudioQualityOption,
    onSelectOption: (AudioQualityOption) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VeltColors.Obsidian,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Audio Quality",
                        color = VeltColors.SilverPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Select master streaming fidelity & DSP decoding",
                        color = VeltColors.SilverMuted,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        currentOption.badge,
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudioQualityOption.values().forEach { option ->
                    val isSelected = option == currentOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    if (isSelected) {
                                        listOf(
                                            Color(0xFF38BDF8).copy(alpha = 0.22f),
                                            Color(0xFF0F172A).copy(alpha = 0.85f)
                                        )
                                    } else {
                                        listOf(
                                            Color.White.copy(alpha = 0.05f),
                                            Color.White.copy(alpha = 0.02f)
                                        )
                                    }
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.60f) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                onSelectOption(option)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF38BDF8) else VeltColors.SilverSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    option.title,
                                    color = VeltColors.SilverPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.10f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        option.badge,
                                        color = VeltColors.SilverSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                option.description,
                                color = VeltColors.SilverMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                            Text(
                                option.sampleRate,
                                color = if (isSelected) Color(0xFF38BDF8) else VeltColors.SilverMuted.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = VeltColors.PureBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
