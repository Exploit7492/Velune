/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.db.MusicDatabase
import com.nikhil.yt.db.entities.EventWithSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * The History page shows the user's own listen history — songs they've actually
 * played, grouped by when — and nothing else. It used to also pull a trending
 * feed from a third-party vercel endpoint plus user-configured "channels" as a
 * notifications feature; that's been removed entirely so this screen only ever
 * reflects local playback, sourced straight from the `event` table.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    application: Application,
    val database: MusicDatabase,
) : AndroidViewModel(application) {

    /** Chronological groups of played songs, most recent group first. */
    val historyGroups: StateFlow<List<HistoryGroup>> = database.events()
        .map { events -> groupByDate(events) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeEvent(event: EventWithSong) {
        database.query { delete(event.event) }
    }

    fun clearHistory() {
        database.query { clearListenHistory() }
    }

    private fun groupByDate(events: List<EventWithSong>): List<HistoryGroup> {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)
        val monthAgo = today.minusDays(30)

        return events
            .groupBy { event ->
                val date = event.event.timestamp.toLocalDate()
                when {
                    date == today -> HistoryPeriod.TODAY
                    date == yesterday -> HistoryPeriod.YESTERDAY
                    date.isAfter(weekAgo) -> HistoryPeriod.THIS_WEEK
                    date.isAfter(monthAgo) -> HistoryPeriod.THIS_MONTH
                    else -> HistoryPeriod.EARLIER
                }
            }
            .toSortedMap(compareBy { it.ordinal })
            .map { (period, items) -> HistoryGroup(period, items) }
    }
}

enum class HistoryPeriod { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, EARLIER }

data class HistoryGroup(val period: HistoryPeriod, val events: List<EventWithSong>)
