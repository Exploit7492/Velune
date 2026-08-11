/*
 * Velune - Glass Effect settings.
 * Lets the user tune the blur/vibrancy/lens presets already defined in
 * GlassEffectDefaults.kt instead of them being fixed constants.
 */

package com.nikhil.yt.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.constants.GlassBlurIntensityKey
import com.nikhil.yt.constants.GlassLensEnabledKey
import com.nikhil.yt.constants.GlassVibrancyEnabledKey
import com.nikhil.yt.ui.component.Material3SettingsGroup
import com.nikhil.yt.ui.component.Material3SettingsItem
import com.nikhil.yt.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassEffectSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val scrollState = rememberScrollState()

    var blurIntensity by rememberPreference(GlassBlurIntensityKey, 1f)
    var vibrancyEnabled by rememberPreference(GlassVibrancyEnabledKey, true)
    var lensEnabled by rememberPreference(GlassLensEnabledKey, true)

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
            )
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Material3SettingsGroup(
            title = stringResource(R.string.glass_effects),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.blur_on),
                    title = { Text(stringResource(R.string.glass_blur_intensity)) },
                    description = { Text(stringResource(R.string.glass_blur_intensity_desc)) },
                    trailingContent = {
                        Text("${(blurIntensity * 100).roundToInt()}%")
                    },
                    onClick = {},
                ),
            ),
        )

        Slider(
            value = blurIntensity,
            onValueChange = { blurIntensity = it },
            valueRange = 0.3f..1.6f,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = null,
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.glass_vibrancy)) },
                    description = { Text(stringResource(R.string.glass_vibrancy_desc)) },
                    trailingContent = {
                        Switch(checked = vibrancyEnabled, onCheckedChange = { vibrancyEnabled = it })
                    },
                    onClick = { vibrancyEnabled = !vibrancyEnabled },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.glass_lens)) },
                    description = { Text(stringResource(R.string.glass_lens_desc)) },
                    trailingContent = {
                        Switch(checked = lensEnabled, onCheckedChange = { lensEnabled = it })
                    },
                    onClick = { lensEnabled = !lensEnabled },
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.glass_effects)) },
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
