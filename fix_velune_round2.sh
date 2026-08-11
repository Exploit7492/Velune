#!/usr/bin/env bash
set -euo pipefail

f_playermenu="app/src/main/kotlin/com/nikhil/yt/ui/menu/PlayerMenu.kt"
f_player="app/src/main/kotlin/com/nikhil/yt/ui/player/Player.kt"
f_history="app/src/main/kotlin/com/nikhil/yt/ui/screens/HistoryScreen.kt"

for f in "$f_playermenu" "$f_player" "$f_history"; do
  [ -f "$f" ] || { echo "ERROR: $f not found — run from repo root."; exit 1; }
  cp "$f" "$f.bak"
done

python3 - "$f_playermenu" "$f_player" "$f_history" <<'PYEOF'
import sys
f_playermenu, f_player, f_history = sys.argv[1:4]

def load(p):
    with open(p, encoding="utf-8") as f: return f.read()
def save(p, c):
    with open(p, "w", encoding="utf-8") as f: f.write(c)
def rep(c, old, new, label, path):
    if old not in c:
        print(f"  [SKIP] {label} ({path})")
        return c
    print(f"  [OK]   {label}")
    return c.replace(old, new, 1)

print("1) PlayerMenu.kt — missing animateFloat import")
c = load(f_playermenu)
c = rep(c, "import androidx.compose.animation.core.FastOutSlowInEasing\n",
        "import androidx.compose.animation.core.FastOutSlowInEasing\nimport androidx.compose.animation.core.animateFloat\n",
        "added animateFloat import", f_playermenu)
save(f_playermenu, c)

print("2) PlayerMenu.kt — MaterialTheme.colorScheme called inside non-@Composable Canvas draw scope")
c = load(f_playermenu)
old = '''                    val bandLabels = caps.centerFreqHz.map { formatHz(it) }
                    val barCount = bandLevelsMb.size.coerceAtLeast(bandCount)
                    Canvas('''
new = '''                    val bandLabels = caps.centerFreqHz.map { formatHz(it) }
                    val barCount = bandLevelsMb.size.coerceAtLeast(bandCount)
                    val positiveBarColor = MaterialTheme.colorScheme.primary
                    val zeroLineColor = MaterialTheme.colorScheme.outline
                    Canvas('''
c = rep(c, old, new, "captured colors before Canvas draw scope", f_playermenu)
c = rep(c, "                                else -> MaterialTheme.colorScheme.primary\n",
        "                                else -> positiveBarColor\n",
        "replaced in-draw-scope color call (primary)", f_playermenu)
c = rep(c, "                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),\n",
        "                            color = zeroLineColor.copy(alpha = 0.5f),\n",
        "replaced in-draw-scope color call (outline)", f_playermenu)
save(f_playermenu, c)

print("3) Player.kt — toggleVideo lambda infers Function0<Any> instead of Function0<Unit>")
c = load(f_player)
c = rep(c, "    val toggleVideo = {\n",
        "    val toggleVideo: () -> Unit = {\n",
        "gave toggleVideo an explicit () -> Unit type", f_player)
save(f_player, c)

print("4) HistoryScreen.kt — bad TopAppBar import (component doesn't exist; material3.* wildcard already covers it)")
c = load(f_history)
c = rep(c, "import com.nikhil.yt.ui.component.TopAppBar\n", "",
        "removed nonexistent TopAppBar import", f_history)

print("5) HistoryScreen.kt — ContentType/NotificationType are nested in HistoryViewModel, need qualified import")
c = rep(c, "import com.nikhil.yt.viewmodels.HistoryViewModel\n",
        "import com.nikhil.yt.viewmodels.HistoryViewModel\n"
        "import com.nikhil.yt.viewmodels.HistoryViewModel.ContentType\n"
        "import com.nikhil.yt.viewmodels.HistoryViewModel.NotificationType\n",
        "added nested-type imports", f_history)

print("6) HistoryScreen.kt — R.drawable.chevron_right / play_circle don't exist; use Material icons instead")
c = rep(c, "import androidx.hilt.navigation.compose.hiltViewModel\n",
        "import androidx.compose.material.icons.Icons\n"
        "import androidx.compose.material.icons.filled.ChevronRight\n"
        "import androidx.compose.material.icons.filled.PlayCircle\n"
        "import androidx.hilt.navigation.compose.hiltViewModel\n",
        "added Material icon imports", f_history)
c = rep(c, "            Icon(painterResource(if (isAppUpdate) R.drawable.chevron_right else R.drawable.play_circle), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))\n",
        "            Icon(if (isAppUpdate) Icons.Default.ChevronRight else Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))\n",
        "swapped drawable resources for Material icons", f_history)
save(f_history, c)
print("\nDone.")
PYEOF

echo "==> Round 2 done. Next: ./gradlew :app:compileArm64ReleaseKotlin --console=plain"
