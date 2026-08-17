/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.nikhil.yt.utils.GlobalLog
import com.nikhil.yt.constants.PreferredLyricsProvider
import com.nikhil.yt.constants.PreferredLyricsProviderKey
import com.nikhil.yt.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.nikhil.yt.extensions.toEnum
import com.nikhil.yt.models.MediaMetadata
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.reportException
import com.nikhil.yt.utils.NetworkConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    private val baseProviders =
        listOf(
            SimpMusicLyricsProvider,
            BetterLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            YouTubeLyricsProvider,
            PaxSenixLyricsProvider,
            YouLyPlusLyricsProvider,
            UnisonLyricsProvider,
        )

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata, preferredProviderOnly: Boolean = false): String {
        // Cancel any in-flight getAllLyrics() run sharing this instance (e.g. the
        // manual "search lyrics" flow) before doing a single-result fetch, same as
        // before.
        currentLyricsJob?.cancel()

        // FIX: cache lookups/writes are now consistently keyed by mediaMetadata.id
        // everywhere in this class (see getAllLyrics below) -- this read used to be
        // the only place actually keying by id, but nothing ever *wrote* under that
        // key (getAllLyrics wrote under a title+artist string instead), so this was
        // always a guaranteed cache miss. Real fetch/write below now populates it.
        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            GlobalLog.append(Log.DEBUG, "LyricsHelper", "Found lyrics in cache for ${mediaMetadata.title}")
            return cached.lyrics
        }
        
        GlobalLog.append(Log.DEBUG, "LyricsHelper", "Fetching lyrics for ${mediaMetadata.title} (Artist: ${mediaMetadata.artists.joinToString { it.name }}, Album: ${mediaMetadata.album?.title})")

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        
        if (!isNetworkAvailable) {
            GlobalLog.append(Log.WARN, "LyricsHelper", "Network unavailable, aborting lyrics fetch")
            return LYRICS_NOT_FOUND
        }

        val ordered = orderedProviders()
        val providers = if (preferredProviderOnly) listOf(ordered.first()) else ordered
        // FIX: this used to spin up an independent `CoroutineScope(SupervisorJob() +
        // Dispatchers.IO)` with no parent job, so when the *caller* (MusicService's
        // collectLatest over the current song, in particular) cancelled this call on
        // a song change, only the `deferred.await()` suspension point threw --  the
        // actual provider fetch running inside that orphaned scope kept going in the
        // background to completion, doing a full round of network calls for a song
        // the user had already skipped past, for a result nothing would ever read.
        // `coroutineScope { }` makes this a properly *structured* child of the
        // caller's job instead: cancelling the caller now actually cancels the
        // in-flight provider call too, rather than leaking it.
        val lyrics = kotlinx.coroutines.coroutineScope {
            val deferred = async(Dispatchers.IO) {
                for (provider in providers) {
                    val enabled = provider.isEnabled(context)

                    if (enabled) {
                        try {
                            val result = provider.getLyrics(
                                mediaMetadata.id,
                                mediaMetadata.title,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.album?.title,
                                mediaMetadata.duration,
                            )
                            result.onSuccess { lyrics ->
                                if (isMeaningfulLyrics(lyrics)) {
                                    return@async lyrics
                                }
                            }.onFailure {
                                reportException(it)
                            }
                        } catch (e: Exception) {
                            reportException(e)
                        }
                    }
                }
                return@async LYRICS_NOT_FOUND
            }
            deferred.await()
        }

        // Populate the id-keyed cache on a real, meaningful result so the next
        // getLyrics() call for the *same* song (id) -- not a different song that
        // merely shares a title/artist -- can skip the network round trip.
        if (isMeaningfulLyrics(lyrics)) {
            cache.put(mediaMetadata.id, listOf(LyricsResult(providerName = "", lyrics = lyrics)))
        }

        return lyrics
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        songAlbum: String?,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        // FIX (the actual "wrong lyrics on some songs" bug): this used to be
        // `"$songArtists-$songTitle".replace(" ", "")` -- a cache key built purely
        // from displayed title+artist *text*, not the song's id. Any two different
        // songs that happen to share (or normalize to) the same title/artist --
        // duplicate library entries from two different searches, re-uploads, a
        // live/alternate version tagged with an identical title, or just the same
        // track added to the queue twice under two different source ids -- would
        // silently return each other's cached lyrics results from here, with no
        // relation to which song was actually asked for. That's an intermittent,
        // hard-to-reproduce bug by nature: it only misfires when *this session's*
        // 3-entry LruCache happens to already hold an entry for a colliding
        // title/artist, which depends entirely on recent usage. Keying strictly by
        // mediaId removes the collision entirely -- every id maps to exactly one
        // cache slot, always.
        cache.get(mediaId)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        
        if (!isNetworkAvailable) {
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = orderedProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
            providers.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, songAlbum, duration) lyricsCallback@{ lyrics ->
                            if (!isMeaningfulLyrics(lyrics)) return@lyricsCallback
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            cache.put(mediaId, allResult)
        }

        currentLyricsJob?.join()
    }

    private suspend fun orderedProviders(): List<LyricsProvider> {
        val preferred =
            context.dataStore.data
                .first()[PreferredLyricsProviderKey]
                .toEnum(PreferredLyricsProvider.LRCLIB)

        val first =
            when (preferred) {
                PreferredLyricsProvider.LRCLIB -> LrcLibLyricsProvider
                PreferredLyricsProvider.KUGOU -> KuGouLyricsProvider
                PreferredLyricsProvider.BETTER_LYRICS -> BetterLyricsProvider
                PreferredLyricsProvider.SIMPMUSIC -> SimpMusicLyricsProvider
            }

        return listOf(first) + baseProviders.filterNot { provider -> provider == first }
    }

    private fun isMeaningfulLyrics(lyrics: String): Boolean {
        val normalized =
            lyrics
                .replace("\uFEFF", "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == '\u00A0' }

        if (normalized.isEmpty()) return false
        if (normalized == LYRICS_NOT_FOUND) return false

        val remaining =
            TIMESTAMP_REGEX
                .replace(normalized, "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == '\u00A0' }

        return remaining.any { !it.isWhitespace() && it != '\u00A0' }
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        // Bumped from 3: with the id-keyed cache now actually being useful (see
        // getLyrics fix above) and lyrics pre-loading fetching several upcoming
        // queue entries ahead, a 3-slot cache was evicting entries before they'd
        // ever be read back. Lyrics text is small, so this costs little memory.
        private const val MAX_CACHE_SIZE = 20
        private val TIMESTAMP_REGEX = Regex("""\[[0-9]{1,2}:[0-9]{2}(?:\.[0-9]{1,3})?]""")
        private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
