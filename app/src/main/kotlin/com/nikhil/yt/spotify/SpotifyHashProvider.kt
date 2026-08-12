package com.nikhil.yt.spotify
object SpotifyHashProvider {
    fun getHash(text: String): String { var h = 0L; for (b in text.toByteArray()) { h = (h shl 5) - h + b.toLong(); h = h and h }; return h.toString(16) }
}
