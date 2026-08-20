/*
 * Velune - Parametric EQ editor (the "Custom" mode of the hybrid equalizer).
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.ui.screens.equalizer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.nikhil.yt.ui.component.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nikhil.yt.R
import com.nikhil.yt.ui.component.EnumDialog
import com.nikhil.yt.ui.component.PreferenceEntry
import com.nikhil.yt.ui.component.PreferenceGroupTitle
import com.nikhil.yt.ui.component.TextFieldDialog
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * The parametric-EQ editor body (profile picker, import/export, preamp,
 * per-band frequency/gain/Q sliders with add/remove). This used to be its own
 * standalone screen ("Echo Equalizer" at settings/eq); it's now embedded as the
 * "Custom" mode inside the hybrid Axion equalizer (see AxionEqScreen) instead of
 * two screens duplicating the same controls. Same [EQViewModel]/EqualizerService
 * backend either way, so a profile saved here shows up in Simple/Advanced too.
 */
@Composable
fun ParametricEqEditor(viewModel: EQViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // File picker for JSON import
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val json = BufferedReader(InputStreamReader(stream)).readText()
                viewModel.importFromJson(json, EQViewModel.ImportFormat.AUTO_EQ)
            }
        }
    }

    Column {
        PreferenceGroupTitle(title = stringResource(R.string.parametric_eq))

        // No local enable switch here — the single master toggle at the
        // top of AxionEqScreen already drives the same ParametricEQEnabledKey
        // this reads via EQViewModel's reactive collector, so a second
        // switch here would just be a redundant, confusing second control
        // for the exact same underlying flag. This composable used to have
        // its own SwitchPreference; removed rather than left disabled/
        // hidden, since state.enabled already reflects the master toggle.

        AnimatedVisibility(visible = state.enabled) {
            Column {
                // Profile selector
                var showProfileDialog by remember { mutableStateOf(false) }
                if (showProfileDialog) {
                    EnumDialog(
                        onDismiss = { showProfileDialog = false },
                        onSelect = {
                            viewModel.selectProfile(it)
                            showProfileDialog = false
                        },
                        title = stringResource(R.string.eq_profile),
                        current = state.selectedProfile,
                        values = state.profiles,
                        valueText = { it.name },
                    )
                }

                PreferenceEntry(
                    title = { Text(stringResource(R.string.eq_profile)) },
                    description = state.selectedProfile?.name ?: stringResource(R.string.not_set),
                    icon = { Icon(painterResource(R.drawable.library_music), null) },
                    onClick = { showProfileDialog = true },
                )

                // Profile actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.showImportDialog() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.import_profile))
                    }
                    OutlinedButton(
                        onClick = { viewModel.showSaveDialog() },
                        enabled = state.selectedProfile != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.save_profile))
                    }
                    val selected = state.selectedProfile
                    OutlinedButton(
                        onClick = { viewModel.showDeleteConfirm() },
                        enabled = selected != null && !selected.id.startsWith("built_in_") && !listOf("flat", "bass_boost", "v_shape", "vocal_boost", "treble_boost").contains(selected.id),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }

                // Preamp
                val profile = state.selectedProfile
                if (profile != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.preamp_db, String.format("%.1f", profile.preamp)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Slider(
                        value = profile.preamp.toFloat(),
                        onValueChange = { viewModel.updatePreamp(it) },
                        valueRange = -12f..12f,
                        steps = 48,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    // Bands — gain now lives on Simple's slim vertical fader
                    // strip (NeonVerticalFader/SimpleBandStrip in
                    // AxionEqScreen.kt), reading/writing this exact same
                    // profile.bands data, so editing gain here would just be
                    // a second, redundant control for the same value. This
                    // list is now Q-only: the one per-band parameter that
                    // doesn't have a home anywhere else, kept compact (one
                    // row per band) instead of the three-slider/full-header
                    // block this used to be.
                    //
                    // Known trade-off, not silently dropped: frequency
                    // editing for individual bands is gone from this
                    // screen entirely. For the fixed 10 standard bands that
                    // Simple's strip drives, that's correct — a graphic EQ
                    // legitimately doesn't expose per-band frequency, only
                    // gain (Neutron's own fixed-band view doesn't either).
                    // It does mean a band added via "Add band" below, or an
                    // AutoEQ/JSON import that produced bands beyond the
                    // fixed 10, has no UI left to move off its initial
                    // frequency. That's a real, known gap for that
                    // less-common path, not an oversight.
                    Spacer(Modifier.height(8.dp))
                    PreferenceGroupTitle(title = stringResource(R.string.eq_bands))

                    profile.bands.forEachIndexed { index, band ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.frequency_hz, band.frequency.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(72.dp),
                            )
                            Slider(
                                value = band.q.toFloat(),
                                onValueChange = { viewModel.updateBandQ(index, it) },
                                valueRange = 0.1f..10f,
                                steps = 99,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "Q %.2f".format(band.q),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(52.dp),
                            )
                            IconButton(
                                onClick = { viewModel.removeBand(index) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = stringResource(R.string.remove),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    // Add band button
                    TextButton(
                        onClick = { viewModel.addBand() },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.add_band))
                    }
                }
            }
        }
    }

    // Import dialog
    if (state.showImportDialog) {
        var importText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportDialog() },
            title = { Text(stringResource(R.string.import_profile)) },
            text = {
                Column {
                    Text(stringResource(R.string.paste_json_or_pick_file))
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text(stringResource(R.string.paste_json_here)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                    )
                    state.importError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importText.isNotBlank()) {
                            viewModel.importFromJson(importText, EQViewModel.ImportFormat.AUTO_EQ)
                        }
                    },
                ) {
                    Text(stringResource(R.string.import_label))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                ) {
                    Text(stringResource(R.string.pick_file))
                }
            },
        )
    }

    // Save dialog
    if (state.showSaveDialog) {
        var saveName by remember { mutableStateOf(state.selectedProfile?.name ?: "") }
        TextFieldDialog(
            title = { Text(stringResource(R.string.save_profile)) },
            icon = { Icon(painterResource(R.drawable.save), null) },
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(text = saveName),
            onDone = {
                if (it.isNotBlank()) viewModel.saveCurrentProfile(it)
                viewModel.dismissSaveDialog()
            },
            onDismiss = { viewModel.dismissSaveDialog() },
        )
    }

    // Delete confirmation
    if (state.showDeleteConfirm) {
        val toDelete = state.selectedProfile
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text(stringResource(R.string.delete_profile)) },
            text = { Text(stringResource(R.string.delete_profile_confirm, toDelete?.name ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = { toDelete?.let { viewModel.deleteProfile(it) } },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

class EQViewModelFactory(private val context: android.content.Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return EQViewModel(context.applicationContext) as T
    }
}
