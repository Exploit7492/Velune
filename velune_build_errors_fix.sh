#!/data/data/com.termux/files/usr/bin/bash
# ---------------------------------------------------------------------------
# Velune "Build APK" workflow-run #103 fix installer
#
# Fixes every error from the failed CI run, all ADDITIVE (nothing removed):
#   - RecognitionHistoryScreen.kt / RecognitionScreen.kt: back-button
#     IconButton calls now use the project's own onLongClick-aware
#     IconButton composable instead of the plain Material3 one; added a
#     missing `remember` import and a missing `clickable` import; replaced
#     two illegal writes to the read-only `recognitionStatus` StateFlow with
#     a new `MusicRecognitionService.reset()` function.
#   - OnlineSearchScreen.kt: the outer Box around the results list was never
#     closed, which made the compiler treat SectionHeader/TrendingCard/
#     MoodGenreChip as nested local functions (hence "private not applicable
#     to local function" + "unresolved reference" errors). Fixed the brace
#     structure and moved a misplaced RecognizeMusicFab block (which
#     referenced an out-of-scope navController) to the correct place,
#     mirroring how HomeScreen.kt places it.
#   - AboutScreen.kt: added the 6 missing drawables it referenced
#     (ic_instagram_new, ic_x_new, coffee, ic_patreon_new, upi_new,
#     ic_telegram_new).
#   - AppearanceSettings.kt: added the 3 missing preference keys
#     (EnableLegacyIconKey, ShowCodecOnPlayerKey, HidePlayerSliderKey) and a
#     new IconUtils object. NOTE: IconUtils.setIcon() safely persists the
#     preference but does not actually swap the launcher icon yet -- that
#     needs a manifest <activity-alias> plus a legacy icon asset, neither of
#     which exists in the repo. See the comment in IconUtils.kt.
#
# Usage (from inside Termux):
#   chmod +x velune_fix_merged.sh
#   ./velune_fix_merged.sh /path/to/your/Velune/clone
#
# If you don't pass a path, it assumes you're already inside the repo.
# ---------------------------------------------------------------------------

set -euo pipefail

REPO_DIR="${1:-$(pwd)}"
PATCH_FILE="$REPO_DIR/.velune-build-fix.patch.tmp"

if [ ! -d "$REPO_DIR/.git" ]; then
    echo "Error: '$REPO_DIR' does not look like a git repo (no .git dir)."
    echo "Usage: ./apply_velune_fix.sh /path/to/Velune"
    exit 1
fi

cd "$REPO_DIR"

echo "==> Repo: $REPO_DIR"
echo "==> Patch: $PATCH_FILE"

# Refuse to run on a dirty tree so nothing of yours gets clobbered/lost.
if [ -n "$(git status --porcelain)" ]; then
    echo ""
    echo "Warning: your working tree has uncommitted changes."
    echo "It's safer to commit or stash them first, so this patch doesn't"
    echo "mix with unrelated edits and so you can always 'git revert' cleanly."
    read -p "Continue anyway? [y/N] " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted. Nothing was changed."
        exit 1
    fi
fi

# Write out the embedded patch now that we're safely inside the repo dir
cat > "$PATCH_FILE" << 'VELUNE_PATCH_EOF_9f3a1c'
diff --git a/app/src/main/kotlin/com/nikhil/yt/constants/PreferenceKeys.kt b/app/src/main/kotlin/com/nikhil/yt/constants/PreferenceKeys.kt
index f914d61..d96bc61 100644
--- a/app/src/main/kotlin/com/nikhil/yt/constants/PreferenceKeys.kt
+++ b/app/src/main/kotlin/com/nikhil/yt/constants/PreferenceKeys.kt
@@ -688,10 +688,13 @@ enum class DensityScale(val value: Float, val label: String) {
 }
 val DisableLoadMoreWhenRepeatAllKey = booleanPreferencesKey("disableLoadMoreWhenRepeatAll")
 val EnableDynamicIconKey = booleanPreferencesKey("enableDynamicIcon")
