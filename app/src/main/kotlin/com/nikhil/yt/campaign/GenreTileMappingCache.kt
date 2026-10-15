/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.campaign

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Task 59 Part 2b-b, Round 13 (handover.md, Mavins-web repo) — the
 * cache-lifecycle half of the sub-part Round 12 flagged as "genuinely
 * the next sub-part, not a small follow-up." Split further, per this
 * project's own mandatory task-splitting rule: this round builds ONLY
 * this cache, self-contained and independently verifiable; wiring it
 * into `MusicService.kt`'s still-stubbed `campaignSlotProvider = { null }`
 * is deliberately left for the next round, not attempted here.
 *
 * Wraps [CampaignRepository.fetchGenreTileMapping] with the periodic-
 * refresh caching behavior that function's own KDoc already asks
 * callers to provide ("Callers are expected to fetch this periodically
 * and cache the result client-side... rather than call it fresh on
 * every single genre-tile tap") — this is that caller, the first one
 * built.
 *
 * `object`, not `class` — matches this package's neighboring
 * [DiscordAssetRegistrar] singleton-cache convention (`com.nikhil.yt.discord`),
 * for the same reason: the whole point of a cache is amortizing
 * repeated fetches across every genre-tile tap for the lifetime of the
 * app process, which only works if every caller shares the same
 * instance rather than each queue construction getting its own
 * freshly-empty one.
 */
object GenreTileMappingCache {
    private const val TAG = "GenreTileMappingCache"

    // 15 minutes — a judgment call, not a confirmed product requirement;
    // no specific cadence was specified anywhere in this task's prior
    // rounds. Long enough that a user browsing several genre tiles in
    // one session doesn't refetch the whole table repeatedly; short
    // enough that a newly-reviewed mapping (an admin resolving an
    // unreviewed tile) reaches a running app session within one
    // reasonable sitting, not just on next app restart. Revisit this
    // number directly if either assumption turns out wrong in practice.
    private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L

    private val repository = CampaignRepository()
    private val mutex = Mutex()

    private var cachedMapping: Map<String, String?> = emptyMap()

    // 0L means "never fetched" -- deliberately NOT inferred from
    // `cachedMapping.isEmpty()`. The live `campaign_genre_tile_mapping`
    // table is expected to start (and may remain, for a while) genuinely
    // empty per Round 12's own note #4 ("still zero rows until real
    // production traffic or a manual seed populates it -- expected, not
    // a bug"). If staleness were judged by map emptiness instead of this
    // separate timestamp, an empty live table would make [ensureFresh]
    // refetch on every single call forever, defeating the entire point
    // of periodic caching for exactly the state this feature is
    // expected to actually launch in.
    private var lastFetchedAtMs: Long = 0L

    /**
     * What a genre-tile tap should send to
     * [CampaignRepository.fetchNextCampaignForQueueSlot], or `null` when
     * there is genuinely no genre context.
     *
     * The distinction this function exists to preserve:
     *
     * * **No genre context** (search, album, playlist, artist radio,
     *   autoplay — `queue.genre` is null). Never reaches this function;
     *   the caller sends `null` and the RPC reads it as "any eligible
     *   campaign". Campaign injection is a monetization slot that belongs
     *   on every one of these (§33).
     * * **Genre context, resolved** (the tile has a reviewed mapping).
     *   Returns that canonical genre id.
     * * **Genre context, unresolved** (tile unseen or not yet reviewed).
     *   Returns the **raw tile title**, never `null`. Fires
     *   [CampaignRepository.ingestGenreTile] fire-and-forget so the tile
     *   reaches an admin for review.
     * * **Genre context, reviewed as a mood** (present key, null value —
     *   an admin confirmed this tile is not genre-targetable). Returns
     *   `null`, deliberately: there is no genre promise to keep, so the
     *   slot behaves like an unscoped queue.
     *
     * The third case is the §H fix. Returning `null` there is what let an
     * R&B tile be served Hip-Hop: the tile had no reviewed mapping, so the
     * client sent null, and the RPC's null branch means "match any genre".
     * A genre-scoped slot must never widen to the whole catalogue. Sending
     * the raw title instead lets the server resolve it against the
     * canonical taxonomy, and the server fails closed if it cannot — which
     * is a deliberate no-campaign slot, not a wrong-genre one.
     *
     * The tile title is the right thing to send rather than a client-side
     * guess: it is the same string the server's own resolver reads, so the
     * mapping stays a data decision on one side of the wire.
     */
    suspend fun resolveTile(tileTitle: String): String? {
        ensureFresh()

        if (!cachedMapping.containsKey(tileTitle)) {
            repository.ingestGenreTile(tileTitle)
            return tileTitle
        }

        // A present key with a null value is an admin's reviewed,
        // deliberate "this tile is a mood, not a genre" decision. There is
        // no genre promise to keep, so the slot behaves like any other
        // unscoped queue and the RPC's match-any branch is correct here.
        return cachedMapping[tileTitle]
    }

    private suspend fun ensureFresh() {
        val now = System.currentTimeMillis()
        if (lastFetchedAtMs != 0L && now - lastFetchedAtMs < REFRESH_INTERVAL_MS) return

        mutex.withLock {
            // Re-check inside the lock -- another coroutine may have
            // just finished a refresh while this one was waiting.
            val nowInLock = System.currentTimeMillis()
            if (lastFetchedAtMs != 0L && nowInLock - lastFetchedAtMs < REFRESH_INTERVAL_MS) return@withLock

            val fetched = repository.fetchGenreTileMapping()
            cachedMapping = fetched
            lastFetchedAtMs = nowInLock
            Timber.tag(TAG).d("Refreshed genre tile mapping cache: ${fetched.size} reviewed entries")
        }
    }
}
