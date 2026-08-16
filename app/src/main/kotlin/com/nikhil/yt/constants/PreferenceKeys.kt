/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
val CustomThemeColorKey = stringPreferencesKey("customThemeColor")
val RandomThemeOnStartupKey = booleanPreferencesKey("randomThemeOnStartup")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")
val UseSystemFontKey = booleanPreferencesKey("useSystemFont")
val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val SlimNavBarKey = booleanPreferencesKey("slimNavBar")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SliderStyleKey = stringPreferencesKey("sliderStyle")
val SwipeToSongKey = booleanPreferencesKey("SwipeToSong")
val PlayerDesignStyleKey = stringPreferencesKey("playerDesignStyle")
val UseNewLibraryDesignKey = booleanPreferencesKey("useNewLibraryDesign")
val UseNewMiniPlayerDesignKey = booleanPreferencesKey("useNewMiniPlayerDesign")
val HidePlayerThumbnailKey = booleanPreferencesKey("hidePlayerThumbnail")
val VeluneCanvasKey = booleanPreferencesKey("veluneCanvas")
val CanvasProviderKey = stringPreferencesKey("canvasProvider")
val ThumbnailCornerRadiusKey = floatPreferencesKey("thumbnailCornerRadius")
val CropThumbnailToSquareKey = booleanPreferencesKey("cropThumbnailToSquare")
val SeekExtraSeconds = booleanPreferencesKey("seekExtraSeconds")
val DisableBlurKey = booleanPreferencesKey("disableBlur")
val GlassNavigationBarKey = booleanPreferencesKey("glassNavigationBar")
val GlassMiniPlayerKey = booleanPreferencesKey("glassMiniPlayer")

enum class SliderStyle {
    Standard,
    Wavy,
    Thick,
    Circular,
    Simple,
}

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
val AppLanguageKey = stringPreferencesKey("appLanguage")
val ContentLanguageKey = stringPreferencesKey("contentLanguage")
val ContentCountryKey = stringPreferencesKey("contentCountry")
val EnableKugouKey = booleanPreferencesKey("enableKugou")
val EnableLrcLibKey = booleanPreferencesKey("enableLrclib")
val EnableBetterLyricsKey = booleanPreferencesKey("enableBetterLyrics")
val AiProviderKey = stringPreferencesKey("aiProvider")
val AiApiKeyKey = stringPreferencesKey("aiApiKey")
val AiBaseUrlKey = stringPreferencesKey("aiBaseUrl")
val AiModelKey = stringPreferencesKey("aiModel")
val AiTranslateLanguageKey = stringPreferencesKey("aiTranslateLanguage")
val AiTranslateModeKey = stringPreferencesKey("aiTranslateMode")
val AiAutoTranslateKey = booleanPreferencesKey("aiAutoTranslate")
val EnablePaxsenixKey = booleanPreferencesKey("enablePaxsenix")
val EnableYouLyPlusKey = booleanPreferencesKey("enableYouLyPlus")
val EnableUnisonKey = booleanPreferencesKey("enableUnison")
val GlassBlurIntensityKey = floatPreferencesKey("glassBlurIntensity")
val GlassVibrancyEnabledKey = booleanPreferencesKey("glassVibrancyEnabled")
val GlassLensEnabledKey = booleanPreferencesKey("glassLensEnabled")
val EnableSimpMusicLyricsKey = booleanPreferencesKey("enableSimpMusicLyrics")
val ExportingSongIdsKey = stringPreferencesKey("exportingSongIds")
val ExportedSongIdsKey = stringPreferencesKey("exportedSongIds")
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoKey = booleanPreferencesKey("hideVideo")
val DataSaverEnabledKey = booleanPreferencesKey("dataSaverEnabled")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyUrlKey = stringPreferencesKey("proxyUrl")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val StreamBypassProxyKey = booleanPreferencesKey("streamBypassProxy")
val YtmSyncKey = booleanPreferencesKey("ytmSync")
val SelectedYtmPlaylistsKey = stringPreferencesKey("ytm_selected_playlists")

val TogetherDisplayNameKey = stringPreferencesKey("together_display_name")
val TogetherClientIdKey = stringPreferencesKey("together_client_id")
val TogetherDefaultPortKey = intPreferencesKey("together_default_port")
val TogetherAllowGuestsToAddTracksKey = booleanPreferencesKey("together_allow_guests_add_tracks")
val TogetherAllowGuestsToControlPlaybackKey = booleanPreferencesKey("together_allow_guests_control_playback")
val TogetherRequireHostApprovalToJoinKey = booleanPreferencesKey("together_require_host_approval_to_join")
val TogetherLastJoinLinkKey = stringPreferencesKey("together_last_join_link")
val TogetherWelcomeShownKey = booleanPreferencesKey("together_welcome_shown")
    