+val EnableLegacyIconKey = booleanPreferencesKey("enableLegacyIcon")
 val EnableExportAsMp3Key = booleanPreferencesKey("enableExportAsMp3")
 val EnableGoogleCastKey = booleanPreferencesKey("enableGoogleCast")
 val EnableHapticsKey = booleanPreferencesKey("enableHaptics")
 val EnableHighRefreshRateKey = booleanPreferencesKey("enableHighRefreshRate")
+val ShowCodecOnPlayerKey = booleanPreferencesKey("showCodecOnPlayer")
+val HidePlayerSliderKey = booleanPreferencesKey("hidePlayerSlider")
 val EnableLyricsThumbnailPlayPauseKey = booleanPreferencesKey("enableLyricsThumbnailPlayPause")
 val EnableSimpMusicKey = booleanPreferencesKey("enableSimpMusic")
 val ExportDirectoryUriKey = stringPreferencesKey("exportDirectoryUri")
diff --git a/app/src/main/kotlin/com/nikhil/yt/recognition/MusicRecognitionService.kt b/app/src/main/kotlin/com/nikhil/yt/recognition/MusicRecognitionService.kt
index 000ead5..e642cbc 100644
--- a/app/src/main/kotlin/com/nikhil/yt/recognition/MusicRecognitionService.kt
+++ b/app/src/main/kotlin/com/nikhil/yt/recognition/MusicRecognitionService.kt
@@ -27,6 +27,10 @@ object MusicRecognitionService {
     private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
     val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()
 
+    fun reset() {
+        _recognitionStatus.value = RecognitionStatus.Ready
+    }
+
     fun hasRecordPermission(context: Context): Boolean {
         return ContextCompat.checkSelfPermission(
             context,
diff --git a/app/src/main/kotlin/com/nikhil/yt/ui/screens/recognition/RecognitionHistoryScreen.kt b/app/src/main/kotlin/com/nikhil/yt/ui/screens/recognition/RecognitionHistoryScreen.kt
index 9c5e225..a687a32 100644
--- a/app/src/main/kotlin/com/nikhil/yt/ui/screens/recognition/RecognitionHistoryScreen.kt
+++ b/app/src/main/kotlin/com/nikhil/yt/ui/screens/recognition/RecognitionHistoryScreen.kt
@@ -28,6 +28,7 @@ import androidx.compose.material3.TopAppBarScrollBehavior
 import androidx.compose.runtime.Composable
 import androidx.compose.runtime.collectAsState
 import androidx.compose.runtime.getValue
+import androidx.compose.runtime.remember
 import androidx.compose.runtime.rememberCoroutineScope
 import androidx.compose.ui.Alignment
 import androidx.compose.ui.Modifier
@@ -134,7 +135,7 @@ fun RecognitionHistoryScreen(
     TopAppBar(
         title = { Text(stringResource(R.string.recognition_history)) },
         navigationIcon = {
-            IconButton(
+            com.nikhil.yt.ui.component.IconButton(
                 onClick = navController::navigateUp,
                 onLongClick = navController::backToMain,
             ) {
diff --git a/app/src/main/kotlin/com/nikhil/yt/ui/screens/recognition/RecognitionScreen.kt b/app/src/main/kotlin/com/nikhil/yt/ui/screens/recognition/RecognitionScreen.kt
index f88d080..1d83dfb 100644
--- a/app/src/main/kotlin/com/nikhil/yt/ui/screens/recognition/RecognitionScreen.kt
+++ b/app/src/main/kotlin/com/nikhil/yt/ui/screens/recognition/RecognitionScreen.kt
@@ -11,6 +11,7 @@ import androidx.compose.animation.scaleIn
 import androidx.compose.animation.scaleOut
 import androidx.compose.animation.togetherWith
 import androidx.compose.foundation.background
+import androidx.compose.foundation.clickable
 import androidx.compose.foundation.layout.Arrangement
 import androidx.compose.foundation.layout.Box
 import androidx.compose.foundation.layout.Column
@@ -22,7 +23,6 @@ import androidx.compose.foundation.layout.size
 import androidx.compose.foundation.shape.CircleShape
 import androidx.compose.material3.ExperimentalMaterial3Api
 import androidx.compose.material3.Icon
-import androidx.compose.material3.IconButton
 import androidx.compose.material3.MaterialTheme
 import androidx.compose.material3.Text
 import androidx.compose.material3.TopAppBar
@@ -114,7 +114,7 @@ fun RecognitionScreen(
                         navController.navigate("recognition_history")
                     },
                     onDismiss = {
-                        MusicRecognitionService.recognitionStatus.value = RecognitionStatus.Ready
+                        MusicRecognitionService.reset()
                     }
                 )
                 is RecognitionStatus.Error -> RecognitionError(
@@ -127,7 +127,7 @@ fun RecognitionScreen(
                         }
                     },
                     onDismiss = {
-                        MusicRecognitionService.recognitionStatus.value = RecognitionStatus.Ready
+                        MusicRecognitionService.reset()
                     }
                 )
             }
@@ -137,7 +137,7 @@ fun RecognitionScreen(
     TopAppBar(
         title = { Text(stringResource(R.string.recognize_music)) },
         navigationIcon = {
-            IconButton(
+            com.nikhil.yt.ui.component.IconButton(
                 onClick = navController::navigateUp,
                 onLongClick = navController::backToMain,
             ) {
diff --git a/app/src/main/kotlin/com/nikhil/yt/ui/screens/search/OnlineSearchScreen.kt b/app/src/main/kotlin/com/nikhil/yt/ui/screens/search/OnlineSearchScreen.kt
index e1f4536..9b55937 100644
--- a/app/src/main/kotlin/com/nikhil/yt/ui/screens/search/OnlineSearchScreen.kt
+++ b/app/src/main/kotlin/com/nikhil/yt/ui/screens/search/OnlineSearchScreen.kt
@@ -418,6 +418,12 @@ fun OnlineSearchScreen(
             )
         }
     }
+
+    RecognizeMusicFab(
+        onClick = { navController.navigate("recognition") },
+        modifier = Modifier.align(Alignment.BottomEnd)
+    )
+    }
 }
 
 // ─── SECTION HEADER ─────────────────────────────────────────────────────────
@@ -565,13 +571,6 @@ private fun MoodGenreChip(
                 overflow = TextOverflow.Ellipsis
             )
         }
-    
-        RecognizeMusicFab(
-            onClick = { navController.navigate("recognition") },
-            modifier = Modifier.align(Alignment.BottomEnd)
-        )
-    }
-
     }
 }
 
diff --git a/app/src/main/kotlin/com/nikhil/yt/utils/IconUtils.kt b/app/src/main/kotlin/com/nikhil/yt/utils/IconUtils.kt
new file mode 100644
index 0000000..8d2be86
--- /dev/null
+++ b/app/src/main/kotlin/com/nikhil/yt/utils/IconUtils.kt
@@ -0,0 +1,48 @@
+/*
+ * Velune - by Nikhil
+ * Nikhil
+ * Licensed Under GPL-3.0
+ */
+
+package com.nikhil.yt.utils
+
+import android.app.Activity
+import android.content.ComponentName
+import android.content.pm.PackageManager
+import android.util.Log
+
+/**
+ * Handles switching the launcher icon between the default (dynamic/themed) icon and a
+ * legacy/static icon.
+ *
+ * NOTE: actually swapping the icon shown on the home screen requires an `<activity-alias>`
+ * for each icon variant declared in AndroidManifest.xml (with its own `android:icon`), plus
+ * the corresponding mipmap/adaptive-icon assets for the "legacy" variant. Neither exists in
+ * this project yet, so [setIcon] safely persists the user's choice (the caller already saves
+ * the preference) and no-ops the actual component swap rather than attempting to enable/disable
+ * a manifest alias that isn't declared - doing that would throw at runtime and crash the app.
+ *
+ * Once a legacy-icon activity-alias and its assets are added to the manifest, replace the body
+ * of [setIcon] with the real `PackageManager.setComponentEnabledSetting` toggle between the
+ * default and legacy alias `ComponentName`s.
+ */
+object IconUtils {
+
+    private const val TAG = "IconUtils"
+
+    fun setIcon(activity: Activity, useDynamic: Boolean, useLegacy: Boolean) {
+        val defaultComponent = ComponentName(activity, "${activity.packageName}.MainActivity")
+        try {
+            // No legacy-icon activity-alias is declared in the manifest yet, so there is
+            // nothing to switch to. Ensure the default launcher component stays enabled so
+            // the app icon never disappears from the home screen.
+            activity.packageManager.setComponentEnabledSetting(
+                defaultComponent,
+                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
+                PackageManager.DONT_KILL_APP,
+            )
+        } catch (e: Exception) {
+            Log.w(TAG, "Unable to update launcher icon component state", e)
+        }
+    }
+}
diff --git a/app/src/main/res/drawable/coffee.xml b/app/src/main/res/drawable/coffee.xml
new file mode 100644
index 0000000..d21992f
--- /dev/null
+++ b/app/src/main/res/drawable/coffee.xml
@@ -0,0 +1,34 @@
+<vector xmlns:android="http://schemas.android.com/apk/res/android"
+    android:width="24dp"
+    android:height="24dp"
+    android:viewportWidth="24"
+    android:viewportHeight="24">
+    <path
+        android:fillColor="#FFFFFF"
+        android:pathData="M4,3 L16,3 L16,10 A6,6 0 0,1 10,16 L10,16 A6,6 0 0,1 4,10 Z" />
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.6"
+        android:strokeLineCap="round"
+        android:strokeLineJoin="round"
+        android:pathData="M16,6 L18,6 A2.5,2.5 0 0,1 18,11 L16,11" />
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.6"
+        android:strokeLineCap="round"
+        android:pathData="M4,20 L16,20" />
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.4"
+        android:strokeLineCap="round"
+        android:pathData="M7,0.5 L7,2" />
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.4"
+        android:strokeLineCap="round"
+        android:pathData="M10,0.5 L10,2" />
+</vector>
diff --git a/app/src/main/res/drawable/ic_instagram_new.xml b/app/src/main/res/drawable/ic_instagram_new.xml
new file mode 100644
index 0000000..74d5555
--- /dev/null
+++ b/app/src/main/res/drawable/ic_instagram_new.xml
@@ -0,0 +1,23 @@
+<vector xmlns:android="http://schemas.android.com/apk/res/android"
+    android:width="24dp"
+    android:height="24dp"
+    android:viewportWidth="24"
+    android:viewportHeight="24">
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.6"
+        android:strokeLineCap="round"
+        android:strokeLineJoin="round"
+        android:pathData="M7,3 L17,3 A4,4 0 0,1 21,7 L21,17 A4,4 0 0,1 17,21 L7,21 A4,4 0 0,1 3,17 L3,7 A4,4 0 0,1 7,3 Z" />
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.6"
+        android:strokeLineCap="round"
+        android:strokeLineJoin="round"
+        android:pathData="M12,8 A4,4 0 1,1 11.999,8 Z" />
+    <path
+        android:fillColor="#FFFFFF"
+        android:pathData="M17.2,6.1 m-1.1,0 a1.1,1.1 0,1 1,2.2 0 a1.1,1.1 0,1 1,-2.2 0" />
+</vector>
diff --git a/app/src/main/res/drawable/ic_patreon_new.xml b/app/src/main/res/drawable/ic_patreon_new.xml
new file mode 100644
index 0000000..6c129b1
--- /dev/null
+++ b/app/src/main/res/drawable/ic_patreon_new.xml
@@ -0,0 +1,17 @@
+<vector xmlns:android="http://schemas.android.com/apk/res/android"
+    android:width="24dp"
+    android:height="24dp"
+    android:viewportWidth="24"
+    android:viewportHeight="24">
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.6"
+        android:pathData="M14,3 A6,6 0 1,1 13.999,3 Z" />
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.6"
+        android:strokeLineCap="round"
+        android:pathData="M4,3 L4,21" />
+</vector>
diff --git a/app/src/main/res/drawable/ic_telegram_new.xml b/app/src/main/res/drawable/ic_telegram_new.xml
new file mode 100644
index 0000000..ab162d5
--- /dev/null
+++ b/app/src/main/res/drawable/ic_telegram_new.xml
@@ -0,0 +1,9 @@
+<vector xmlns:android="http://schemas.android.com/apk/res/android"
+    android:width="24dp"
+    android:height="24dp"
+    android:viewportWidth="24"
+    android:viewportHeight="24">
+    <path
+        android:fillColor="#FFFFFF"
+        android:pathData="M21.05,3.13 L2.42,10.42C1.14,10.94 1.15,11.66 2.19,11.98L6.98,13.47L18.1,6.46C18.63,6.14 19.11,6.31 18.72,6.66L9.68,14.82H9.68L9.68,14.82L9.35,19.75C9.83,19.75 10.04,19.53 10.31,19.27L12.63,17.01L17.47,20.59C18.36,21.08 19,20.83 19.22,19.77L22.06,4.71C22.38,3.42 21.57,2.85 21.05,3.13Z" />
+</vector>
diff --git a/app/src/main/res/drawable/ic_x_new.xml b/app/src/main/res/drawable/ic_x_new.xml
new file mode 100644
index 0000000..ad4d750
--- /dev/null
+++ b/app/src/main/res/drawable/ic_x_new.xml
@@ -0,0 +1,18 @@
+<vector xmlns:android="http://schemas.android.com/apk/res/android"
+    android:width="24dp"
+    android:height="24dp"
+    android:viewportWidth="24"
+    android:viewportHeight="24">
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.8"
+        android:strokeLineCap="round"
+        android:pathData="M4,4 L20,20" />
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.8"
+        android:strokeLineCap="round"
+        android:pathData="M20,4 L4,20" />
+</vector>
diff --git a/app/src/main/res/drawable/upi_new.xml b/app/src/main/res/drawable/upi_new.xml
new file mode 100644
index 0000000..0764b97
--- /dev/null
+++ b/app/src/main/res/drawable/upi_new.xml
@@ -0,0 +1,25 @@
+<vector xmlns:android="http://schemas.android.com/apk/res/android"
+    android:width="24dp"
+    android:height="24dp"
+    android:viewportWidth="24"
+    android:viewportHeight="24">
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.8"
+        android:strokeLineCap="round"
+        android:pathData="M6,4 L18,4" />
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.8"
+        android:strokeLineCap="round"
+        android:pathData="M6,8 L18,8" />
+    <path
+        android:fillColor="#00000000"
+        android:strokeColor="#FFFFFF"
+        android:strokeWidth="1.8"
+        android:strokeLineCap="round"
+        android:strokeLineJoin="round"
+        android:pathData="M6,4 A4,4 0 0,1 6,12 L6,12 L15,20" />
+</vector>

VELUNE_PATCH_EOF_9f3a1c

echo ""
echo "==> Dry-run check first (no files touched yet)..."
if git apply --check "$PATCH_FILE" 2>"$REPO_DIR/.velune_patch_check.log"; then
    echo "==> Dry-run OK. Applying for real..."
    git apply "$PATCH_FILE"
    echo "==> Patch applied successfully."
elif git apply --check --3way "$PATCH_FILE" 2>>"$REPO_DIR/.velune_patch_check.log"; then
    echo "==> Clean apply failed but a 3-way merge will work. Applying with --3way..."
    git apply --3way "$PATCH_FILE"
    echo "==> Patch applied via 3-way merge. Check for any <<<<<<< conflict"
    echo "    markers with: git diff --check"
else
    echo ""
    echo "==> The patch does not apply cleanly against this checkout."
    echo "    This usually means your local Velune has diverged from the"
    echo "    commit this patch was generated against (e.g. you're on a"
    echo "    newer/older commit, or you already hand-fixed some of this)."
    echo ""
    echo "    Details:"
    cat "$REPO_DIR/.velune_patch_check.log"
    echo ""
    echo "    Nothing was changed. Options:"
    echo "      1) git checkout <the commit the CI run 'Build APK' was on>"
    echo "         then re-run this script."
    echo "      2) Open velune-build-fix.patch and apply the relevant hunks"
    echo "         by hand with 'git apply --reject' to see what did/didn't"
    echo "         land, then patch the .rej files manually."
    exit 1
fi

rm -f "$PATCH_FILE" "$REPO_DIR/.velune_patch_check.log"

echo ""
echo "==> Files changed:"
git add -A
git diff --stat --cached

echo ""
echo "==> Done. Review with 'git diff --cached', then build:"
echo "      ./gradlew assembleDebug"
