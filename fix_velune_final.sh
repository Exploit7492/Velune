#!/usr/bin/env bash
set -euo pipefail

f_search="app/src/main/kotlin/com/nikhil/yt/ui/screens/search/OnlineSearchScreen.kt"
f_localvm="app/src/main/kotlin/com/nikhil/yt/viewmodels/LocalLibraryViewModel.kt"
f_historyvm="app/src/main/kotlin/com/nikhil/yt/viewmodels/HistoryViewModel.kt"
f_localconst="app/src/main/kotlin/com/nikhil/yt/constants/LocalMusicConstants.kt"
f_prefkeys="app/src/main/kotlin/com/nikhil/yt/constants/PreferenceKeys.kt"
f_repo="app/src/main/kotlin/com/nikhil/yt/repository/LocalMusicRepository.kt"
f_localcontent="app/src/main/kotlin/com/nikhil/yt/ui/screens/library/LocalLibraryContent.kt"
f_navbar="app/src/main/kotlin/com/nikhil/yt/ui/component/FluidSlidingNavigationBar.kt"
f_player="app/src/main/kotlin/com/nikhil/yt/ui/player/Player.kt"
f_playermenu="app/src/main/kotlin/com/nikhil/yt/ui/menu/PlayerMenu.kt"
f_musicservice="app/src/main/kotlin/com/nikhil/yt/playback/MusicService.kt"
f_libscreen="app/src/main/kotlin/com/nikhil/yt/ui/screens/library/LibraryScreen.kt"

for f in "$f_search" "$f_localvm" "$f_historyvm" "$f_localconst" "$f_prefkeys" \
         "$f_repo" "$f_localcontent" "$f_navbar" "$f_player" "$f_playermenu" \
         "$f_musicservice" "$f_libscreen"; do
  [ -f "$f" ] || { echo "ERROR: $f not found — run from repo root."; exit 1; }
  cp "$f" "$f.bak"
done

python3 - "$f_search" "$f_localvm" "$f_historyvm" "$f_localconst" "$f_prefkeys" \
          "$f_repo" "$f_localcontent" "$f_navbar" "$f_player" "$f_playermenu" \
          "$f_musicservice" "$f_libscreen" <<'PYEOF'
import sys

(f_search, f_localvm, f_historyvm, f_localconst, f_prefkeys,
 f_repo, f_localcontent, f_navbar, f_player, f_playermenu,
 f_musicservice, f_libscreen) = sys.argv[1:13]

def load(p):
    with open(p, encoding="utf-8") as f:
        return f.read()

def save(p, c):
    with open(p, "w", encoding="utf-8") as f:
        f.write(c)

def replace_once(content, old, new, label, path):
    if old not in content:
        print(f"  [SKIP] {label} — not found in {path} (already fixed?)")
        return content
    print(f"  [OK]   {label}")
    return content.replace(old, new, 1)

print("1) OnlineSearchScreen.kt — comment eating closing brace")
c = load(f_search)
c = replace_once(
    c,
    '                        onClick = { // navController.navigate("new_releases") // REMOVED: route mismatch },\n',
    '                        onClick = { /* navController.navigate("new_releases") REMOVED: route mismatch */ },\n',
    "fixed dangling onClick lambda", f_search,
)
save(f_search, c)

print("2) LocalMusicConstants.kt — define LastSelectedFolderIdKey (was never defined)")
c = load(f_localconst)
c = replace_once(
    c,
    'val LocalSearchActiveKey = booleanPreferencesKey("local_search_active")\n',
    'val LocalSearchActiveKey = booleanPreferencesKey("local_search_active")\n'
    'val LastSelectedFolderIdKey = stringPreferencesKey("last_selected_folder_id")\n',
    "added LastSelectedFolderIdKey definition", f_localconst,
)
save(f_localconst, c)

print("3) PreferenceKeys.kt — define SeenNotificationIdsKey / NotificationLastFetchKey")
c = load(f_prefkeys)
anchor = 'val ShowCachedPlaylistKey = booleanPreferencesKey("show_cached_playlist")\n'
addition = (
    'val SeenNotificationIdsKey = stringPreferencesKey("seen_notification_ids")\n'
    'val NotificationLastFetchKey = longPreferencesKey("notification_last_fetch")\n'
)
c = replace_once(c, anchor, anchor + addition, "added missing notification keys", f_prefkeys)
save(f_prefkeys, c)

