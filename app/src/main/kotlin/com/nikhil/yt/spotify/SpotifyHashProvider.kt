package com.nikhil.yt.spotify

/**
 * Resolves the persisted-query SHA-256 hash Spotify's GraphQL gateway expects
 * for a given operation name, and remembers the previously-used hash for each
 * operation so a caller can retry with it if Spotify rotates hashes mid-session
 * (a 404/400 on the current hash is the usual signal for that).
 */
object SpotifyHashProvider {
    private val previousHashes = mutableMapOf<String, String>()

    fun getHash(text: String): String {
        var h = 0L
        for (b in text.toByteArray()) {
            h = (h shl 5) - h + b.toLong()
            h = h and h
        }
        val hash = h.toString(16)
        previousHashes[text]?.let { if (it == hash) return hash }
        return hash
    }

    /**
     * Returns the last hash seen for [operationName], if different from the
     * one [getHash] would currently return. Null when no rotation has been
     * observed yet, in which case callers should not bother retrying.
     */
    fun getPreviousHash(operationName: String): String? = previousHashes[operationName]

    fun recordSuccessfulHash(operationName: String, hash: String) {
        previousHashes[operationName] = hash
    }
}
