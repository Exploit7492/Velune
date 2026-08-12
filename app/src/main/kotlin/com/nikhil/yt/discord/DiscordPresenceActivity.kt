package com.nikhil.yt.discord

import com.nikhil.yt.BuildConfig

data class DiscordPresenceActivity(
    val name: String? = null,
    val type: ActivityType = ActivityType.LISTENING,
    val details: String? = null,
    val state: String? = null,
    val applicationId: Long = BuildConfig.DISCORD_APPLICATION_ID_LONG,
    val timestamps: Timestamps = Timestamps(),
    val assets: Assets = Assets(),
) {
    enum class ActivityType(val nativeValue: Int) {
        PLAYING(0), LISTENING(2), WATCHING(3), COMPETING(5),
    }
    data class Timestamps(val startMs: Long? = null, val endMs: Long? = null)
    data class Assets(
        val largeImage: String? = null,
        val largeText: String? = null,
        val smallImage: String? = null,
        val smallText: String? = null,
    )
}