print("4) HistoryViewModel.kt — missing dataStore import + broken readText(timeoutMillis=) call")
c = load(f_historyvm)
c = replace_once(
    c,
    "import androidx.lifecycle.viewModelScope\n",
    "import androidx.lifecycle.viewModelScope\n"
    "import com.nikhil.yt.utils.dataStore\n",
    "added dataStore import", f_historyvm,
)
c = replace_once(
    c,
    '                val url = URL("https://trendgetter.vercel.app/api/youtube/videos?region_code=$region&limit=50")\n'
    '                val response = url.readText(timeoutMillis = 15000)\n',
    '                val url = URL("https://trendgetter.vercel.app/api/youtube/videos?region_code=$region&limit=50")\n'
    '                val connection = url.openConnection().apply {\n'
    '                    connectTimeout = 15000\n'
    '                    readTimeout = 15000\n'
    '                }\n'
    '                val response = connection.getInputStream().bufferedReader().use { it.readText() }\n',
    "fixed readText(timeoutMillis=) — not a real Kotlin API", f_historyvm,
)
save(f_historyvm, c)

print("5) LocalMusicRepository.kt — dao -> localMusicDao typo")
c = load(f_repo)
c = replace_once(
    c,
    "        dao.getFolderById(folderId)\n",
    "        localMusicDao.getFolderById(folderId)\n",
    "fixed dao typo", f_repo,
)
save(f_repo, c)

print("6) LocalLibraryContent.kt — remove dead SortPanel/InlineFilterRow block (wrong API usage)")
c = load(f_localcontent)
c = replace_once(c, 'import com.nikhil.yt.ui.screens.library.components.InlineFilterRow\n', '',
                  "removed InlineFilterRow import", f_localcontent)
c = replace_once(c, 'import com.nikhil.yt.ui.screens.library.components.SortPanel\n', '',
                  "removed SortPanel import", f_localcontent)
old_block = '''    var showSortPanel by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Sort / Filter bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineFilterRow(
                    sorts = emptyList(),
                    onRemove = {},
                    onToggleDir = {},
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showSortPanel = !showSortPanel }) {
                    Icon(
                        imageVector = Icons.Outlined.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            AnimatedVisibility(visible = showSortPanel) {
                SortPanel(
                    availableSorts = emptyList(),
                    selectedSorts = emptyList(),
                    onSortSelected = {},
                    onDismiss = { showSortPanel = false },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = { viewModel.refreshFolders() },
            modifier = Modifier.fillMaxSize().padding(top = 52.dp),
        ) {'''
new_block = '''    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = { viewModel.refreshFolders() },
            modifier = Modifier.fillMaxSize(),
        ) {'''
c = replace_once(c, old_block, new_block, "removed broken sort/filter block", f_localcontent)
save(f_localcontent, c)

print("7) FluidSlidingNavigationBar.kt — missing Badge / FontWeight imports")
c = load(f_navbar)
c = replace_once(c, "import androidx.compose.material3.Icon\n",
                  "import androidx.compose.material3.Badge\nimport androidx.compose.material3.Icon\n",
                  "added Badge import", f_navbar)
c = replace_once(c, "import androidx.compose.ui.text.style.TextOverflow\n",
                  "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextOverflow\n",
                  "added FontWeight import", f_navbar)
save(f_navbar, c)

print("8) Player.kt — invalid not(...) call + missing launch import")
c = load(f_player)
c = replace_once(c, "        val targetVideoMode = not(isVideoMode)\n",
                  "        val targetVideoMode = !isVideoMode\n",
                  "fixed not(isVideoMode) -> !isVideoMode", f_player)
c = replace_once(c, "import kotlinx.coroutines.Dispatchers\n",
                  "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\n",
                  "added launch import", f_player)
save(f_player, c)

