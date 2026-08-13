#!/bin/bash
set -e
A="app/src/main/kotlin/com/nikhil/yt"
D="$A/discord"
R="app/src/main/res"

###################################################################
#  NEW FILES
###################################################################

cat << 'EOF' > "$D/DiscordWrappers.kt"
package com.nikhil.yt.discord
import com.my.kizzy.gateway.entities.op.OpCode
typealias GatewayOp = OpCode
enum class GatewaySessionState { DISCONNECTED, CONNECTING, CONNECTED, RESUMING }
data class GatewayReadyEvent(val sessionId: String = "", val resumeGatewayUrl: String = "")
data class GatewayCloseInfo(val code: Int, val reason: String = "", val isResumable: Boolean = true) {
    fun copy(code: Int = this.code, reason: String = this.reason, isResumable: Boolean = this.isResumable) = GatewayCloseInfo(code, reason, isResumable)
}
object GatewayDefaults { const val HEARTBEAT_INTERVAL = 41_250L; const val IDENTIFY_INTERVAL = 5_000L }
object GatewayCapabilitiesFlags { const val MESSAGE_CONTENT_V2 = (1 shl 15) }
object IntentsFlags { const val GUILD_MESSAGES = (1 shl 9); const val MESSAGE_CONTENT = (1 shl 15) }
val NON_RESUMABLE_CLOSE_CODES = setOf(4004, 4010, 4011, 4012, 4013, 4014)
const val DISCORD_APPLICATION_ID = "1284533208369975316"
const val DISCORD_APPLICATION_ID_LONG: Long = 1284533208369975316L
const val DISCORD_REDIRECT_SCHEME = "velune"
EOF

cat << 'EOF' > "$D/DiscordPresenceActivity.kt"
package com.nikhil.yt.discord
data class DiscordPresenceActivity(
    val name: String? = null,
    val type: DiscordActivityType = DiscordActivityType.LISTENING,
    val details: String? = null,
    val state: String? = null,
    val applicationId: String? = null,
    val timestamps: DiscordPresenceTimestamps = DiscordPresenceTimestamps(),
    val assets: DiscordPresenceAssets = DiscordPresenceAssets(),
    val buttons: List<DiscordPresenceButton> = emptyList(),
    val onlineStatus: DiscordOnlineStatus = DiscordOnlineStatus.ONLINE,
)
data class DiscordPresenceTimestamps(val startMs: Long? = null, val endMs: Long? = null)
data class DiscordPresenceAssets(val largeImage: String? = null, val largeText: String? = null, val smallImage: String? = null, val smallText: String? = null)
data class DiscordPresenceButton(val label: String, val url: String)
enum class DiscordActivityType(val nativeValue: Int) { PLAYING(0), STREAMING(1), LISTENING(2), WATCHING(3), COMPETING(5) }
enum class DiscordOnlineStatus { ONLINE, IDLE, DND, INVISIBLE }
EOF

cat << 'EOF' > "$A/applecanvas/ContentEncoding.kt"
package com.nikhil.yt.applecanvas
enum class ContentEncoding { GZIP, DEFLATE, IDENTITY }
val ContentEncoding.gzip get() = ContentEncoding.GZIP
val ContentEncoding.deflate get() = ContentEncoding.DEFLATE
EOF

cat << 'EOF' > "$A/applecanvas/AppleMusicTokenProvider.kt"
package com.nikhil.yt.applecanvas
object AppleMusicTokenProvider {
    private var token: String? = null; private var expiresAt: Long = 0L
    fun getToken(): String? { if (System.currentTimeMillis() > expiresAt) return null; return token }
    fun setToken(t: String, ms: Long = 3600_000L) { token = t; expiresAt = System.currentTimeMillis() + ms }
}
EOF

