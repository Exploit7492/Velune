/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.utils

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.Log

/**
 * Handles switching the launcher icon between the default (dynamic/themed) icon and a
 * legacy/static icon.
 *
 * NOTE: actually swapping the icon shown on the home screen requires an `<activity-alias>`
 * for each icon variant declared in AndroidManifest.xml (with its own `android:icon`), plus
 * the corresponding mipmap/adaptive-icon assets for the "legacy" variant. Neither exists in
 * this project yet, so [setIcon] safely persists the user's choice (the caller already saves
 * the preference) and no-ops the actual component swap rather than attempting to enable/disable
 * a manifest alias that isn't declared - doing that would throw at runtime and crash the app.
 *
 * Once a legacy-icon activity-alias and its assets are added to the manifest, replace the body
 * of [setIcon] with the real `PackageManager.setComponentEnabledSetting` toggle between the
 * default and legacy alias `ComponentName`s.
 */
object IconUtils {

    private const val TAG = "IconUtils"

    fun setIcon(activity: Activity, useDynamic: Boolean, useLegacy: Boolean) {
        val defaultComponent = ComponentName(activity, "${activity.packageName}.MainActivity")
        try {
            // No legacy-icon activity-alias is declared in the manifest yet, so there is
            // nothing to switch to. Ensure the default launcher component stays enabled so
            // the app icon never disappears from the home screen.
            activity.packageManager.setComponentEnabledSetting(
                defaultComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update launcher icon component state", e)
        }
    }
}
