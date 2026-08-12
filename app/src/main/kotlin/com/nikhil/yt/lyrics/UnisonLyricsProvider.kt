/*
 * Ported from Echo Music (GPL-3.0) and adapted to Velune's LyricsProvider interface.
 * TTMLParser reference points at Velune's own betterlyrics module.
 */

package com.nikhil.yt.lyrics

import android.content.Context
import com.nikhil.yt.unison.Unison
import com.nikhil.yt.constants.EnableUnisonKey
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.get

object UnisonLyricsProvider : LyricsProvider {
    override val name: String = "Unison"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableUnisonKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = Unison.getLyrics(
        videoId = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = duration
    ).map { convertIfTTML(it) }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        Unison.getAllLyrics(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = { callback(convertIfTTML(it)) }
        )
    }

    private fun convertIfTTML(content: String): String {
        return if (content.trimStart().startsWith("<tt", ignoreCase = true)) {
            val parsedLines = com.nikhil.yt.betterlyrics.TTMLParser.parseTTML(content)
            com.nikhil.yt.betterlyrics.TTMLParser.toLRC(parsedLines)
        } else {
            content
        }
    }
}
