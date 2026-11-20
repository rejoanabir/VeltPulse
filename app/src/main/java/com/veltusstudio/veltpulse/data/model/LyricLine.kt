package com.veltusstudio.veltpulse.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class LyricWord(
    val word: String,
    val startMs: Long = 0L,
    val endMs: Long = 0L
)

@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
) {
    fun getOrComputeWords(nextLineTimeMs: Long?): List<LyricWord> {
        if (words.isNotEmpty()) return words
        val clean = text.trim()
        if (clean.isBlank()) return emptyList()
        val tokens = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val lineStart = timeMs.coerceAtLeast(0L)
        val defaultDuration = (tokens.size * 360L).coerceIn(1200L, 5000L)
        val lineDuration = if (nextLineTimeMs != null && nextLineTimeMs > lineStart) {
            (nextLineTimeMs - lineStart).coerceIn(1000L, 8000L)
        } else {
            defaultDuration
        }

        val totalChars = tokens.sumOf { it.length }.coerceAtLeast(1)
        var currentStart = lineStart
        return tokens.mapIndexed { index, token ->
            val weight = token.length.toFloat() / totalChars.toFloat()
            val wordDuration = (lineDuration * weight).toLong().coerceAtLeast(180L)
            val end = if (index == tokens.lastIndex) lineStart + lineDuration else currentStart + wordDuration
            val word = LyricWord(word = token, startMs = currentStart, endMs = end)
            currentStart = end
            word
        }
    }
}
