/*
 * Ported from Echo Music (GPL-3.0) and adapted to Velune's LyricsProvider interface.
 */

package com.nikhil.yt.lyrics

import android.content.Context
import com.music.youlyplus.YouLyPlus
import com.nikhil.yt.constants.EnableYouLyPlusKey
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.get

object YouLyPlusLyricsProvider : LyricsProvider {
    override val name = "YouLyPlus"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableYouLyPlusKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = YouLyPlus.getLyrics(title, artist, duration, album, id)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        YouLyPlus.getAllLyrics(title, artist, duration, album, id, null, callback)
    }
}
