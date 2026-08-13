/*
 * Velune Project Original (2026)
 * Kòi Natsuko (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.nikhil.yt.innertube.models.response

import com.nikhil.yt.innertube.models.NavigationEndpoint
import com.nikhil.yt.innertube.models.PlaylistPanelRenderer
import com.nikhil.yt.innertube.models.Tabs
import com.nikhil.yt.innertube.models.YouTubeDataPage
import kotlinx.serialization.Serializable

@Serializable
data class NextResponse(
    val contents: Contents,
    val engagementPanels: List<EngagementPanel>? = null,
    val continuationContents: ContinuationContents?,
    val currentVideoEndpoint: NavigationEndpoint?,
) {
    @Serializable
    data class EngagementPanel(
        val engagementPanelSectionListRenderer: EngagementPanelSectionListRenderer? = null
    ) {
        @Serializable
        data class EngagementPanelSectionListRenderer(
            val panelIdentifier: String? = null,
            val content: Content? = null
        ) {
            @Serializable
            data class Content(
                val sectionListRenderer: SectionListRenderer? = null
            ) {
                @Serializable
                data class SectionListRenderer(
                    val contents: List<YouTubeDataPage.Contents.TwoColumnWatchNextResults.Results.Results.Content>? = null
                )
            }
        }
    }

    @Serializable
    data class Contents(
        val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer?,
        val twoColumnWatchNextResults: YouTubeDataPage.Contents.TwoColumnWatchNextResults?,
    ) {
        @Serializable
        data class SingleColumnMusicWatchNextResultsRenderer(
            val tabbedRenderer: TabbedRenderer?,
        ) {
            @Serializable
            data class TabbedRenderer(
                val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer?,
            ) {
                @Serializable
                data class WatchNextTabbedResultsRenderer(
                    val tabs: List<Tabs.Tab>,
                )
            }
        }
    }

    @Serializable
    data class ContinuationContents(
        val playlistPanelContinuation: PlaylistPanelRenderer,
    )
}
