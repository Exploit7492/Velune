/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.pages.BrowseResult
import com.nikhil.yt.constants.HideExplicitKey
import com.nikhil.yt.constants.HideVideoKey
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.get
import com.nikhil.yt.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")

    /**
     * Task 59 Part 2b-b, first sub-part (handover.md): the raw genre-
     * tile title this browse originated from, if any -- e.g.
     * "Afrobeats", "R&B" -- decoded from the query arg
     * MoodAndGenresScreen.kt's own onClick now sends. `null` for the
     * other two callers of this same shared route
     * (ExploreScreen.kt/HomeScreenComponents.kt), which is exactly
     * Round 3's already-decided fail-closed signal: no title means no
     * genre-lock, not an error case to handle specially.
     *
     * Deliberately NOT yet consumed anywhere below this point in this
     * sub-part -- wiring this into an actual `campaignSlotProvider`
     * that calls Part A's `fetchGenreTileMapping()` and threads a
     * resolved genre id into playback (`PlayerConnection.kt`,
     * `MusicService.kt`) is explicitly the next sub-part, not this
     * one. This field exists and is correctly populated end-to-end
     * from the UI tap through to here -- that's this sub-part's whole,
     * self-contained deliverable.
     */
    val genreTileTitle: String? = savedStateHandle.get<String>("genreTile")?.let {
        URLDecoder.decode(it, "UTF-8")
    }

    val result = MutableStateFlow<BrowseResult?>(null)

    init {
        viewModelScope.launch {
            YouTube
                .browse(browseId, params)
                .onSuccess {
                    val hideVideo = context.dataStore.get(HideVideoKey, false)
                    result.value = it.filterExplicit(context.dataStore.get(HideExplicitKey, false)).filterVideo(hideVideo)
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
