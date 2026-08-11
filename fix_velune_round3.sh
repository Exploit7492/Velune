#!/usr/bin/env bash
set -euo pipefail

f_history="app/src/main/kotlin/com/nikhil/yt/ui/screens/HistoryScreen.kt"

[ -f "$f_history" ] || { echo "ERROR: $f_history not found — run from repo root."; exit 1; }
cp "$f_history" "$f_history.bak"

python3 - "$f_history" <<'PYEOF'
import sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    c = f.read()

before = c

n1 = c.count("HistoryViewModel.NotificationItem.ContentType")
c = c.replace("HistoryViewModel.NotificationItem.ContentType", "ContentType")
n2 = c.count("HistoryViewModel.NotificationItem.NotificationType")
c = c.replace("HistoryViewModel.NotificationItem.NotificationType", "NotificationType")
print(f"  [OK]   fixed {n1} wrong ContentType path(s), {n2} wrong NotificationType path(s)")

old_icon_import = "import androidx.compose.material.icons.filled.PlayCircle\n"
new_icon_import = "import androidx.compose.material.icons.filled.PlayCircle\nimport androidx.compose.material.icons.filled.Refresh\n"
if old_icon_import in c:
    c = c.replace(old_icon_import, new_icon_import, 1)
    print("  [OK]   added Refresh icon import")
else:
    print("  [SKIP] Refresh icon import anchor not found")

old_refresh_icon = 'else Icon(painterResource(R.drawable.refresh), contentDescription = "Refresh")\n'
new_refresh_icon = 'else Icon(Icons.Default.Refresh, contentDescription = "Refresh")\n'
if old_refresh_icon in c:
    c = c.replace(old_refresh_icon, new_refresh_icon, 1)
    print("  [OK]   swapped R.drawable.refresh for Icons.Default.Refresh")
else:
    print("  [SKIP] refresh icon usage not found (already fixed?)")

if c == before:
    print("  [WARN] no changes were made -- check file state")

with open(path, "w", encoding="utf-8") as f:
    f.write(c)
PYEOF

echo "==> Round 3 done. Next: ./gradlew :app:compileArm64ReleaseKotlin --console=plain"