// ListenBrainz scrobbling
val ListenBrainzEnabledKey = booleanPreferencesKey("listenbrainz_enabled")
val ListenBrainzTokenKey = stringPreferencesKey("listenbrainz_token")

// Last.fm scrobbling
val LastFMSessionKey = stringPreferencesKey("lastfmSession")
val LastFMUsernameKey = stringPreferencesKey("lastfmUsername")
val EnableLastFMScrobblingKey = booleanPreferencesKey("lastfmScrobblingEnable")
val LastFMUseNowPlaying = booleanPreferencesKey("lastfmUseNowPlaying")
val ScrobbleDelayPercentKey = floatPreferencesKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intPreferencesKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intPreferencesKey("scrobbleDelaySeconds")

val AudioQualityKey = stringPreferencesKey("audioQuality")

val NetworkMeteredKey = booleanPreferencesKey("networkMetered")

enum class AudioQuality {
    OPUS,
    AUTO,
    HIGH,
    HIGHEST,
    LOW,
}

val PlayerStreamClientKey = stringPreferencesKey("playerStreamClient")

enum class PlayerStreamClient {
    ANDROID_VR,
    WEB_REMIX,
    IOS,
    MOBILE,
    TVHTML5,
    ANDROID_MUSIC,
}

val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PermanentShuffleKey = booleanPreferencesKey("permanentShuffle")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val AudioOffload = booleanPreferencesKey("audioOffload")
val SimilarContent = booleanPreferencesKey("similarContent")
val AudioCrossfadeDurationKey = intPreferencesKey("audioCrossfadeDuration")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val PauseOnDeviceMuteKey = booleanPreferencesKey("pauseOnDeviceMute")
val AutoStartOnBluetoothKey = booleanPreferencesKey("autoStartOnBluetooth")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val ArtistSeparatorsKey = stringPreferencesKey("artistSeparators")
val PlaylistTagsFilterKey = stringPreferencesKey("playlistTagsFilter")
val ShowHomeCategoryChipsKey = booleanPreferencesKey("showHomeCategoryChips")
val ShowTagsInLibraryKey = booleanPreferencesKey("showTagsInLibrary")

val EqualizerEnabledKey = booleanPreferencesKey("equalizerEnabled")
val EqualizerBandLevelsMbKey = stringPreferencesKey("equalizerBandLevelsMb")
val EqualizerOutputGainEnabledKey = booleanPreferencesKey("equalizerOutputGainEnabled")
val EqualizerOutputGainMbKey = intPreferencesKey("equalizerOutputGainMb")
val EqualizerBassBoostEnabledKey = booleanPreferencesKey("equalizerBassBoostEnabled")
val EqualizerBassBoostStrengthKey = intPreferencesKey("equalizerBassBoostStrength")
val EqualizerVirtualizerEnabledKey = booleanPreferencesKey("equalizerVirtualizerEnabled")
val EqualizerVirtualizerStrengthKey = intPreferencesKey("equalizerVirtualizerStrength")
val ParametricEQEnabledKey = booleanPreferencesKey("parametricEQEnabled")
val EqBalanceKey = floatPreferencesKey("eq_balance")
val EqBassBoostKey = floatPreferencesKey("eq_bass_boost")
val ParametricEQSelectedProfileIdKey = stringPreferencesKey("parametricEQSelectedProfileId")
val EqualizerSelectedProfileIdKey = stringPreferencesKey("equalizerSelectedProfileId")
val EqualizerCustomProfilesJsonKey = stringPreferencesKey("equalizerCustomProfilesJson")

val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val SmartTrimmerKey = booleanPreferencesKey("smartTrimmer")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")
val MaxCanvasCacheSizeKey = intPreferencesKey("maxCanvasCacheSize")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")

