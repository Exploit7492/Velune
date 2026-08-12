/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt

import com.nikhil.yt.ui.component.FluidSlidingNavigationBar
import android.annotation.SuppressLint
import android.Manifest
import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.nikhil.yt.utils.PreferenceStore
import kotlinx.coroutines.withContext
import com.nikhil.yt.constants.AppBarHeight
import com.nikhil.yt.constants.AppLanguageKey
import com.nikhil.yt.constants.CustomThemeColorKey
import com.nikhil.yt.constants.DarkModeKey
import com.nikhil.yt.constants.DefaultOpenTabKey
import com.nikhil.yt.constants.DisableScreenshotKey
import com.nikhil.yt.constants.DynamicThemeKey
import com.nikhil.yt.constants.HasPressedStarKey
import com.nikhil.yt.constants.LaunchCountKey
import com.nikhil.yt.constants.MiniPlayerBottomSpacing
import com.nikhil.yt.constants.MiniPlayerHeight
import com.nikhil.yt.constants.NavigationBarAnimationSpec
import com.nikhil.yt.constants.NavigationBarHeight
import com.nikhil.yt.constants.PauseSearchHistoryKey
import com.nikhil.yt.constants.PureBlackKey
import com.nikhil.yt.constants.RemindAfterKey
import com.nikhil.yt.constants.SYSTEM_DEFAULT
import com.nikhil.yt.constants.SearchSource
import com.nikhil.yt.constants.SearchSourceKey
import com.nikhil.yt.constants.SlimNavBarHeight
import com.nikhil.yt.constants.SlimNavBarKey

import com.nikhil.yt.constants.StopMusicOnTaskClearKey
import com.nikhil.yt.constants.UseNewMiniPlayerDesignKey
import com.nikhil.yt.constants.UseSystemFontKey
import com.nikhil.yt.db.MusicDatabase
import com.nikhil.yt.db.entities.SearchHistory
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.extensions.toMediaItem
import com.nikhil.yt.playback.DownloadUtil
import com.nikhil.yt.playback.MusicService
import com.nikhil.yt.playback.MusicService.MusicBinder
import com.nikhil.yt.playback.PlayerConnection
import com.nikhil.yt.playback.queues.ListQueue

import com.nikhil.yt.ui.component.BottomSheetMenu
import com.nikhil.yt.ui.component.BottomSheetPage
import com.nikhil.yt.ui.component.COLLAPSED_ANCHOR
import com.nikhil.yt.ui.component.DISMISSED_ANCHOR
import com.nikhil.yt.ui.component.EXPANDED_ANCHOR
import com.nikhil.yt.ui.component.IconButton

import com.nikhil.yt.ui.component.LocalBottomSheetPageState
import com.nikhil.yt.ui.component.LocalMenuState
import com.nikhil.yt.ui.component.StarDialog
import com.nikhil.yt.ui.component.TopSearch
import com.nikhil.yt.ui.component.rememberBottomSheetState
import com.nikhil.yt.ui.component.shimmer.ShimmerTheme
import com.nikhil.yt.ui.menu.YouTubeSongMenu
import com.nikhil.yt.ui.player.BottomSheetPlayer
import com.nikhil.yt.ui.screens.Screens
import com.nikhil.yt.ui.screens.navigationBuilder
import com.nikhil.yt.ui.screens.search.LocalSearchScreen
import com.nikhil.yt.ui.screens.search.OnlineSearchScreen
import com.nikhil.yt.ui.screens.settings.DarkMode
import com.nikhil.yt.ui.screens.settings.DiscordPresenceManager
import com.nikhil.yt.ui.screens.settings.NavigationTab
import com.nikhil.yt.ui.theme.VeluneTheme
import com.nikhil.yt.ui.theme.ColorSaver
import com.nikhil.yt.ui.theme.DefaultThemeColor
import com.nikhil.yt.ui.theme.extractThemeColor
import com.nikhil.yt.ui.utils.appBarScrollBehavior
import com.nikhil.yt.ui.utils.backToMain
import com.nikhil.yt.ui.utils.resetHeightOffset
import com.nikhil.yt.utils.SyncUtils
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.get
import com.nikhil.yt.utils.rememberEnumPreference
import com.nikhil.yt.utils.rememberPreference
import com.nikhil.yt.utils.reportException
import com.nikhil.yt.utils.setAppLocale
import com.nikhil.yt.viewmodels.HomeViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_RECOGNITION = "com.nikhil.yt.action.RECOGNITION"
        const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
        const val EXTRA_RECOGNITION_RESULT = "recognition_result"
        const val ACTION_SEARCH = "com.nikhil.yt.action.SEARCH"
        const val ACTION_LIBRARY = "com.nikhil.yt.action.LIBRARY"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
