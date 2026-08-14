/*
 * Velune - Adaptive List-Detail Pane.
 * Reusable two-pane layout for tablets/foldables.
 * Falls back to single-pane on phones.
 */

package com.nikhil.yt.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass

@Composable
fun <T> AdaptiveListDetailPane(
    selectedItem: T?,
    listPane: @Composable (Modifier) -> Unit,
    detailPane: @Composable (T, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    listWeight: Float = 0.4f,
    detailWeight: Float = 0.6f,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isExpanded = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )

    if (isExpanded && selectedItem != null) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(listWeight)) {
                listPane(Modifier.fillMaxSize())
            }
            Box(modifier = Modifier.weight(detailWeight)) {
                detailPane(selectedItem, Modifier.fillMaxSize())
            }
        }
    } else {
        if (selectedItem != null) {
            detailPane(selectedItem, modifier)
        } else {
            listPane(modifier)
        }
    }
}