val DiscordTokenKey = stringPreferencesKey("discordToken")
val DiscordInfoDismissedKey = booleanPreferencesKey("discordInfoDismissed")
val DiscordUsernameKey = stringPreferencesKey("discordUsername")
val DiscordNameKey = stringPreferencesKey("discordName")
val EnableDiscordRPCKey = booleanPreferencesKey("discordRPCEnable")
// Discord activity customization keys
val DiscordActivityNameKey = stringPreferencesKey("discordActivityName")
val DiscordActivityDetailsKey = stringPreferencesKey("discordActivityDetails")
val DiscordActivityStateKey = stringPreferencesKey("discordActivityState")
// Custom button labels and urls for Discord activity buttons
val DiscordActivityButton1LabelKey = stringPreferencesKey("discordActivityButton1Label")
val DiscordActivityButton1UrlSourceKey = stringPreferencesKey("discordActivityButton1UrlSource")
val DiscordActivityButton1CustomUrlKey = stringPreferencesKey("discordActivityButton1CustomUrl")
val DiscordActivityButton2LabelKey = stringPreferencesKey("discordActivityButton2Label")
val DiscordActivityButton2UrlSourceKey = stringPreferencesKey("discordActivityButton2UrlSource")
val DiscordActivityButton2CustomUrlKey = stringPreferencesKey("discordActivityButton2CustomUrl")
val DiscordActivityButton1EnabledKey = booleanPreferencesKey("discordActivityButton1Enabled")
val DiscordActivityButton2EnabledKey = booleanPreferencesKey("discordActivityButton2Enabled")
val DiscordShowWhenPausedKey = booleanPreferencesKey("discordShowWhenPaused")
// Activity type for Discord presence (PLAYING, STREAMING, LISTENING, WATCHING, COMPETING)
val DiscordActivityTypeKey = stringPreferencesKey("discordActivityType")
val DiscordPresenceIntervalValueKey = intPreferencesKey("discordPresenceIntervalValue")
val DiscordPresenceIntervalUnitKey = stringPreferencesKey("discordPresenceIntervalUnit") // "S", "M", "H"
val DiscordPresenceStatusKey = stringPreferencesKey("discordPresenceStatus") // "ONLINE", "IDLE", "DND", "INVISIBLE"

// Discord image selection keys
// Values for type keys: "thumbnail", "artist", "appicon", "custom"
val DiscordLargeImageTypeKey = stringPreferencesKey("discordLargeImageType")
val DiscordLargeTextSourceKey = stringPreferencesKey("discordLargeTextSource")
val DiscordLargeTextCustomKey = stringPreferencesKey("discordLargeTextCustom")
val DiscordLargeImageCustomUrlKey = stringPreferencesKey("discordLargeImageCustomUrl")
val DiscordSmallImageTypeKey = stringPreferencesKey("discordSmallImageType")
val DiscordSmallImageCustomUrlKey = stringPreferencesKey("discordSmallImageCustomUrl")
// Activity platform (discord client platform) selection
val DiscordActivityPlatformKey = stringPreferencesKey("discordActivityPlatform")

val TranslatorContextsKey = stringPreferencesKey("translatorContexts")
val TranslatorTargetLangKey = stringPreferencesKey("translatorTargetLang")
val EnableTranslatorKey = booleanPreferencesKey("enableTranslator")

val ChipSortTypeKey = stringPreferencesKey("chipSortType")
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val AutoPlaylistSongSortTypeKey = stringPreferencesKey("autoPlaylistSongSortType")
val AutoPlaylistSongSortDescendingKey = booleanPreferencesKey("autoPlaylistSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")
val MixSortTypeKey = stringPreferencesKey("mixSortType")
val MixSortDescendingKey = booleanPreferencesKey("albumSortDescending")

val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")

val LastLikeSongSyncKey = longPreferencesKey("last_like_song_sync")
val LastLibSongSyncKey = longPreferencesKey("last_library_song_sync")
val LastAlbumSyncKey = longPreferencesKey("last_album_sync")
val LastArtistSyncKey = longPreferencesKey("last_artist_sync")
val LastPlaylistSyncKey = longPreferencesKey("last_playlist_sync")

val ArtistViewTypeKey = stringPreferencesKey("artistViewType")
val AlbumViewTypeKey = stringPreferencesKey("albumViewType")
val PlaylistViewTypeKey = stringPreferencesKey("playlistViewType")

val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val QuickPicksKey = stringPreferencesKey("discover")
val PreferredLyricsProviderKey = stringPreferencesKey("lyricsProvider")
val QueueEditLockKey = booleanPreferencesKey("queueEditLock")

val ShowLikedPlaylistKey = booleanPreferencesKey("show_liked_playlist")
val ShowDownloadedPlaylistKey = booleanPreferencesKey("show_downloaded_playlist")
val ShowTopPlaylistKey = booleanPreferencesKey("show_top_playlist")
val ShowCachedPlaylistKey = booleanPreferencesKey("show_cached_playlist")

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle() =
        when (this) {
            LIST -> GRID
            GRID -> LIST
        }
}

enum class SongFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED
}

enum class ArtistFilter {
    LIBRARY,
    LIKED
}

