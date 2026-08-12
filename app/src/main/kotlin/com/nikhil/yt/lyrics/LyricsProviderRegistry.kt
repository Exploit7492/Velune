package com.nikhil.yt.lyrics

object LyricsProviderRegistry {

    private val displayNames = mapOf(
        "LrcLib" to "LRCLIB",
        "Kugou" to "KuGou",
        "BetterLyrics" to "BetterLyrics",
        "SimpMusic" to "SimpMusic",
        "YouLyPlus" to "YouLyPlus",
        "Paxsenix" to "Paxsenix",
    )

    fun getDefaultProviderOrder(): List<String> =
        listOf("LrcLib", "Kugou", "BetterLyrics", "SimpMusic", "YouLyPlus", "Paxsenix")

    fun getDisplayName(id: String): String = displayNames[id] ?: id

    fun serializeProviderOrder(order: List<String>): String = order.joinToString(",")

    fun deserializeProviderOrder(serialized: String): List<String> =
        if (serialized.isBlank()) {
            emptyList()
        } else {
            serialized.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
}
