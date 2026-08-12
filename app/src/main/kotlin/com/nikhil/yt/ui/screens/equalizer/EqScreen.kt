/*
 * Velune - Parametric EQ settings screen.
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.nikhil.yt.ui.component.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.eq.data.ParametricEQ
import com.nikhil.yt.ui.component.EnumDialog
import com.nikhil.yt.ui.component.PreferenceEntry
import com.nikhil.yt.ui.component.PreferenceGroupTitle
import com.nikhil.yt.ui.component.SwitchPreference
import com.nikhil.yt.ui.component.TextFieldDialog
import com.nikhil.yt.ui.utils.backToMain
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val viewModel: EQViewModel = viewModel(factory = EQViewModelFactory(context))
    val state by viewModel.state.collectAsState()

    val scrollState = rememberScrollState()

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

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .verticalScroll(scrollState)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        PreferenceGroupTitle(title = stringResource(R.string.parametric_eq))

        // Enable toggle
        SwitchPreference(
            title = { Text(stringResource(R.string.enable_parametric_eq)) },
            description = stringResource(R.string.enable_parametric_eq_desc),
            icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
            checked = state.enabled,
            onCheckedChange = { viewModel.setEnabled(it) },
        )

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

                    // Bands
                    Spacer(Modifier.height(8.dp))
                    PreferenceGroupTitle(title = stringResource(R.string.eq_bands))

                    profile.bands.forEachIndexed { index, band ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Band ${index + 1}: ${band.filterType.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                IconButton(
                                    onClick = { viewModel.removeBand(index) },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.remove),
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.frequency_hz, band.frequency.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = band.frequency.toFloat(),
                                onValueChange = { viewModel.updateBandFrequency(index, it) },
                                valueRange = 20f..20000f,
                                steps = 199,
                            )

                            Text(
                                text = stringResource(R.string.gain_db, String.format("%.1f", band.gain)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = band.gain.toFloat(),
                                onValueChange = { viewModel.updateBandGain(index, it) },
                                valueRange = -12f..12f,
                                steps = 48,
                            )

                            Text(
                                text = "Q: ${String.format("%.2f", band.q)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Slider(
                                value = band.q.toFloat(),
                                onValueChange = { viewModel.updateBandQ(index, it) },
                                valueRange = 0.1f..10f,
                                steps = 99,
                            )
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

    TopAppBar(
        title = { Text(stringResource(R.string.parametric_eq)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

class EQViewModelFactory(private val context: android.content.Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return EQViewModel(context.applicationContext) as T
    }
}