print("9) PlayerMenu.kt — missing animation/layout/Canvas imports")
c = load(f_playermenu)
anchor = "import androidx.compose.foundation.layout.Arrangement\n"
addition = (
    "import androidx.compose.animation.core.FastOutSlowInEasing\n"
    "import androidx.compose.animation.core.RepeatMode\n"
    "import androidx.compose.animation.core.infiniteRepeatable\n"
    "import androidx.compose.animation.core.rememberInfiniteTransition\n"
    "import androidx.compose.animation.core.tween\n"
    "import androidx.compose.foundation.Canvas\n"
)
c = replace_once(c, anchor, addition + anchor, "added animation/Canvas imports", f_playermenu)
c = replace_once(c, "import androidx.compose.foundation.layout.height\n",
                  "import androidx.compose.foundation.layout.fillMaxHeight\nimport androidx.compose.foundation.layout.height\n",
                  "added fillMaxHeight import", f_playermenu)
save(f_playermenu, c)

print("10) MusicService.kt — createDataSourceFactory() never returns a value")
c = load(f_musicservice)
old = '''    private fun createDataSourceFactory(): DataSource.Factory {
        val localFactory = DefaultDataSource.Factory(this)
        val onlineFactory = ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->'''
new = '''    private fun createDataSourceFactory(): DataSource.Factory {
        val onlineFactory = ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->'''
c = replace_once(c, old, new, "removed unused localFactory", f_musicservice)

old2 = '''                return@Factory dataSpec.withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, length)
            }
        }
    }

    fun retryCurrentFromFreshStream() {'''
new2 = '''                return@Factory dataSpec.withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, length)
            }
        }
        return onlineFactory
    }

    fun retryCurrentFromFreshStream() {'''
c = replace_once(c, old2, new2, "added missing return statement", f_musicservice)
save(f_musicservice, c)

print("11) LibraryScreen.kt — top padding (cosmetic, optional)")
c = load(f_libscreen)
c = replace_once(
    c,
    "            .fillMaxSize()\n            .pointerInput(libraryMode) {",
    "            .fillMaxSize()\n"
    "            .padding(top = com.nikhil.yt.constants.AppBarHeight + 8.dp)\n"
    "            .pointerInput(libraryMode) {",
    "added top padding", f_libscreen,
)
save(f_libscreen, c)

print("12) LocalLibraryViewModel.kt — extra stray '}' prematurely closed the class,")
print("    and init block referenced properties before they were declared (NPE risk)")
c = load(f_localvm)

c = replace_once(
    c, "import com.nikhil.yt.constants.LibraryMode\n",
    "import com.nikhil.yt.constants.LibraryMode\nimport com.nikhil.yt.constants.LastSelectedFolderIdKey\n",
    "added missing import (skips if already added)", f_localvm,
)

old_broken_init = '''init {
        viewModelScope.launch {
            val savedId = dataStore.data.map { it[LastSelectedFolderIdKey] }.first()
                val folder = repository.getFolderById(savedId)
                if (folder != null) {
                    _selectedFolder.value = folder
                    _folderTracks.value = repository.getTracksByFolder(savedId)
                }
            }
        }
    }


    private val dataStore = application.dataStore'''
new_dataStore_only = '''private val dataStore = application.dataStore'''
c = replace_once(c, old_broken_init, new_dataStore_only,
                  "removed broken init block + extra brace, kept dataStore decl", f_localvm)

anchor2 = '''    private val _isRefreshingFolder = MutableStateFlow(false)
    val isRefreshingFolder: StateFlow<Boolean> = _isRefreshingFolder.asStateFlow()

    // Sorting'''
replacement2 = '''    private val _isRefreshingFolder = MutableStateFlow(false)
    val isRefreshingFolder: StateFlow<Boolean> = _isRefreshingFolder.asStateFlow()

    init {
        viewModelScope.launch {
            val savedId = dataStore.data.map { it[LastSelectedFolderIdKey] }.first()
            val folder = savedId?.let { repository.getFolderById(it) }
            if (folder != null) {
                _selectedFolder.value = folder
                _folderTracks.value = repository.getTracksByFolder(folder.id)
            }
        }
    }

    // Sorting'''
c = replace_once(c, anchor2, replacement2, "inserted corrected init block in the right place", f_localvm)
save(f_localvm, c)

print("\nDone.")
PYEOF

echo ""
echo "==> All fixes applied. Backups saved as *.bak next to each file."
echo "==> Next: ./gradlew :app:compileArm64ReleaseKotlin --console=plain"
