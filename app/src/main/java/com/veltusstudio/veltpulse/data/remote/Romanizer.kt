package com.veltusstudio.veltpulse.data.remote

object Romanizer {
    private val initials = arrayOf("g","kk","n","d","tt","r","m","b","pp","s","ss","","j","jj","ch","k","t","p","h")
    private val medials = arrayOf("a","ae","ya","yae","eo","e","yeo","ye","o","wa","wae","oe","yo","u","wo","we","wi","yu","eu","ui","i")
    private val finals = arrayOf("","k","k","k","n","n","n","t","l","l","l","l","l","l","l","l","m","p","p","t","t","ng","t","t","k","t","p","t")

    fun hasNonLatin(text: String): Boolean = text.any { it.code > 0x024F }

    fun romanize(text: String): String {
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            val code = ch.code
            when (code) {
                in 0xAC00..0xD7A3 -> {
                    val s = code - 0xAC00
                    sb.append(initials[s / (21 * 28)])
                    sb.append(medials[(s % (21 * 28)) / 28])
                    sb.append(finals[s % 28])
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
