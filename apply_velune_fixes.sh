#!/usr/bin/env bash
# ============================================================================
# Velune — Local Music wiring fixes
# Run from the ROOT of your cloned repo:  bash apply_velune_fixes.sh
#
# Fixes applied:
#   1. AppModule.kt          — remove spliced/duplicated provideLocalMusicRepository
#                               and duplicate import; restore provideDownloadCache's
#                               real body. (Was a hard compile blocker.)
#   2. LocalMusicRepository  — folder-scope filter missing "/" separator, so
#                               "/Music" also matched "/MusicVideos" etc.
#   3. MediaItemExt.kt       — local track duration passed in ms into a field
#                               that's in seconds everywhere else (+ Long->Int
#                               compile error).
#   4. LocalLibraryViewModel — refreshFolders() never rescanned tracks inside
#                               already-watched folders, only rebuilt the album
#                               list. Now also calls scanAllWatchedFolders().
#   5. LocalLibraryViewModel — selectFolder() did a one-shot fetch instead of
#                               collecting the repository's Flow, so rescans
#                               never reached an already-open folder screen.
#                               Added refreshSelectedFolder().
#   6. FolderDetailScreen.kt — "Pull down to refresh" text existed but no
#                               PullToRefreshBox was wired up. Added one.
# ============================================================================
set -euo pipefail

REPO_ROOT="$(pwd)"
APP_MODULE="app/src/main/kotlin/com/nikhil/yt/di/AppModule.kt"
LOCAL_REPO="app/src/main/kotlin/com/nikhil/yt/repository/LocalMusicRepository.kt"
MEDIA_EXT="app/src/main/kotlin/com/nikhil/yt/extensions/MediaItemExt.kt"
VIEWMODEL="app/src/main/kotlin/com/nikhil/yt/viewmodels/LocalLibraryViewModel.kt"
FOLDER_SCREEN="app/src/main/kotlin/com/nikhil/yt/ui/screens/library/FolderDetailScreen.kt"

for f in "$APP_MODULE" "$LOCAL_REPO" "$MEDIA_EXT" "$VIEWMODEL" "$FOLDER_SCREEN"; do
  if [ ! -f "$f" ]; then
    echo "ERROR: expected file not found: $f"
    echo "Run this script from the repo root."
    exit 1
  fi
done

echo "==> Backing up originals to *.orig"
for f in "$APP_MODULE" "$LOCAL_REPO" "$MEDIA_EXT" "$VIEWMODEL" "$FOLDER_SCREEN"; do
  cp "$f" "$f.orig"
done

# ----------------------------------------------------------------------------
# Fix 1: AppModule.kt
# ----------------------------------------------------------------------------
echo "==> [1/6] Fixing AppModule.kt (duplicate import + spliced function bodies)"
python3 - "$APP_MODULE" <<'PYEOF'
import sys, re

path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    content = f.read()

# Remove the duplicate import line (keep first occurrence only)
content = content.replace(
    "import com.nikhil.yt.repository.LocalMusicRepository\n"
    "import com.nikhil.yt.repository.LocalMusicRepository\n",
    "import com.nikhil.yt.repository.LocalMusicRepository\n",
    1,
)

broken = '''    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
    @Singleton
    @Provides
    fun provideLocalMusicRepository(
        @ApplicationContext context: Context,
        database: MusicDatabase,
    ): LocalMusicRepository = LocalMusicRepository(
        context = context,
        localMusicDao = database.localMusicDao,
    )

    @Singleton
    @Provides
    fun provideLocalMusicRepository(
        @ApplicationContext context: Context,
        database: MusicDatabase,
    ): LocalMusicRepository = LocalMusicRepository(
        context = context,
        localMusicDao = database.localMusicDao,
    )

        LazyCache {
            SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), databaseProvider)
        }
}'''

fixed = '''    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
        LazyCache {
            SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), databaseProvider)
        }

    @Singleton
    @Provides
    fun provideLocalMusicRepository(
        @ApplicationContext context: Context,
        database: MusicDatabase,
    ): LocalMusicRepository = LocalMusicRepository(
        context = context,
        localMusicDao = database.localMusicDao,
    )
}'''

if broken not in content:
    print("WARNING: AppModule.kt did not match the expected broken pattern exactly.")
    print("The file may already be fixed, or has diverged. Skipping this edit —")
    print("please check AppModule.kt manually (backup saved as AppModule.kt.orig).")