enum class AlbumFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    DOWNLOADED_FULL
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class PlaylistSongSortType {
    CUSTOM,
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class AutoPlaylistSongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class ArtistSongSortType {
    CREATE_DATE,
    NAME,
    PLAY_TIME,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
    CUSTOM,
}

enum class MixSortType {
    CREATE_DATE,
    NAME,
    LAST_UPDATED,
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            DAY ->
                LocalDateTime
                    .now()
                    .minusDays(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            WEEK ->
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH ->
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            YEAR ->
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            ALL_TIME -> 0
        }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
}

enum class PreferredLyricsProvider {
    LRCLIB,
    KUGOU,
    BETTER_LYRICS,
    SIMPMUSIC,
}

enum class PlayerButtonsStyle {
    DEFAULT,
    SECONDARY,
    PRIMARY,
    TERTIARY,
}

enum class PlayerDesignStyle {
    V1,
    V2,
    V3,
    V4,
    V5,
}

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    CUSTOM,
    BLUR,
    COLORING,
    BLUR_GRADIENT,
    GLOW,
    GLOW_ANIMATED,
    APPLE_MUSIC,
    LIVE_MESH,
    LIQUID_GLASS,
}

// Keys for customized background
val PlayerCustomImageUriKey = stringPreferencesKey("playerCustomImageUri")
val PlayerCustomBlurKey = floatPreferencesKey("playerCustomBlur")
val PlayerCustomContrastKey = floatPreferencesKey("playerCustomContrast")
val PlayerCustomBrightnessKey = floatPreferencesKey("playerCustomBrightness")


val LyricsAnimationStyleKey = stringPreferencesKey("lyricsAnimationStyle")
enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
    APPLE_V2,
    echomusic_1,
    LYRICS_V2,
    METRO_LYRICS,
}

val LyricsTextSizeKey = floatPreferencesKey("lyricsTextSize")
val LyricsLineSpacingKey = floatPreferencesKey("lyricsLineSpacing")

val TopSize = stringPreferencesKey("topSize")
val HistoryDuration = floatPreferencesKey("historyDuration")