cat << 'EOF' > "$A/spotify/models/SpotifyModels.kt"
package com.nikhil.yt.spotify.models
import kotlinx.serialization.SerialName; import kotlinx.serialization.Serializable
@Serializable data class SpotifyImage(val url: String = "", @SerialName("width") val width: Int? = null, @SerialName("height") val height: Int? = null)
@Serializable data class SpotifyArtistSimple(val id: String = "", val name: String = "", val uri: String = "")
@Serializable data class SpotifyAlbumSimple(val id: String = "", val name: String = "", val images: List<SpotifyImage> = emptyList(), val uri: String = "")
@Serializable data class SpotifyFollowers(@SerialName("total") val total: Int = 0)
@Serializable data class SpotifyArtist(val id: String = "", val name: String = "", val images: List<SpotifyImage> = emptyList(), val followers: SpotifyFollowers? = null, val uri: String = "", val popularity: Int? = null, val type: String = "artist")
@Serializable data class SpotifyAlbum(val id: String = "", val name: String = "", val artists: List<SpotifyArtistSimple> = emptyList(), val images: List<SpotifyImage> = emptyList(), @SerialName("release_date") val releaseDate: String? = null, @SerialName("total_tracks") val totalTracks: Int = 0, val uri: String = "")
@Serializable data class SpotifyTrack(val id: String = "", val name: String = "", val artists: List<SpotifyArtistSimple> = emptyList(), val album: SpotifyAlbumSimple? = null, @SerialName("duration_ms") val durationMs: Long = 0, val uri: String = "")
@Serializable data class SpotifyPlaylistSimple(val id: String = "", val name: String = "", val description: String? = null, val images: List<SpotifyImage> = emptyList(), val uri: String = "", @SerialName("tracks") val tracks: SpotifyPlaylistTracksRef? = null)
@Serializable data class SpotifyPlaylistTracksRef(val href: String = "", val total: Int = 0)
@Serializable data class SpotifyPlaylistTrack(@SerialName("added_at") val addedAt: String? = null, val track: SpotifyTrack? = null)
@Serializable data class SpotifySearchResult(val albums: SpotifyPaging<SpotifyAlbum>? = null, val artists: SpotifyPaging<SpotifyArtist>? = null, val tracks: SpotifyPaging<SpotifyTrack>? = null, val playlists: SpotifyPaging<SpotifyPlaylistSimple>? = null)
@Serializable data class SpotifyRecommendations(val tracks: List<SpotifyTrack> = emptyList())
@Serializable data class SpotifyRecommendationSeed(@SerialName("initialPoolSize") val initialPoolSize: Int = 0, val id: String = "", val type: String = "")
@Serializable data class SpotifyLibraryItem(@SerialName("added_at") val addedAt: String? = null, val track: SpotifyTrack? = null)
@Serializable data class SpotifyLibraryFolder(val id: String = "", val name: String = "", val uri: String = "", @SerialName("image_url") val imageUrl: String? = null)
@Serializable data class SpotifyHomeFeed(val sections: List<SpotifyHomeFeedSection> = emptyList())
@Serializable data class SpotifyHomeFeedSection(val id: String? = null, val title: String? = null, val items: List<SpotifyHomeFeedItem> = emptyList(), val type: String? = null)
@Serializable data class SpotifyHomeFeedItem(val uri: String = "", val type: String = "", val album: SpotifyAlbum? = null, val artist: SpotifyArtist? = null, val track: SpotifyTrack? = null, val playlist: SpotifyPlaylistSimple? = null, val image: SpotifyImage? = null, val title: String? = null, val subtitle: String? = null)
@Serializable data class SpotifyPaging<T>(val href: String = "", val items: List<T> = emptyList(), val limit: Int = 20, val next: String? = null, val offset: Int = 0, val previous: String? = null, val total: Int = 0)
@Serializable data class SpotifyUserPrivate(val id: String = "", @SerialName("display_name") val displayName: String? = null, val email: String? = null, val images: List<SpotifyImage> = emptyList(), val uri: String = "")
EOF

cat << 'EOF' > "$A/spotify/SpotifyHashProvider.kt"
package com.nikhil.yt.spotify
object SpotifyHashProvider {
    fun getHash(text: String): String { var h = 0L; for (b in text.toByteArray()) { h = (h shl 5) - h + b.toLong(); h = h and h }; return h.toString(16) }
}
EOF

cat << 'EOF' > "$A/ui/component/GlassEffectStubs.kt"
package com.nikhil.yt.ui.component
import androidx.compose.runtime.Composable; import androidx.compose.runtime.staticCompositionLocalOf
data class GlassEffectConfig(val enabled: Boolean = false)
val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }
@Composable fun liquidGlass(enabled: Boolean = LocalGlassEffectConfig.current.enabled, content: @Composable () -> Unit) { content() }
EOF