else:
    content = content.replace(broken, fixed, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("   OK")
PYEOF

# ----------------------------------------------------------------------------
# Fix 2: LocalMusicRepository.kt — folder scope bug
# ----------------------------------------------------------------------------
echo "==> [2/6] Fixing LocalMusicRepository.kt (folder-scope filter)"
if grep -q 'arrayOf("\$folderPath%")' "$LOCAL_REPO"; then
  sed -i 's|arrayOf("\$folderPath%")|arrayOf("$folderPath/%")|' "$LOCAL_REPO"
  echo "   OK"
else
  echo "   WARNING: expected pattern not found — may already be fixed. Check manually."
fi

# ----------------------------------------------------------------------------
# Fix 3: MediaItemExt.kt — duration unit/type bug
# ----------------------------------------------------------------------------
echo "==> [3/6] Fixing MediaItemExt.kt (duration ms->sec, Long->Int)"
python3 - "$MEDIA_EXT" <<'PYEOF'
import sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    content = f.read()

old = "        duration = duration,\n        thumbnailUrl = artworkUri,"
new = "        duration = (duration / 1000L).toInt(),\n        thumbnailUrl = artworkUri,"

if old not in content:
    print("WARNING: expected pattern not found in MediaItemExt.kt — may already be fixed.")
else:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("   OK")
PYEOF

# ----------------------------------------------------------------------------
# Fix 4 & 5: LocalLibraryViewModel.kt
# ----------------------------------------------------------------------------
echo "==> [4/6] Fixing LocalLibraryViewModel.kt (refreshFolders rescans watched tracks)"
python3 - "$VIEWMODEL" <<'PYEOF'
import sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    content = f.read()

old = '''            try {
                repository.refreshAvailableAlbums()
            } catch (e: Exception) {
                Timber.e(e, "refreshFolders failed")
            } finally {'''
new = '''            try {
                repository.refreshAvailableAlbums()
                repository.scanAllWatchedFolders()
            } catch (e: Exception) {
                Timber.e(e, "refreshFolders failed")
            } finally {'''

if old not in content:
    print("WARNING: [4] expected pattern not found — may already be fixed.")
else:
    content = content.replace(old, new, 1)
    print("   [4/6] OK")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
PYEOF

echo "==> [5/6] Fixing LocalLibraryViewModel.kt (reactive folder-track collection + refreshSelectedFolder)"
python3 - "$VIEWMODEL" <<'PYEOF'
import sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    content = f.read()

old_field = '''    private val _folderTracks = MutableStateFlow<List<LocalTrackEntity>>(emptyList())
    val folderTracks: StateFlow<List<LocalTrackEntity>> = _folderTracks.asStateFlow()'''
new_field = '''    private val _folderTracks = MutableStateFlow<List<LocalTrackEntity>>(emptyList())
    val folderTracks: StateFlow<List<LocalTrackEntity>> = _folderTracks.asStateFlow()

    private var folderTracksJob: kotlinx.coroutines.Job? = null'''

if old_field not in content:
    print("WARNING: [5a] field declaration pattern not found — may already be fixed.")
else:
    content = content.replace(old_field, new_field, 1)

old_funcs = '''    fun selectFolder(folder: LocalFolderEntity) {
        _selectedFolder.value = folder
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = repository.getTracksByFolder(folder.id)
            _folderTracks.value = tracks
        }
    }

    fun clearSelectedFolder() {
        _selectedFolder.value = null
        _folderTracks.value = emptyList()
        _searchQuery.value = ""
        _searchActive.value = false
    }'''

new_funcs = '''    fun selectFolder(folder: LocalFolderEntity) {
        _selectedFolder.value = folder
        folderTracksJob?.cancel()
        folderTracksJob = viewModelScope.launch(Dispatchers.IO) {
            repository.tracksByFolderFlow(folder.id).collect { tracks ->
                _folderTracks.value = tracks
            }
        }
    }

    /** Re-scans just the currently open folder (e.g. from a pull-to-refresh gesture). */
    fun refreshSelectedFolder() {
        val folder = _selectedFolder.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _currentScanningFolder.value = folder.id
            try {
                repository.scanFolder(folder.id)
            } catch (e: Exception) {
                Timber.e(e, "refreshSelectedFolder failed")
            } finally {
                _isScanning.value = false
                _currentScanningFolder.value = null
            }
        }
    }

    fun clearSelectedFolder() {
        folderTracksJob?.cancel()
        folderTracksJob = null
        _selectedFolder.value = null
        _folderTracks.value = emptyList()
        _searchQuery.value = ""
        _searchActive.value = false
    }'''

if old_funcs not in content:
    print("WARNING: [5b] selectFolder/clearSelectedFolder pattern not found — may already be fixed.")
else:
    content = content.replace(old_funcs, new_funcs, 1)
    print("   [5/6] OK")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
PYEOF

# ----------------------------------------------------------------------------
# Fix 6: FolderDetailScreen.kt — wire up real pull-to-refresh
# ----------------------------------------------------------------------------
echo "==> [6/6] Fixing FolderDetailScreen.kt (wire up PullToRefreshBox)"
python3 - "$FOLDER_SCREEN" <<'PYEOF'
import sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    content = f.read()

old_import = '''import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text'''
new_import = '''import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox'''

if old_import not in content:
    print("WARNING: [6a] import block pattern not found — may already be fixed.")
else:
    content = content.replace(old_import, new_import, 1)

old_block = '''            // ── Track List ───────────────────────────────────────────────────
            if (isScanning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else if (tracks.isEmpty()) {
                LocalEmptyState(
                    title = if (searchQuery.isNotBlank()) "No matches" else "No tracks",
                    subtitle = if (searchQuery.isNotBlank()) "Try a different search" else "Pull down to refresh",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isPlaying = currentTrackId == track.id,
                            onClick = {
                                val playlist = buildPlaylist(tracks, track)
                                onPlayTrack(track, playlist)
                            },
                        )
                    }
                }
            }
        }'''

new_block = '''            // ── Track List ───────────────────────────────────────────────────
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh = { viewModel.refreshSelectedFolder() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (isScanning) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                } else if (tracks.isEmpty()) {
                    LocalEmptyState(
                        title = if (searchQuery.isNotBlank()) "No matches" else "No tracks",
                        subtitle = if (searchQuery.isNotBlank()) "Try a different search" else "Pull down to refresh",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(tracks, key = { it.id }) { track ->
                            TrackRow(
                                track = track,
                                isPlaying = currentTrackId == track.id,
                                onClick = {
                                    val playlist = buildPlaylist(tracks, track)
                                    onPlayTrack(track, playlist)
                                },
                            )
                        }
                    }
                }
            }
        }'''

if old_block not in content:
    print("WARNING: [6b] track-list block pattern not found — may already be fixed.")
else:
    content = content.replace(old_block, new_block, 1)
    print("   [6/6] OK")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
PYEOF

# ----------------------------------------------------------------------------
# Verification: brace/paren balance + no leftover duplicate declarations
# ----------------------------------------------------------------------------
echo ""
echo "==> Verifying structural integrity of patched files"
python3 - "$APP_MODULE" "$LOCAL_REPO" "$MEDIA_EXT" "$VIEWMODEL" "$FOLDER_SCREEN" <<'PYEOF'
import sys, re
ok = True
for path in sys.argv[1:]:
    c = open(path, encoding="utf-8").read()
    ob, cb = c.count("{"), c.count("}")
    op, cp = c.count("("), c.count(")")
    funcs = re.findall(r'fun\s+(\w+)\s*\(', c)
    dupes = sorted({x for x in funcs if funcs.count(x) > 1})
    imports = re.findall(r'^import .+$', c, re.MULTILINE)
    dupimports = sorted({x for x in imports if imports.count(x) > 1})
    status = "OK"
    if ob != cb or op != cp or dupes or dupimports:
        status = "PROBLEM"
        ok = False
    print(f"  {status:8s} {path}  braces={ob}/{cb} parens={op}/{cp} dup_funcs={dupes} dup_imports={dupimports}")

print("")
if ok:
    print("All patched files look structurally sound.")
else:
    print("One or more files still look off — please review manually (originals saved as *.orig).")
    sys.exit(1)
PYEOF

echo ""
echo "==> Done. Backups saved alongside each file as *.orig — remove them once you've"
echo "    confirmed the build works (e.g. ./gradlew assembleDebug)."
echo ""
echo "Reminder: these are correctness/wiring fixes. There is still no live filesystem"
echo "watcher (FileObserver/ContentObserver) for watched folders — updates now happen"
echo "on pull-to-refresh rather than automatically in the background."
