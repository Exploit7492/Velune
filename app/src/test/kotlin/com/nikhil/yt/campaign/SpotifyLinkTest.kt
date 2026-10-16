package com.nikhil.yt.campaign

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyLinkTest {

    @Test
    fun parsesAStandaloneTrackLink() {
        val target = SpotifyLink.parse("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
        assertEquals(SpotifyLink.Kind.TRACK, target?.kind)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", target?.id)
    }

    @Test
    fun parsesALocalisedIntlPrefix() {
        // Copied straight from a localised page — a real shape users paste.
        val target = SpotifyLink.parse("https://open.spotify.com/intl-de/artist/1n5LD9Ar3D6RK2X2ewGvXb")
        assertEquals(SpotifyLink.Kind.ARTIST, target?.kind)
        assertEquals("1n5LD9Ar3D6RK2X2ewGvXb", target?.id)
    }

    @Test
    fun parsesAnEmbedUrl() {
        val target = SpotifyLink.parse("https://open.spotify.com/embed/album/1DFixLWuPkv3KT3TnV35m3?utm_source=oembed")
        assertEquals(SpotifyLink.Kind.ALBUM, target?.kind)
        assertEquals("1DFixLWuPkv3KT3TnV35m3", target?.id)
    }

    @Test
    fun parsesASpotifyUri() {
        val target = SpotifyLink.parse("spotify:track:4cOdK2wGLETKBW3PvgPWqT")
        assertEquals(SpotifyLink.Kind.TRACK, target?.kind)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", target?.id)
    }

    @Test
    fun ignoresATrailingQueryString() {
        val target = SpotifyLink.parse("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=abc123")
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", target?.id)
    }

    @Test
    fun rejectsAYoutubeUrl() {
        assertNull(SpotifyLink.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(SpotifyLink.isSpotifyUrl("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun rejectsAnIdOfTheWrongLength() {
        // Spotify ids are exactly 22 characters; a longer run of id
        // characters must not be truncated to a plausible-looking 22.
        assertNull(SpotifyLink.parse("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqTXYZ"))
        assertNull(SpotifyLink.parse("https://open.spotify.com/track/4cOdK2wGLETKBW3Pvg"))
    }

    @Test
    fun acceptsAWellFormedButNonexistentId() {
        // A 22-character id that does not exist is still a *shape*-valid
        // link; existence is decided by the fetch, not the parser. This
        // pins that separation so a future reader does not "fix" the
        // parser into guessing at validity.
        assertEquals(
            "2sU8FHNRqK2tJOVjaBLs93",
            SpotifyLink.parse("https://open.spotify.com/track/2sU8FHNRqK2tJOVjaBLs93")?.id,
        )
    }

    @Test
    fun buildsTheOembedAndEmbedUrls() {
        val target = SpotifyLink.Target(SpotifyLink.Kind.ARTIST, "1n5LD9Ar3D6RK2X2ewGvXb")
        assertEquals(
            "https://open.spotify.com/oembed?url=https://open.spotify.com/artist/1n5LD9Ar3D6RK2X2ewGvXb",
            SpotifyLink.oEmbedUrl(target),
        )
        assertEquals(
            "https://open.spotify.com/embed/artist/1n5LD9Ar3D6RK2X2ewGvXb",
            SpotifyLink.embedUrl(target),
        )
    }
}

class CampaignSpotifyResolverTest {

    /** The shape an artist embed page's `entity` actually has, captured
     * from a live response. */
    private fun artistEntity(): JSONObject = JSONObject(
        """
        {
          "type": "artist",
          "name": "Carla's Dreams",
          "id": "1n5LD9Ar3D6RK2X2ewGvXb",
          "trackList": [
            {"uri": "spotify:track:4RBS6EFpwOwq2f6OMcIms4", "title": "Cum Ne Noi",
             "subtitle": "Carla's Dreams, Delia", "isPlayable": true},
            {"uri": "spotify:track:5ycCO6HJZXBW2HOMT36ZhO", "title": "Second", "subtitle": "x"}
          ]
        }
        """.trimIndent()
    )

    private fun trackEntity(): JSONObject = JSONObject(
        """
        {
          "type": "track",
          "name": "Never Gonna Give You Up",
          "id": "4cOdK2wGLETKBW3PvgPWqT",
          "artists": [{"name": "Rick Astley", "uri": "spotify:artist:0gxyHStUsqpMadRV0Di1Qt"}]
        }
        """.trimIndent()
    )

    @Test
    fun readsTheHeadlineTrackFromAnArtistEmbed() {
        val track = CampaignSpotifyResolver.trackFromEntity(artistEntity())
        assertEquals("Cum Ne Noi", track?.title)
        assertEquals("Carla's Dreams, Delia", track?.artist)
    }

    @Test
    fun normalisesTheNonBreakingSpaceSpotifyPutsBetweenCoArtists() {
        // Observed live as "Carla's Dreams,\u00a0Delia" — a plain trim()
        // leaves U+00A0 in place, which then defeats an artist split.
        val entity = JSONObject(
            """{"name":"Artist","trackList":[{"title":"T","subtitle":"A,\u00a0B"}]}"""
        )
        assertEquals("A, B", CampaignSpotifyResolver.trackFromEntity(entity)?.artist)
    }

    @Test
    fun collapsesDoubledSpacesInATitle() {
        val entity = JSONObject("""{"name":"Too   Many  Spaces"}""")
        assertEquals("Too Many Spaces", CampaignSpotifyResolver.trackFromEntity(entity)?.title)
    }

    @Test
    fun readsTitleAndArtistFromATrackEmbed() {
        val track = CampaignSpotifyResolver.trackFromEntity(trackEntity())
        assertEquals("Never Gonna Give You Up", track?.title)
        assertEquals("Rick Astley", track?.artist)
    }

    @Test
    fun joinsMultipleArtistsFromATrackEmbed() {
        val entity = JSONObject(
            """{"name":"Song","artists":[{"name":"A"},{"name":"B"}]}"""
        )
        assertEquals("A, B", CampaignSpotifyResolver.trackFromEntity(entity)?.artist)
    }

    @Test
    fun picksTheFirstTrackListEntryAsTheHeadline() {
        // Order in the embed is the artist's own ranking; the first entry
        // is the "top track" this resolves to.
        val entity = JSONObject(
            """{"name":"Artist","trackList":[{"title":"Top","subtitle":"A"},{"title":"Deep Cut","subtitle":"A"}]}"""
        )
        assertEquals("Top", CampaignSpotifyResolver.trackFromEntity(entity)?.title)
    }

    @Test
    fun returnsNullForAnEmptyEntity() {
        assertNull(CampaignSpotifyResolver.trackFromEntity(JSONObject("{}")))
    }

    @Test
    fun usesTitleWhenNameIsAbsent() {
        val entity = JSONObject("""{"title":"Only Title"}""")
        assertEquals("Only Title", CampaignSpotifyResolver.trackFromEntity(entity)?.title)
    }

    @Test
    fun ignoresAnEmptyTrackListAndFallsBackToTheEntityName() {
        val entity = JSONObject("""{"name":"Fallback","trackList":[]}""")
        assertEquals("Fallback", CampaignSpotifyResolver.trackFromEntity(entity)?.title)
    }
}
