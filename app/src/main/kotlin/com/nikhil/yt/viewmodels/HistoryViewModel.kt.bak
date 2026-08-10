package com.nikhil.yt.viewmodels

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.constants.SeenNotificationIdsKey
import com.nikhil.yt.constants.NotificationLastFetchKey
import com.nikhil.yt.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import javax.inject.Inject

@Serializable
data class TrendingVideo(val video_id: String = "", val title: String = "", val channel_title: String = "", val description: String = "", val thumbnail_url: String? = null, val date: String = "", val details: TrendingDetails? = null, val statistics: TrendingStats? = null, val url: String = "")
@Serializable
data class TrendingDetails(val duration: String = "")
@Serializable
data class TrendingStats(val view_count: String = "0")
@Serializable
data class TrendingResponse(val data: List<TrendingVideo> = emptyList())

@HiltViewModel
class HistoryViewModel @Inject constructor(application: Application, val database: MusicDatabase) : AndroidViewModel(application) {
    private val dataStore = application.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _seenIds = MutableStateFlow<Set<String>>(emptySet())
    val seenIds: StateFlow<Set<String>> = _seenIds.asStateFlow()

    data class NotificationItem(val id: String, val title: String, val source: String, val thumbnailUrl: String?, val publishedAt: String, val type: NotificationType, val contentType: ContentType, val viewCount: Long = 0, val durationSeconds: Int = 0)
    enum class NotificationType { TRENDING, APP_UPDATE }
    enum class ContentType { MUSIC, VIDEO, OTHER }

    init {
        viewModelScope.launch {
            dataStore.data.map { prefs -> prefs[SeenNotificationIdsKey]?.split(",")?.toSet() ?: emptySet() }.collect { _seenIds.value = it }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                val items = fetchGlobalTrending()
                _notifications.value = items.sortedByDescending { it.publishedAt }
                dataStore.edit { prefs -> prefs[NotificationLastFetchKey] = System.currentTimeMillis() }
            } catch (e: Exception) { _error.value = e.message }
            finally { _isLoading.value = false }
        }
    }

    fun markSeen(id: String) {
        viewModelScope.launch {
            val current = _seenIds.value.toMutableSet()
            current.add(id)
            _seenIds.value = current
            dataStore.edit { prefs -> prefs[SeenNotificationIdsKey] = current.joinToString(",") }
        }
    }

    fun markAllSeen() {
        viewModelScope.launch {
            val allIds = _notifications.value.map { it.id }.toSet()
            _seenIds.value = allIds
            dataStore.edit { prefs -> prefs[SeenNotificationIdsKey] = allIds.joinToString(",") }
        }
    }

    private suspend fun fetchGlobalTrending(): List<NotificationItem> = withContext(Dispatchers.IO) {
        val regions = listOf("NG", "US", "GB", "IN", "ZA", "GH", "CA", "AU", "JP", "DE")
        val allItems = mutableListOf<NotificationItem>()
        val seenKeys = mutableSetOf<String>()
        for (region in regions) {
            try {
                val url = URL("https://trendgetter.vercel.app/api/youtube/videos?region_code=$region&limit=50")
                val response = url.readText(timeoutMillis = 15000)
                val data = json.decodeFromString(TrendingResponse.serializer(), response)
                for (video in data.data) {
                    val viewCount = video.statistics?.view_count?.toLongOrNull() ?: 0L
                    if (viewCount < 50000) continue
                    val duration = parseDuration(video.details?.duration ?: "")
                    val contentType = classifyContent(video.title, video.channel_title, duration)
                    if (contentType == ContentType.OTHER) continue
                    val dedupeKey = "${video.channel_title}__${video.title}"
                    if (seenKeys.contains(dedupeKey)) continue
                    seenKeys.add(dedupeKey)
                    val derivedId = video.video_id.ifBlank { video.url.substringAfter("v=").takeIf { it.length == 11 } ?: "${video.channel_title}_${video.title}".replace(Regex("[^a-zA-Z0-9]"), "_").take(60) }
                    val thumb = video.thumbnail_url ?: if (derivedId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) "https://img.youtube.com/vi/$derivedId/mqdefault.jpg" else null
                    allItems.add(NotificationItem(id = "trending_${region}_$derivedId", title = video.title, source = video.channel_title, thumbnailUrl = thumb, publishedAt = video.date, type = NotificationType.TRENDING, contentType = contentType, viewCount = viewCount, durationSeconds = duration))
                }
            } catch (_: Exception) {}
        }
        allItems
    }

    private fun parseDuration(pt: String): Int {
        if (pt.isBlank()) return 0
        val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").find(pt) ?: return 0
        return (m.groupValues[1].toIntOrNull() ?: 0) * 3600 + (m.groupValues[2].toIntOrNull() ?: 0) * 60 + (m.groupValues[3].toIntOrNull() ?: 0)
    }

    private fun classifyContent(title: String, channel: String, duration: Int): ContentType {
        val c = "${title.lowercase()} ${channel.lowercase()}"
        if (duration > 1200) return ContentType.OTHER
        val nonMusic = listOf("minecraft","roblox","fortnite","valorant","pokemon","gaming","gameplay","stream","twitch","reaction","react to","reacts","challenge","hide and seek","among us","gta","call of duty","warzone","apex legends","elden ring","rust ","overwatch","dead by daylight","raid:","mmorpg","let's play","honest trailer","trailer breakdown","trailer reaction","review","breakdown","podcast","episode","ep.","vlog","day in","shrek","disney","marvel","spider-man","avengers","star wars","star trek","netflix","hbo","paramount","film"," movie","teaser trailer","official trailer","season ","series","episode")
        if (nonMusic.any { c.contains(it) }) return ContentType.OTHER
        val mv = listOf("official music video","official video","(official video)","official mv","music video"," mv)","(mv)","video clip","official visual","vevo","directed by")
        if (mv.any { c.contains(it) }) return ContentType.VIDEO
        val music = listOf("official audio","(audio)","lyrics","letra","lyric video","official lyric","audio only","visualizer","official visualizer","provided to youtube")
        if (music.any { c.contains(it) }) return ContentType.MUSIC
        val ch = listOf("records"," music","vevo","entertainment","official","hiphop","rap","reggaeton","latin","afrobeats","afro","naija")
        if (ch.any { channel.lowercase().contains(it) } && duration in 1..480) return ContentType.MUSIC
        if (duration in 1..480) return ContentType.MUSIC
        return ContentType.OTHER
    }
}