val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val LyricsTextPositionKey = stringPreferencesKey("lyricsTextPosition")
val LyricsClickKey = booleanPreferencesKey("lyricsClick")
val LyricsScrollKey = booleanPreferencesKey("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val LyricsRomanizeAsMainKey = booleanPreferencesKey("lyricsRomanizeAsMain")
val LyricsRomanizeChineseKey = booleanPreferencesKey("lyricsRomanizeChinese")
val LyricsRomanizeHindiKey = booleanPreferencesKey("lyricsRomanizeHindi")
val LyricsRomanizePunjabiKey = booleanPreferencesKey("lyricsRomanizePunjabi")
val LyricsRomanizeRussianKey = booleanPreferencesKey("lyricsRomanizeRussian")
val LyricsRomanizeUkrainianKey = booleanPreferencesKey("lyricsRomanizeUkrainian")
val LyricsRomanizeSerbianKey = booleanPreferencesKey("lyricsRomanizeSerbian")
val LyricsRomanizeBulgarianKey = booleanPreferencesKey("lyricsRomanizeBulgarian")
val LyricsRomanizeBelarusianKey = booleanPreferencesKey("lyricsRomanizeBelarusian")
val LyricsRomanizeKyrgyzKey = booleanPreferencesKey("lyricsRomanizeKyrgyz")
val LyricsRomanizeMacedonianKey = booleanPreferencesKey("lyricsRomanizeMacedonian")
val LyricsRomanizeCyrillicByLineKey = booleanPreferencesKey("lyricsRomanizeCyrillicByLine")
val TranslateLyricsKey = booleanPreferencesKey("translateLyrics")
val UseLyricsV2Key = booleanPreferencesKey("useLyricsV2")

// Queue lyrics pre-load settings
val PreloadQueueLyricsEnabledKey = booleanPreferencesKey("preload_queue_lyrics_enabled")
val QueueLyricsPreloadCountKey = intPreferencesKey("queue_lyrics_preload_count")

val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")

val SearchSourceKey = stringPreferencesKey("searchSource")
val SwipeThumbnailKey = booleanPreferencesKey("swipeThumbnail")
val SwipeSensitivityKey = floatPreferencesKey("swipeSensitivity")

enum class SearchSource {
    LOCAL,
    ONLINE,
    ;

    fun toggle() =
        when (this) {
            LOCAL -> ONLINE
            ONLINE -> LOCAL
        }
}

val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")
val PoTokenKey = stringPreferencesKey("poToken")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")

val WebClientPoTokenEnabledKey = booleanPreferencesKey("webClientPoTokenEnabled")
val PoTokenGvsKey = stringPreferencesKey("poTokenGvs")
val PoTokenPlayerKey = stringPreferencesKey("poTokenPlayer")
val UseVisitorDataKey = booleanPreferencesKey("useVisitorData")
val PoTokenSourceUrlKey = stringPreferencesKey("poTokenSourceUrl")

val LanguageCodeToName =
    mapOf(
        "en" to "English (US)",
        "en-GB" to "English (UK)",
        "ja" to "日本語",
        "ko" to "한국어",
        "vi" to "Tiếng Việt",
        "zh" to "中文",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "pt" to "Português",
        "pt-BR" to "Português (Brasil)",
        "ru" to "Русский",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "tr" to "Türkçe",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "th" to "ไทย",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Melayu",
        "uk" to "Українська",
        "cs" to "Čeština",
        "el" to "Ελληνικά",
        "he" to "עברית",
        "hu" to "Magyar",
        "ro" to "Română",
        "fi" to "Suomi",
        "da" to "Dansk",
        "no" to "Norsk",
        "sv" to "Svenska",
        "sk" to "Slovenčina",
        "bg" to "Български",
        "hr" to "Hrvatski",
        "sr" to "Срpsки",
        "lt" to "Lietuvių",
        "lv" to "Latviešu",
        "et" to "Eesti",
    )

val CountryCodeToName =
    mapOf(
        "JP" to "Japan",
        "KR" to "South Korea",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CN" to "China",
        "TW" to "Taiwan",
        "HK" to "Hong Kong",
        "FR" to "France",
        "DE" to "Germany",
        "ES" to "Spain",
        "MX" to "Mexico",
        "BR" to "Brazil",
        "RU" to "Russia",
        "IT" to "Italy",
        "NL" to "Netherlands",
        "PL" to "Poland",
        "TR" to "Turkey",
        "AU" to "Australia",
        "CA" to "Canada",
        "IN" to "India",
        "ID" to "Indonesia",
        "TH" to "Thailand",
        "VN" to "Vietnam",
        "PH" to "Philippines",
        "MY" to "Malaysia",
        "SG" to "Singapore",
        "AR" to "Argentina",
        "CL" to "Chile",
        "CO" to "Colombia",
        "PE" to "Peru",
        "ZA" to "South Africa",
        "EG" to "Egypt",
        "SA" to "Saudi Arabia",
        "AE" to "United Arab Emirates",
    )

// Update settings
val EnableUpdateNotificationKey = booleanPreferencesKey("enableUpdateNotification")
val UpdateChannelKey = stringPreferencesKey("updateChannel")
val LastUpdateCheckKey = longPreferencesKey("lastUpdateCheck")
val LastNotifiedVersionKey = stringPreferencesKey("lastNotifiedVersion")

val GitHubContributorsEtagKey = stringPreferencesKey("github_contributors_etag")
val GitHubContributorsJsonKey = stringPreferencesKey("github_contributors_json")
val GitHubContributorsLastCheckedAtKey = longPreferencesKey("github_contributors_last_checked_at")

val GitHubReleasesEtagKey = stringPreferencesKey("github_releases_etag")
val GitHubReleasesJsonKey = stringPreferencesKey("github_releases_json")
val GitHubReleasesLastCheckedAtKey = longPreferencesKey("github_releases_last_checked_at")
val GitHubReleasesFingerprintKey = stringPreferencesKey("github_releases_fingerprint")

val TogetherOnlineEndpointCacheKey = stringPreferencesKey("together_online_endpoint_cache")
val TogetherOnlineEndpointLastCheckedAtKey = longPreferencesKey("together_online_endpoint_last_checked_at")

enum class UpdateChannel {
    STABLE,
    NIGHTLY,
}
val AutomixDuckEnabledKey = booleanPreferencesKey("automixDuckEnabled")
val SilenceSkipEnabledKey = booleanPreferencesKey("silenceSkipEnabled")
val BeatAnalysisEnabledKey = booleanPreferencesKey("beatAnalysisEnabled")
val BeatAnalysisSensitivityKey = floatPreferencesKey("beatAnalysisSensitivity")
val SilenceSkipThresholdDbKey = floatPreferencesKey("silenceSkipThresholdDb")
val SleepTimerMinutesKey = intPreferencesKey("sleepTimerMinutes")
val SleepTimerFadeOutKey = booleanPreferencesKey("sleepTimerFadeOut")
val MonoAudioEnabledKey = booleanPreferencesKey("monoAudioEnabled")
val LastAudioDeviceIdKey = stringPreferencesKey("lastAudioDeviceId")
val AxionEqEnabledKey = booleanPreferencesKey("axionEqEnabled")
val AmbientModeEnabledKey = booleanPreferencesKey("ambientModeEnabled")
val FloatingMiniPlayerEnabledKey = booleanPreferencesKey("floatingMiniPlayerEnabled")
val CanvasArtworkInPlayerKey = booleanPreferencesKey("canvasArtworkInPlayer")
val FloatingNavBarEnabledKey = booleanPreferencesKey("floatingNavBarEnabled")
val SponsorBlockEnabledKey = booleanPreferencesKey("sponsorBlockEnabled")
val SponsorBlockCategoriesKey = stringPreferencesKey("sponsorBlockCategories")
val CommentsEnabledKey = booleanPreferencesKey("commentsEnabled")
val AiPlaylistEnabledKey = booleanPreferencesKey("aiPlaylistEnabled")
val AiRecommendationEnabledKey = booleanPreferencesKey("aiRecommendationEnabled")
val CastEnabledKey = booleanPreferencesKey("castEnabled")
val AutoUpdateEnabledKey = booleanPreferencesKey("autoUpdateEnabled")
val SpotifyConnectedKey = booleanPreferencesKey("spotifyConnected")
val SpotifyTokenKey = stringPreferencesKey("spotifyToken")
val RomanizationEnabledKey = booleanPreferencesKey("romanizationEnabled")
val RingtoneTrimStartKey = longPreferencesKey("ringtoneTrimStart")
val RingtoneTrimEndKey = longPreferencesKey("ringtoneTrimEnd")
val DiscordRefreshTokenKey = stringPreferencesKey("discord_refresh_token")
val DiscordTokenExpiresAtKey = longPreferencesKey("discord_token_expires_at")
val DiscordAvatarUrlKey = stringPreferencesKey("discord_avatar_url")
val AiTasteProfileEnabledKey = booleanPreferencesKey("ai_taste_profile_enabled")
val SpeedDialEnabledKey = booleanPreferencesKey("speed_dial_enabled")
val PlaybackLogEnabledKey = booleanPreferencesKey("playback_log_enabled")
val CipherDeobfuscationEnabledKey = booleanPreferencesKey("cipher_deobfuscation_enabled")
val ChunkingDataSourceEnabledKey = booleanPreferencesKey("chunking_data_source_enabled")
val OpenRouterApiKey = stringPreferencesKey("openRouterApiKey")
val OpenRouterBaseUrlKey = stringPreferencesKey("openRouterBaseUrl")
val OpenRouterModelKey = stringPreferencesKey("openRouterModel")
val DeeplApiKey = stringPreferencesKey("deeplApiKey")
val DownloadQualityKey = stringPreferencesKey("downloadQuality")
enum class DownloadQuality { AUTO, HIGH, HIGHEST, LOW, YOUTUBE }
val PreferredAudioDeviceIdKey = stringPreferencesKey("preferredAudioDeviceId")
val AlbumCanvasEnabledKey = booleanPreferencesKey("albumCanvasEnabled")
val AppleMusicLyricsBlurKey = booleanPreferencesKey("appleMusicLyricsBlur")
val AutomixCrossfadeKey = booleanPreferencesKey("automixCrossfade")
val AutomixDebugOverlayKey = booleanPreferencesKey("automixDebugOverlay")
val CanvasThumbnailAnimationKey = booleanPreferencesKey("canvasThumbnailAnimation")
val CropAlbumArtKey = booleanPreferencesKey("cropAlbumArt")
val CrossfadeDurationKey = floatPreferencesKey("crossfadeDuration")
val CrossfadeEnabledKey = booleanPreferencesKey("crossfadeEnabled")
val CrossfadeGaplessKey = booleanPreferencesKey("crossfadeGapless")
val DensityScaleKey = floatPreferencesKey("densityScale")
enum class DensityScale(val value: Float, val label: String) {
    COMPACT(0.85f, "Compact"),
    DEFAULT(1.0f, "Default"),
    COMFORTABLE(1.15f, "Comfortable"),
    LARGE(1.3f, "Large");

    companion object {
        fun fromValue(value: Float): DensityScale =
            entries.minByOrNull { kotlin.math.abs(it.value - value) } ?: DEFAULT
    }
}
val DisableLoadMoreWhenRepeatAllKey = booleanPreferencesKey("disableLoadMoreWhenRepeatAll")
val EnableDynamicIconKey = booleanPreferencesKey("enableDynamicIcon")
val EnableLegacyIconKey = booleanPreferencesKey("enableLegacyIcon")
val EnableExportAsMp3Key = booleanPreferencesKey("enableExportAsMp3")
val EnableGoogleCastKey = booleanPreferencesKey("enableGoogleCast")
val EnableHapticsKey = booleanPreferencesKey("enableHaptics")
val EnableHighRefreshRateKey = booleanPreferencesKey("enableHighRefreshRate")
val ShowCodecOnPlayerKey = booleanPreferencesKey("showCodecOnPlayer")
val HidePlayerSliderKey = booleanPreferencesKey("hidePlayerSlider")
val EnableLyricsThumbnailPlayPauseKey = booleanPreferencesKey("enableLyricsThumbnailPlayPause")
val EnableSimpMusicKey = booleanPreferencesKey("enableSimpMusic")
val ExportDirectoryUriKey = stringPreferencesKey("exportDirectoryUri")
val HideStatusBarOnFullscreenKey = booleanPreferencesKey("hideStatusBarOnFullscreen")
val HideVideoSongsKey = booleanPreferencesKey("hideVideoSongs")
val HideYoutubeShortsKey = booleanPreferencesKey("hideYoutubeShorts")
val IpVersionKey = stringPreferencesKey("ipVersion")
val PauseOnMute = booleanPreferencesKey("pauseOnMute")
val KeepScreenOn = booleanPreferencesKey("keepScreenOn")
val LiquidGlassBlurRadiusKey = floatPreferencesKey("liquidGlassBlurRadius")
val LiquidGlassChromaticAberrationKey = booleanPreferencesKey("liquidGlassChromaticAberration")
val LiquidGlassDepthEffectKey = booleanPreferencesKey("liquidGlassDepthEffect")
val LiquidGlassGlobalEnabledKey = booleanPreferencesKey("liquidGlassGlobalEnabled")
val LiquidGlassLensAmountKey = floatPreferencesKey("liquidGlassLensAmount")
val LiquidGlassLensHeightKey = floatPreferencesKey("liquidGlassLensHeight")
val LiquidGlassMiniPlayerEnabledKey = booleanPreferencesKey("liquidGlassMiniPlayerEnabled")
val LiquidGlassNavBarEnabledKey = booleanPreferencesKey("liquidGlassNavBarEnabled")
val LiquidGlassPlayerEnabledKey = booleanPreferencesKey("liquidGlassPlayerEnabled")
val LiquidGlassSurfaceOpacityKey = floatPreferencesKey("liquidGlassSurfaceOpacity")
val LiquidGlassSurfaceTintColorKey = intPreferencesKey("liquidGlassSurfaceTintColor")
val LiquidGlassTextColorKey = intPreferencesKey("liquidGlassTextColor")
val LiquidGlassVibrancyKey = floatPreferencesKey("liquidGlassVibrancy")
val ListenTogetherInTopBarKey = booleanPreferencesKey("listenTogetherInTopBar")
val LyricsGlowEffectKey = booleanPreferencesKey("lyricsGlowEffect")
val LyricsProviderOrderKey = stringPreferencesKey("lyricsProviderOrder")
val LyricsStandardBlurKey = booleanPreferencesKey("lyricsStandardBlur")
val MiniPlayerBackgroundStyleKey = stringPreferencesKey("miniPlayerBackgroundStyle")
val PersistentShuffleAcrossQueuesKey = booleanPreferencesKey("persistentShuffleAcrossQueues")
val PreloadLyricsEnabledKey = booleanPreferencesKey("preloadLyricsEnabled")
val PreloadNextSongEnabledKey = booleanPreferencesKey("preloadNextSongEnabled")
val PreloadNextSongLimitKey = intPreferencesKey("preloadNextSongLimit")
val PreventDuplicateTracksInQueueKey = booleanPreferencesKey("preventDuplicateTracksInQueue")
val ProxyPasswordKey = stringPreferencesKey("proxyPassword")
val ProxyUsernameKey = stringPreferencesKey("proxyUsername")
val RandomizeHomeOrderKey = booleanPreferencesKey("randomizeHomeOrder")
val RememberShuffleAndRepeatKey = booleanPreferencesKey("rememberShuffleAndRepeat")
val ResumeOnBluetoothConnectKey = booleanPreferencesKey("resumeOnBluetoothConnect")
val RotatingThumbnailKey = booleanPreferencesKey("rotatingThumbnail")
val SelectedThemeColorKey = intPreferencesKey("selectedThemeColor")
val ShowArtistBackgroundVideoKey = booleanPreferencesKey("showArtistBackgroundVideo")
val ShowArtistDescriptionKey = booleanPreferencesKey("showArtistDescription")
val ShowArtistSubscriberCountKey = booleanPreferencesKey("showArtistSubscriberCount")
val ShowArtistVideoKey = booleanPreferencesKey("showArtistVideo")
val ShowCommentButtonKey = booleanPreferencesKey("showCommentButton")
val ShowExportedPlaylistKey = booleanPreferencesKey("showExportedPlaylist")
val ShowMonthlyListenersKey = booleanPreferencesKey("showMonthlyListeners")
val ShowSpeedDialKey = booleanPreferencesKey("showSpeedDial")
val ShowUploadedPlaylistKey = booleanPreferencesKey("showUploadedPlaylist")
val ShufflePlaylistFirstKey = booleanPreferencesKey("shufflePlaylistFirst")
val SkipSilenceInstantKey = booleanPreferencesKey("skipSilenceInstant")
val SquigglySliderKey = booleanPreferencesKey("squigglySlider")
val SuggestionRegionKey = stringPreferencesKey("suggestionRegion")
val SuggestionRegionSlugToName =
    mapOf(
        "system" to "System Default",
        "zeitgeist_global" to "Global Charts",
        "zeitgeist_us" to "United States",
        "zeitgeist_gb" to "United Kingdom",
        "zeitgeist_jp" to "Japan",
        "zeitgeist_kr" to "South Korea",
        "zeitgeist_in" to "India",
        "zeitgeist_br" to "Brazil",
        "zeitgeist_de" to "Germany",
        "zeitgeist_fr" to "France",
    )
val SwipeLyricsKey = booleanPreferencesKey("swipeLyrics")
val SwipeToRemoveSongKey = booleanPreferencesKey("swipeToRemoveSong")
val UseFloatingNavBarKey = booleanPreferencesKey("useFloatingNavBar")
val UseNewPlayerDesignKey = booleanPreferencesKey("useNewPlayerDesign")

// Listen Together (ported from echo-music reference)
val ListenTogetherServerUrlKey = stringPreferencesKey("listenTogetherServerUrl")
val ListenTogetherUsernameKey = stringPreferencesKey("listenTogetherUsername")
val EnableListenTogetherKey = booleanPreferencesKey("enableListenTogether")
val ListenTogetherAutoApprovalKey = booleanPreferencesKey("listenTogetherAutoApproval")
val ListenTogetherSyncVolumeKey = booleanPreferencesKey("listenTogetherSyncVolume")
val ListenTogetherSmartResyncKey = booleanPreferencesKey("listenTogetherSmartResync")
val ListenTogetherBlockedUsersKey = stringPreferencesKey("listenTogetherBlockedUsers")
val ListenTogetherSessionTokenKey = stringPreferencesKey("listenTogetherSessionToken")
val ListenTogetherRoomCodeKey = stringPreferencesKey("listenTogetherRoomCode")
val ListenTogetherUserIdKey = stringPreferencesKey("listenTogetherUserId")
val ListenTogetherIsHostKey = booleanPreferencesKey("listenTogetherIsHost")
val ListenTogetherSessionTimestampKey = longPreferencesKey("listenTogetherSessionTimestamp")

// Dynamic app icon/branding — last-fetched remote config JSON (see
// com.nikhil.yt.branding.AppIconRepository), cached so the admin-uploaded
// icons/logos survive restarts without re-fetching before first paint.
val AppIconConfigJsonKey = stringPreferencesKey("appIconConfigJson")
val AppIconConfigFetchedAtKey = longPreferencesKey("appIconConfigFetchedAt")

// EQ Advanced tab — per-band Q (bandwidth/quality), independent of gain.
// Fixed at 1.41 for every band until now; stored the same per-band way
// EqBandGain_$it already is. See AxionEqViewModel.setBandQ.
val EqBandQPrefix = "eq_band_q_"

// EQ Master tab — limiter and stereo width, additive to the existing
// Balance/Bass Boost master-bus controls (see EqBalanceKey/EqBassBoostKey
// above). Distinct responsibilities, so none of these duplicate each other:
// Balance shifts L/R volume, Bass Boost shelves low frequencies, Stereo
// Width narrows/widens the stereo image via mid/side processing, and the
// Limiter caps final output level after everything else (preamp, per-band
// gain, bass boost, balance, width) has already been applied.
val EqLimiterEnabledKey = booleanPreferencesKey("eq_limiter_enabled")
val EqLimiterCeilingKey = floatPreferencesKey("eq_limiter_ceiling_db")
val EqStereoWidthKey = floatPreferencesKey("eq_stereo_width")

// EQ Master tab — convolution-based tone shaping (impulse response
// correction, see ConvolutionAudioProcessor/EqualizerService). The picked
// IR is copied into app-private storage at import time rather than kept as
// a content:// Uri — the same pattern Wavelet/Neutron use, since a
// scoped-storage Uri's permission can be revoked later and the DSP layer
// needs a stable file it can reopen on every processor recreation, not a
// one-shot stream. These keys persist that copy's path plus the original
// picked file name so the UI survives process death without re-importing.
val EqConvolutionEnabledKey = booleanPreferencesKey("eq_convolution_enabled")
val EqConvolutionIrPathKey = stringPreferencesKey("eq_convolution_ir_path")
val EqConvolutionIrNameKey = stringPreferencesKey("eq_convolution_ir_name")