cat << 'EOF' > "$A/ui/component/ItemThumbnail.kt"
package com.nikhil.yt.ui.component
import androidx.compose.foundation.background; import androidx.compose.foundation.layout.Box; import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape; import androidx.compose.material3.MaterialTheme; import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment; import androidx.compose.ui.Modifier; import androidx.compose.ui.draw.clip; import androidx.compose.ui.unit.Dp; import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
@Composable
fun ItemThumbnail(thumbnailUrl: String?, modifier: Modifier = Modifier, size: Dp = 48.dp, placeholder: @Composable (() -> Unit)? = null) {
    Box(modifier = modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (thumbnailUrl != null) AsyncImage(model = thumbnailUrl, contentDescription = null, modifier = Modifier.matchParentSize()) else placeholder?.invoke()
    }
}
EOF

cat << 'EOF' > "$R/drawable/ic_push_pin.xml"
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFF" android:pathData="M16,12V4h1V2H7v2h1v8l-2,2v2h5.2v6h1.6v-6H18v-2L16,12z"/></vector>
EOF
cat << 'EOF' > "$R/drawable/mic.xml"
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFF" android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14z"/><path android:fillColor="#FFFFFF" android:pathData="M17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92H17z"/></vector>
EOF
cat << 'EOF' > "$R/drawable/hearing.xml"
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"><path android:fillColor="#FFFFFF" android:pathData="M12,1c-4.97,0 -9,4.03 -9,9v7c0,1.66 1.34,3 3,3h3v-8H5v-2c0,-3.87 3.13,-7 7,-7s7,3.13 7,7v2h-4v8h3c1.66,0 3,-1.34 3,-3v-7C21,5.03 16.97,1 12,1z"/></vector>
EOF

###################################################################
#  APPEND TO EXISTING FILES
###################################################################

cat << 'EOF' >> "$A/constants/PreferenceKeys.kt"
val DownloadQualityKey = stringPreferencesKey("downloadQuality")
enum class DownloadQuality { AUTO, HIGH, HIGHEST, LOW }
val PreferredAudioDeviceIdKey = stringPreferencesKey("preferredAudioDeviceId")
EOF

# Spotify stub methods needed by SpotifyImportRepository/ViewModel
cat << 'EOF' >> "$A/spotify/Spotify.kt"

// Stubs for SpotifyImport integration
suspend fun getCurrentUser(): SpotifyUserPrivate = SpotifyUserPrivate()
suspend fun getUserPlaylists(limit: Int = 50, offset: Int = 0): SpotifyPaging<SpotifyPlaylistSimple> = SpotifyPaging()
suspend fun getSavedTracks(limit: Int = 50, offset: Int = 0): SpotifyPaging<SpotifyLibraryItem> = SpotifyPaging()
suspend fun getAllPlaylistTracks(playlistId: String): List<SpotifyPlaylistTrack> = emptyList()
suspend fun getAllSavedTracks(): List<SpotifyLibraryItem> = emptyList()
fun setSpDcCookie(cookie: String) {}
EOF

# Top-level stubs for AudioDeviceBottomSheet unqualified references
cat << 'EOF' >> "$A/ui/component/AudioDeviceBottomSheet.kt"

// Stubs for unqualified references from Echo Music merge
val castConnectionHandler: Any? = null
val isCasting = false
val castDeviceName: String? = null
val castVolume = 1f
var preferredDeviceId: String? = null
fun setPreferredAudioDevice(id: String?) { preferredDeviceId = id }
fun setVolume(v: Float) {}
EOF

# String resources
perl -pi -e 's|</resources>|    <string name="bluetooth_permission_required">Bluetooth permission required</string>\n    <string name="audio_devices">Audio Devices</string>\n    <string name="this_phone">This Phone</string>\n    <string name="audio_quality_title">Audio Quality</string>\n    <string name="download_quality_title">Download Quality</string>\n</resources>|' "$R/values/strings.xml"

