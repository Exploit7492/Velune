/*
 * Velune - Quick Equalizer dialog.
 * Lightweight wrapper around the existing Parametric EQ (EQViewModel),
 * for use as a modal launched from the player screen.
 */

package com.nikhil.yt.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nikhil.yt.R
import com.nikhil.yt.ui.screens.equalizer.EQViewModel
import com.nikhil.yt.ui.screens.equalizer.EQViewModelFactory

@Composable
fun EqualizerDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: EQViewModel = viewModel(factory = EQViewModelFactory(context))
    val state by viewModel.state.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
        title = { Text(stringResource(R.string.equalizer)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.eq_enabled))
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { viewModel.setEnabled(it) },
                    )
                }

                if (state.enabled) {
                    val profile = state.selectedProfile
                    Text(
                        text = profile?.name ?: stringResource(R.string.not_set),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (profile != null) {
                        Text(
                            text = stringResource(R.string.eq_preamp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = profile.preamp.toFloat(),
                            onValueChange = { viewModel.updatePreamp(it) },
                            valueRange = -12f..12f,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
    )
}
