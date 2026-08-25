/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.campaign

/**
 * A single promoted-content card. Sourced from the `track_campaigns`
 * system via [CampaignRepository.fetchActiveCampaigns].
 *
 * All fields come from Supabase RPC responses or live YouTube resolution.
 * No generated/projected/simulated fields exist on this class.
 */
data class CampaignCard(
    val id: String,                 // campaign_id (UUID)
    val songId: String,             // YouTube video ID (extracted from tracks table)
    val trackId: String,            // track_id (UUID in tracks table)
    val artistId: String,           // artist_id (UUID in users table)
    val title: String,              // Resolved from YouTube or fallback to track_title
    val artist: String,             // Resolved from YouTube or fallback to artist_name
    val thumbnailUrl: String,       // Resolved from YouTube or fallback to cover_url
    val totalStreams: Long,         // Blended real + seeded from track_campaigns
    val trendingScore: Double,      // Algorithmic score from get_trending_campaigns
    val geographicTier: String,     // local | regional | national | global
    val currentStage: String,       // planting | germination | root_system | branching | full_bloom
    val certified: Boolean,         // Derived from stage (branching+ = true) or manual flag
    val isLive: Boolean,            // Derived from metadata or manual flag
    val playCount: Long,            // Alias for totalStreams (UI compatibility)
    val ctaLabel: String,           // Dynamic based on stage: Discover → Trending → Hot → Viral → Charting
)