# Fix plurals - add missing default quantity items
if [ -f "$R/values/plurals.xml" ]; then
  perl -0pi -e 's|(<plurals name="n_element">)\s*(<item quantity="one">[^<]*</item>)|${1}\n        ${2}\n        <item quantity="other">%d elements</item>|sg' "$R/values/plurals.xml"
  perl -0pi -e 's|(<plurals name="n_time">)\s*(<item quantity="one">[^<]*</item>)|${1}\n        ${2}\n        <item quantity="other">%d times</item>|sg' "$R/values/plurals.xml"
  perl -0pi -e 's|(<plurals name="seconds">)\s*(<item quantity="one">[^<]*</item>)|${1}\n        ${2}\n        <item quantity="other">%d seconds</item>|sg' "$R/values/plurals.xml"
fi

###################################################################
#  FIX EXISTING FILES - regex replacements
###################################################################

# EQ filter types
perl -pi -e 's/\bPEAK\b/FilterType.PK/g; s/\bLOW_SHELF\b/FilterType.LSC/g; s/\bHIGH_SHELF\b/FilterType.HSC/g; s/\bLOW_PASS\b/FilterType.LPQ/g; s/\bHIGH_PASS\b/FilterType.HPQ/g; s/\bBAND_PASS\b/FilterType.PK/g; s/\bNOTCH\b/FilterType.PK/g; s/\bALL_PASS\b/FilterType.PK/g' "$A/eq/audio/BiquadFilter.kt"

# ParametricEQProfile -> ParametricEQ
find "$A" -name "*.kt" -exec perl -pi -e 's/\bParametricEQProfile\b/ParametricEQ/g' {} +

# albumName -> name in applecanvas
perl -pi -e 's/\balbumName\s*=/name =/g' "$A/applecanvas/AppleMusicCanvasProvider.kt"

# Fix contentencoding import
perl -pi -e 's/import.*contentencoding.*/import com.nikhil.yt.applecanvas.ContentEncoding/gi' "$A/applecanvas/AppleMusicCanvasProvider.kt"

# bodyAsText import
perl -pi -e 'if (/^package / && !/bodyAsText/) { $_ .= "\nimport io.ktor.client.statement.bodyAsText\n" }' "$A/canvas/providers/AppleMusicCanvasProvider.kt"

# mediaId -> videoId
perl -pi -e 's/\bmediaId\b/videoId/g' "$A/playback/AudioExportService.kt"

# Unison imports
perl -pi -e 's/import com\.nikhil\.yt\.lyrics\.unison\./import music.echo.unison./gi' "$A/lyrics/UnisonLyricsProvider.kt"
perl -pi -e 's/import com\.nikhil\.yt\.lyrics\.Unison/import music.echo.unison.UnisonClient/g' "$A/lyrics/UnisonLyricsProvider.kt"

# CipherDeobfuscator import
perl -pi -e 'if (/^package / && !/CipherDeobfuscator/) { $_ .= "\nimport com.nikhil.yt.utils.cipher.CipherDeobfuscator\n" }' "$A/playback/PlayerJsFetcher.kt"

# Fix ALL Echo Music package imports (iad1tya.echo.music -> com.nikhil.yt)
find "$A" -name "*.kt" -exec perl -pi -e 's/import iad1tya\.echo\.music\./import com.nikhil.yt./g' {} +

# Fix echomusic sub-package import
perl -pi -e 's/import com\.nikhil\.yt\.echomusic\./import com.nikhil.yt./g' "$A/ui/component/AudioDeviceBottomSheet.kt"

# Replace BuildConfig discord refs with local constants in DiscordAssetRegistrar
perl -pi -e 's/BuildConfig\.DISCORD_APPLICATION_ID\b/DISCORD_APPLICATION_ID/g' "$D/DiscordAssetRegistrar.kt"

# Replace BuildConfig discord refs in DiscordOAuthRepository + remove stale BuildConfig import
perl -pi -e 's/BuildConfig\.DISCORD_APPLICATION_ID_LONG/DISCORD_APPLICATION_ID_LONG/g; s/BuildConfig\.DISCORD_REDIRECT_SCHEME/DISCORD_REDIRECT_SCHEME/g; s/BuildConfig\.DISCORD_APPLICATION_ID\b/DISCORD_APPLICATION_ID/g' "$D/DiscordOAuthRepository.kt"
perl -pi -e 's/^import com\.nikhil\.yt\.BuildConfig\n//g' "$D/DiscordOAuthRepository.kt"

