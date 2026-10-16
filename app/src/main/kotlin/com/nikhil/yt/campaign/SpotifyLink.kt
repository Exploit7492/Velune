/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.campaign

/**
 * Parses a Spotify share link into the (kind, id) pair the embed and
 * oEmbed endpoints need.
 *
 * Deliberately a pure object with no I/O: the resolver's Spotify branch
 * is the only part of campaign playback that reaches a *third-party*
 * host, so the URL-shape logic — the part most likely to be handed a
 * link a user pasted by hand — is kept separable and unit-testable
 * without a network or an Android device.
 */
object SpotifyLink {

    enum class Kind { TRACK, ALBUM, ARTIST }

    data class Target(val kind: Kind, val id: String)

    // Spotify ids are exactly 22 base62 characters. The optional
    // `intl-<lang>/` segment appears when a link is copied from a
    // localised open.spotify.com page (e.g. /intl-de/track/...), and
    // `embed/` when copied straight from an iframe — both are real
    // shapes users paste, so both must parse.
    private val URL_REGEX = Regex(
        """open\.spotify\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?(track|album|artist)/([A-Za-z0-9]{22})(?![A-Za-z0-9])"""
    )

    private val URI_REGEX = Regex("""spotify:(track|album|artist):([A-Za-z0-9]{22})(?![A-Za-z0-9])""")

    /** True when [sourceUrl] looks like a Spotify entity link at all —
     * cheap pre-filter so the resolver does not run its Spotify path on
     * an ordinary YouTube URL. */
    fun isSpotifyUrl(sourceUrl: String): Boolean = parse(sourceUrl) != null

    fun parse(sourceUrl: String): Target? {
        val trimmed = sourceUrl.trim()
        URL_REGEX.find(trimmed)?.let { match ->
            val (kind, id) = match.destructured
            kindFromString(kind)?.let { return Target(it, id) }
        }
        URI_REGEX.find(trimmed)?.let { match ->
            val (kind, id) = match.destructured
            kindFromString(kind)?.let { return Target(it, id) }
        }
        return null
    }

    private fun kindFromString(raw: String): Kind? = when (raw) {
        "track" -> Kind.TRACK
        "album" -> Kind.ALBUM
        "artist" -> Kind.ARTIST
        else -> null
    }

    /**
     * The public oEmbed endpoint for [target]. Returns JSON with a
     * `title` field (the track title, or the artist/album name) — the
     * one metadata source that needs no credential.
     */
    fun oEmbedUrl(target: Target): String =
        "https://open.spotify.com/oembed?url=https://open.spotify.com/${target.kind.name.lowercase()}/${target.id}"

    /**
     * The embed page URL for [target]. For track links it exposes the
     * track's own title and artists; for artist/album links it exposes
     * a `trackList`, whose first entry is the entity's headline track.
     */
    fun embedUrl(target: Target): String =
        "https://open.spotify.com/embed/${target.kind.name.lowercase()}/${target.id}"
}
