package com.veltusstudio.veltpulse.data.local

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class LyricsPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("veltpulse_lyrics_prefs", Context.MODE_PRIVATE)

    var offsetMs: Long by mutableLongStateOf(prefs.getLong("offset_ms", 0L))
        private set

    var wordByWordEnabled: Boolean by mutableStateOf(prefs.getBoolean("word_by_word", true))
        private set

    var autoRomanize: Boolean by mutableStateOf(prefs.getBoolean("auto_romanize", false))
        private set

    fun setOffset(offset: Long) {
        offsetMs = offset
        prefs.edit().putLong("offset_ms", offset).apply()
    }

    fun setWordByWord(enabled: Boolean) {
        wordByWordEnabled = enabled
        prefs.edit().putBoolean("word_by_word", enabled).apply()
    }

    fun setRomanize(enabled: Boolean) {
        autoRomanize = enabled
        prefs.edit().putBoolean("auto_romanize", enabled).apply()
    }
}