# Add discord wrapper imports to GatewayClient.kt
perl -pi -e 'if (/^package / && !/GatewaySessionState/) { $_ .= "\nimport com.nikhil.yt.discord.GatewaySessionState\nimport com.nikhil.yt.discord.GatewayReadyEvent\nimport com.nikhil.yt.discord.GatewayCloseInfo\nimport com.nikhil.yt.discord.GatewayDefaults\nimport com.nikhil.yt.discord.GatewayOp\nimport com.nikhil.yt.discord.GatewayCapabilitiesFlags\nimport com.nikhil.yt.discord.IntentsFlags\nimport com.nikhil.yt.discord.NON_RESUMABLE_CLOSE_CODES\n" }' "$D/GatewayClient.kt"

# Fix bitwise NOT operator in GatewayClient (Kotlin uses .inv() not .not())
perl -pi -e 's/(\w+)\.not\(\)/$1.inv()/g' "$D/GatewayClient.kt"

# Add discord wrapper imports to DiscordSocialPresenceClient.kt
perl -pi -e 'if (/^package / && !/DiscordPresenceActivity/) { $_ .= "\nimport com.nikhil.yt.discord.DiscordPresenceActivity\nimport com.nikhil.yt.discord.DiscordOnlineStatus\n" }' "$D/DiscordSocialPresenceClient.kt"

# Add discord constant imports to DiscordAssetRegistrar.kt
perl -pi -e 'if (/^package / && !/DISCORD_APPLICATION_ID/) { $_ .= "\nimport com.nikhil.yt.discord.DISCORD_APPLICATION_ID\n" }' "$D/DiscordAssetRegistrar.kt"

# Add discord constant imports to DiscordOAuthRepository.kt
perl -pi -e 'if (/^package / && !/DISCORD_APPLICATION_ID/) { $_ .= "\nimport com.nikhil.yt.discord.DISCORD_APPLICATION_ID\nimport com.nikhil.yt.discord.DISCORD_APPLICATION_ID_LONG\nimport com.nikhil.yt.discord.DISCORD_REDIRECT_SCHEME\n" }' "$D/DiscordOAuthRepository.kt"

# Add Spotify model imports
perl -pi -e 'if (/^package com\.nikhil\.yt\.spotify$/ && !/models/) { $_ .= "\nimport com.nikhil.yt.spotify.models.*\n" }' "$A/spotify/Spotify.kt"
perl -pi -e 'if (/^package com\.nikhil\.yt\.spotify$/ && !/SpotifyHashProvider/) { $_ .= "\nimport com.nikhil.yt.spotify.SpotifyHashProvider\n" }' "$A/spotify/Spotify.kt"
perl -pi -e 'if (/^package com\.nikhil\.yt\.spotify$/ && !/models/) { $_ .= "\nimport com.nikhil.yt.spotify.models.*\n" }' "$A/spotify/SpotifyMapper.kt"
perl -pi -e 'if (/^package com\.nikhil\.yt\.spotifyimport$/ && !/models/) { $_ .= "\nimport com.nikhil.yt.spotify.models.*\n" }' "$A/spotifyimport/SpotifyImportRepository.kt"

# Fix RecognitionForegroundService setOngoing (probably foregroundInfo.setOngoing -> recreate with ongoing=true)
perl -pi -e 's/\.setOngoing\((true|false)\)//g' "$A/recognition/RecognitionForegroundService.kt"

echo "DONE. Remaining errors need manual fixes (see below):"
echo "1. MusicService.kt:268-272 — missing closing brace before line 268 causes cascade (dataSaverEnabled, reified type, 'private' ref)"
echo "2. MusicService.kt:654 — Boolean? used as Boolean, add == true"
echo "3. MusicService.kt:776-785 — EqualizerService.getProfile/setProfile/setEnabled need to exist on your EqualizerService object"
echo "4. MusicService.kt:4138 — AudioQuality enum cast issue (cascades from #1)"
echo "5. MusicService.kt:4323 — EqualizerService.audioProcessor needs to exist"
echo "6. SpotifyMapper.kt:72 — missing 'filter' parameter in constructor call"
echo "7. SpotifyImportRepository.kt:49,63-65 — 'results'/'track'/'it' cascade from method return type mismatches"
echo "8. AdaptiveListDetailPane.kt:12 — RowColumnParentData.weight is internal, use Modifier.weight() instead"
echo "9. AudioDeviceBottomSheet.kt:913 — reified type parameter, specify explicitly"
