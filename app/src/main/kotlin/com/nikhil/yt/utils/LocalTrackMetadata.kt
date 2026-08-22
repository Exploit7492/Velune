package com.nikhil.yt.utils

/**
 * Local-file artist/title enrichment.
 *
 * Ported from phoenix-boss/mavins (expo-video branch,
 * `libs/playerSetup.tsx`: `extractArtistFromFilename` +
 * `enrichLocalTrackMetadata`), since MediaStore's own ARTIST column is
 * routinely empty, null, or the literal string `"<unknown>"` for locally
 * ripped or downloaded files that never had a proper ID3 artist tag
 * written — very common for anything not sourced from an official release.
 *
 * "Upcoming Artist" replaces "Unknown Artist" throughout as the generic
 * fallback, matching the wording Mavins settled on, before falling back
 * to parsing the filename.
 */
object LocalTrackMetadata {
    const val UPCOMING_ARTIST = "Upcoming Artist"

    private val HYPHEN_SPLIT = Regex("[-\u2013\u2014]")
    private val FEAT_MATCH = Regex("feat\\.?\\s+([^)\\]]+)", RegexOption.IGNORE_CASE)
    private val BRACKET_MATCH = Regex("\\[([^]]+)]$")
    private val PAREN_MATCH = Regex("\\(([^)]+)\\)$")
    private val PAREN_EXCLUDE = Regex("feat|ft|remix|live|acoustic|version", RegexOption.IGNORE_CASE)

    private fun stripExtension(filename: String): String = filename.substringBeforeLast('.', filename)

    private fun isGenericArtist(artist: String?): Boolean =
        artist.isNullOrBlank() ||
            artist.equals("Unknown Artist", ignoreCase = true) ||
            artist == "<unknown>" ||
            artist == UPCOMING_ARTIST

    /**
     * Best-effort artist guess from a bare filename, tried in the same
     * order Mavins tries it:
     * 1. Split on a hyphen/en-dash/em-dash. Whichever side is shorter (and
     *    under 30 chars) is assumed to be the artist — artist names tend
     *    to run shorter than song titles — otherwise take the right-hand
     *    side (the far more common "Artist - Title" convention).
     * 2. A "feat. X" credit.
     * 3. A trailing "[X]" bracket.
     * 4. A trailing "(X)" parenthetical, unless it looks like a
     *    remix/live/version/feat qualifier rather than an actual name.
     * 5. [UPCOMING_ARTIST] if none of the above found anything usable.
     */
    fun extractArtistFromFilename(filename: String): String {
        if (filename.isBlank()) return UPCOMING_ARTIST
        val withoutExt = stripExtension(filename)

        val hyphenSplit = withoutExt.split(HYPHEN_SPLIT)
        if (hyphenSplit.size >= 2) {
            val left = hyphenSplit[0].trim()
            val right = hyphenSplit[1].trim()
            if (left.isNotEmpty() && left.length < right.length && left.length < 30) return left
            if (right.isNotEmpty()) return right
        }

        FEAT_MATCH.find(withoutExt)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        BRACKET_MATCH.find(withoutExt)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        PAREN_MATCH.find(withoutExt)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() && !PAREN_EXCLUDE.containsMatchIn(it) }?.let { return it }

        return UPCOMING_ARTIST
    }

    /**
     * Given a local track's raw MediaStore title/artist plus its
     * filesystem path, returns a (title, artist) pair with a missing or
     * generic artist filled in — from an "Artist - Title" pattern already
     * in the title if present, otherwise parsed from the filename via
     * [extractArtistFromFilename]. A real ID3 artist tag is always left
     * exactly as-is; this only ever fires when MediaStore's tag was
     * genuinely missing or one of the generic placeholders.
     */
    fun enrich(rawTitle: String?, rawArtist: String?, filePath: String?): Pair<String, String> {
        var title = rawTitle?.trim().orEmpty()
        var artist = rawArtist

        if (isGenericArtist(artist)) {
            val titleHyphenSplit = if (title.isNotEmpty()) title.split(HYPHEN_SPLIT) else emptyList()
            if (titleHyphenSplit.size >= 2) {
                val left = titleHyphenSplit[0].trim()
                val right = titleHyphenSplit[1].trim()
                if (left.isNotEmpty() && left.length < right.length && left.length < 30) {
                    artist = left
                    title = right
                } else if (right.isNotEmpty()) {
                    artist = right
                    title = left
                }
            } else if (!filePath.isNullOrBlank()) {
                val filename = filePath.substringAfterLast('/')
                val guessedArtist = extractArtistFromFilename(filename)
                artist = guessedArtist
                if (guessedArtist != UPCOMING_ARTIST) {
                    val withoutExt = stripExtension(filename)
                    val sourceLower = withoutExt.lowercase()
                    val artistLower = guessedArtist.lowercase()
                    val idx = sourceLower.indexOf(artistLower)
                    if (idx >= 0 && withoutExt.length > guessedArtist.length) {
                        val remaining = withoutExt.substring(idx + guessedArtist.length)
                        val cleanedTitle = remaining.trimStart('-', '\u2013', '\u2014', ' ').trim()
                        if (cleanedTitle.isNotEmpty()) title = cleanedTitle
                    }
                }
            } else {
                artist = UPCOMING_ARTIST
            }
        }

        if (isGenericArtist(artist)) artist = UPCOMING_ARTIST

        if (title.isBlank()) {
            title = filePath?.substringAfterLast('/')?.let { stripExtension(it) } ?: "Unknown Track"
        }

        return title to artist!!
    }
}
