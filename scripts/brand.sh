#!/bin/bash
set -e

# Usage: ./scripts/brand.sh <path-to-tenant-config.json>
CONFIG_FILE="$1"
if [ -z "$CONFIG_FILE" ]; then
    echo "Usage: $0 <config.json>"
    exit 1
fi

# Parse JSON (requires jq)
TENANT_ID=$(jq -r '.tenant_id' "$CONFIG_FILE")
APP_NAME=$(jq -r '.app_name' "$CONFIG_FILE")
PACKAGE_NAME=$(jq -r '.package_name' "$CONFIG_FILE")
PRIMARY_COLOR=$(jq -r '.primary_color' "$CONFIG_FILE")
ACCENT_COLOR=$(jq -r '.accent_color' "$CONFIG_FILE")
LOGO_URL=$(jq -r '.logo_url' "$CONFIG_FILE")
CONSENT_TEXT=$(jq -r '.consent_text' "$CONFIG_FILE")
PAWNS_API_KEY=$(jq -r '.pawns_api_key // ""' "$CONFIG_FILE")

if [ -z "$PAWNS_API_KEY" ]; then
    # No per-tenant key in the config JSON — fall back to the shared
    # default, passed in by the caller as DEFAULT_PAWNS_API_KEY (the
    # workflow sources this from a GitHub Actions secret). This used to
    # be a live key hardcoded directly here, committed to this public
    # repo's git history — moved out for the same reason every other
    # credential in this repo is build-time-sourced now, not committed.
    # See app/build.gradle.kts's own PAWNS_API_KEY comment for the fuller
    # explanation and the rotation note.
    PAWNS_API_KEY="${DEFAULT_PAWNS_API_KEY:-}"
fi

echo "Branding tenant: $TENANT_ID ($APP_NAME)"

# 1. Update app name in strings.xml
sed -i "s/<string name=\"app_name\">.*<\/string>/<string name=\"app_name\">$APP_NAME<\/string>/" app/src/main/res/values/strings.xml
sed -i "s/<string name=\"config_app_name\">.*<\/string>/<string name=\"config_app_name\">$APP_NAME<\/string>/" app/src/main/res/values/strings.xml

# 2. Update colors in colors.xml
sed -i "s/<color name=\"primary\">.*</<color name=\"primary\">$PRIMARY_COLOR</" app/src/main/res/values/colors.xml
sed -i "s/<color name=\"accent\">.*</<color name=\"accent\">$ACCENT_COLOR</" app/src/main/res/values/colors.xml

# 3. Replace launcher icons (all densities)
curl -L "$LOGO_URL" -o /tmp/logo.png
for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    size=48
    case $density in
        mdpi) size=48 ;;
        hdpi) size=72 ;;
        xhdpi) size=96 ;;
        xxhdpi) size=144 ;;
        xxxhdpi) size=192 ;;
    esac
    convert /tmp/logo.png -resize ${size}x${size} app/src/main/res/mipmap-${density}/ic_launcher.png
    convert /tmp/logo.png -resize ${size}x${size} app/src/main/res/mipmap-${density}/ic_launcher_round.png
done

# 4. Update consent text (if exists)
if [ -n "$CONSENT_TEXT" ]; then
    sed -i "s/<string name=\"consent_text\">.*<\/string>/<string name=\"consent_text\">$CONSENT_TEXT<\/string>/" app/src/main/res/values/strings.xml
fi

# 5. Output environment variables for the build
echo "PACKAGE_NAME=$PACKAGE_NAME" > scripts/brand.env
echo "PAWNS_API_KEY=$PAWNS_API_KEY" >> scripts/brand.env
echo "TENANT_ID=$TENANT_ID" >> scripts/brand.env
echo "APP_NAME=$APP_NAME" >> scripts/brand.env

echo "Branding completed for $TENANT_ID"
