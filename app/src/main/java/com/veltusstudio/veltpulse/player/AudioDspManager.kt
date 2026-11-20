package com.veltusstudio.veltpulse.player

import android.media.audiofx.DynamicsProcessing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EqPreset(val name: String, val gainsDb: List<Float>)

data class EqState(
    val enabled: Boolean = false,
    val selectedPreset: String = "Flat",
    val bandGainsDb: List<Float> = List(10) { 0f }
)

class AudioDspManager(context: android.content.Context? = null) {
    private val prefs = context?.getSharedPreferences("veltpulse_eq", android.content.Context.MODE_PRIVATE)

    val bandLabels = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    private val cutoffsHz = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    val presets = listOf(
        EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Bass Boost", listOf(6f, 5f, 3.5f, 1.5f, 0f, 0f, 0f, 0.5f, 1f, 1f)),
        EqPreset("Vocal Clarity", listOf(-2f, -1.5f, -0.5f, 1f, 2.5f, 4f, 4.5f, 3f, 1.5f, 0.5f)),
        EqPreset("V-Shape", listOf(4.5f, 3.5f, 1.5f, -1.5f, -2f, -1f, 1.5f, 3.5f, 4.5f, 4f)),
        EqPreset("Harman IEM", listOf(3.5f, 3f, 1.5f, 0f, -0.5f, 0.5f, 2.5f, 3.5f, 2f, 1f))
    )

    private val _state = MutableStateFlow(loadSavedState())
    private fun loadSavedState(): EqState {
        val p = prefs ?: return EqState()
        val en = p.getBoolean("enabled", false)
        val preset = p.getString("preset", "Flat").orEmpty().ifBlank { "Flat" }
        val gains = (0 until 10).map { i -> p.getFloat("band_$i", 0f) }
        return EqState(en, preset, gains)
    }
    val state: StateFlow<EqState> = _state.asStateFlow()

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var attachedSessionId: Int = 0

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == attachedSessionId) return
        release()
        runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true, 10,
                false, 0,
                false, 0,
                false
            ).build()
            DynamicsProcessing(0, audioSessionId, config).apply {
                attachedSessionId = audioSessionId
                dynamicsProcessing = this
                applyCurrentState()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        applyCurrentState()
    }

    fun applyPreset(preset: EqPreset) {
        _state.update { it.copy(enabled = true, selectedPreset = preset.name, bandGainsDb = preset.gainsDb) }
        applyCurrentState()
    }

    fun setBandGain(index: Int, gainDb: Float) {
        val clamped = gainDb.coerceIn(-12f, 12f)
        _state.update { cur ->
            val updated = cur.bandGainsDb.toMutableList().apply { this[index] = clamped }
            cur.copy(enabled = true, selectedPreset = "Custom", bandGainsDb = updated)
        }
        applyCurrentState()
    }

    private fun applyCurrentState() {
        prefs?.edit()?.apply {
            val cur = _state.value
            putBoolean("enabled", cur.enabled)
            putString("preset", cur.selectedPreset)
            cur.bandGainsDb.forEachIndexed { i, g -> putFloat("band_$i", g) }
            apply()
        }
        val dp = dynamicsProcessing ?: return
        val cur = _state.value
        runCatching {
            dp.enabled = cur.enabled
            for (channel in 0 until 2) {
                for (i in 0 until 10) {
                    val band = dp.getPreEqBandByChannelIndex(channel, i)
                    band.isEnabled = cur.enabled
                    band.cutoffFrequency = cutoffsHz[i]
                    band.gain = cur.bandGainsDb[i]
                    dp.setPreEqBandByChannelIndex(channel, i, band)
                }
            }
        }
    }

    fun release() {
        runCatching { dynamicsProcessing?.release() }
        dynamicsProcessing = null
        attachedSessionId = 0
    }
}
