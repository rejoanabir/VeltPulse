package com.veltusstudio.veltpulse.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.player.AutoEqCatalog
import com.veltusstudio.veltpulse.player.EqPreset
import com.veltusstudio.veltpulse.player.EqState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqBottomSheet(
    state: EqState,
    bandLabels: List<String>,
    presets: List<EqPreset>,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (EqPreset) -> Unit,
    onBandChange: (Int, Float) -> Unit
) {
    var eqQuery by remember { mutableStateOf("") }
    val filteredAutoEq = remember(eqQuery) {
        if (eqQuery.isBlank()) AutoEqCatalog.profiles
        else AutoEqCatalog.profiles.filter { it.name.contains(eqQuery.trim(), ignoreCase = true) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = VeltColors.Obsidian,
        contentColor = VeltColors.SilverPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("10-Band Equalizer", style = MaterialTheme.typography.headlineMedium, color = VeltColors.SilverPrimary)
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = VeltColors.PureBlack, checkedTrackColor = VeltColors.PulseCyanHint)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { preset ->
                    FilterChip(
                        selected = state.selectedPreset == preset.name,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset.name, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VeltColors.SilverPrimary,
                            selectedLabelColor = VeltColors.PureBlack,
                            containerColor = VeltColors.SurfaceDark,
                            labelColor = VeltColors.SilverSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = eqQuery,
                onValueChange = { eqQuery = it },
                placeholder = { Text("Search AutoEQ (Nothing Ear, Sony, HD 600...)", fontSize = 12.sp, color = VeltColors.SilverMuted) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = VeltColors.SilverPrimary, unfocusedTextColor = VeltColors.SilverPrimary, focusedBorderColor = VeltColors.SilverPrimary, unfocusedBorderColor = VeltColors.Hairline),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredAutoEq) { profile ->
                    FilterChip(
                        selected = state.selectedPreset == profile.name,
                        onClick = { onSelectPreset(profile) },
                        label = { Text(profile.name, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VeltColors.PulseCyanHint,
                            selectedLabelColor = VeltColors.PureBlack,
                            containerColor = VeltColors.SurfaceElevated,
                            labelColor = VeltColors.SilverSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            bandLabels.forEachIndexed { index, label ->
                val gain = state.bandGainsDb.getOrElse(index) { 0f }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = VeltColors.SilverMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp))
                    Slider(
                        value = gain,
                        onValueChange = { onBandChange(index, it) },
                        valueRange = -12f..12f,
                        enabled = state.enabled,
                        colors = SliderDefaults.colors(
                            thumbColor = VeltColors.SilverPrimary,
                            activeTrackColor = VeltColors.PulseCyanHint,
                            inactiveTrackColor = VeltColors.Hairline
                        ),
                        modifier = Modifier.weight(1f).height(30.dp)
                    )
                    Text("%+.1f".format(gain), color = VeltColors.SilverSecondary, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                }
            }
        }
    }
}
