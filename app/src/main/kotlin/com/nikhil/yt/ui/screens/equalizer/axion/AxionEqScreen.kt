package com.nikhil.yt.ui.screens.equalizer.axion

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nikhil.yt.R
import com.nikhil.yt.eq.data.SavedEQProfile
import com.nikhil.yt.ui.component.Material3SettingsGroup
import com.nikhil.yt.ui.component.Material3SettingsItem
import com.nikhil.yt.ui.component.PreferenceGroupTitle
import com.nikhil.yt.ui.screens.equalizer.EQViewModel
import com.nikhil.yt.ui.screens.equalizer.EQViewModelFactory
import com.nikhil.yt.ui.screens.equalizer.ParametricEqEditor
import com.nikhil.yt.ui.utils.backToMain
import kotlin.math.abs
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Resolves a picked document Uri's user-facing file name via the standard
 * SAF OpenableColumns query, falling back to the last path segment for
 * providers that don't report DISPLAY_NAME. UI-layer concern only — the
 * name is purely for display/persistence, never used to reopen the file
 * (the copy made in AxionEqViewModel.importImpulseResponse is what gets
 * reopened).
 */
private fun resolveDisplayName(context: android.content.Context, uri: Uri): String {
    var name: String? = null
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
    }
    return name ?: uri.lastPathSegment ?: "impulse_response.wav"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AxionEqScreen(
    onBackClick: () -> Unit,
    viewModel: AxionEqViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabled.collectAsState()
    val bandGains by viewModel.bandGains.collectAsState()
    val bandQ by viewModel.bandQ.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val preampDb by viewModel.preampDb.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val bassBoostDb by viewModel.bassBoostDb.collectAsState()
    val stereoWidth by viewModel.stereoWidth.collectAsState()
    val limiterEnabled by viewModel.limiterEnabled.collectAsState()
    val limiterCeilingDb by viewModel.limiterCeilingDb.collectAsState()
    val convolutionEnabled by viewModel.convolutionEnabled.collectAsState()
    val impulseResponseInfo by viewModel.impulseResponseInfo.collectAsState()
    val convolutionImporting by viewModel.convolutionImporting.collectAsState()
    val convolutionImportError by viewModel.convolutionImportError.collectAsState()
    val presetManufacturers by viewModel.presetManufacturers.collectAsState()
    val presetModels by viewModel.presetModels.collectAsState()
    val presetSelectedManufacturer by viewModel.presetSelectedManufacturer.collectAsState()
    val presetBrowserLoading by viewModel.presetBrowserLoading.collectAsState()
    val presetBrowserError by viewModel.presetBrowserError.collectAsState()
    val presetDownloadingName by viewModel.presetDownloadingName.collectAsState()
    var showPresetSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val irPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importImpulseResponse(it, resolveDisplayName(context, it))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.echo_equalizer, stringResource(R.string.app_name))) },
                navigationIcon = {
                    com.nikhil.yt.ui.component.IconButton(
                        onClick = onBackClick,
                        onLongClick = {}
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        icon = androidx.compose.ui.res.painterResource(R.drawable.equalizer),
                        title = { Text(stringResource(R.string.eq_enable_title)) },
                        description = { Text(stringResource(R.string.eq_enable_summary)) },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { viewModel.setEnabled(it) },
                                thumbContent = {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(
                                            id = if (enabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { viewModel.setEnabled(!enabled) }
                    )
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ToggleButton(
                    checked = mode == 0,
                    onCheckedChange = { viewModel.setMode(0) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) {
                    Text(stringResource(R.string.eq_simple))
                }
                ToggleButton(
                    checked = mode == 1,
                    onCheckedChange = { viewModel.setMode(1) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                ) {
                    Text(stringResource(R.string.eq_advanced))
                }
                ToggleButton(
                    checked = mode == 2,
                    onCheckedChange = { viewModel.setMode(2) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) {
                    Text(stringResource(R.string.eq_master))
                }
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    fadeIn().togetherWith(fadeOut()).using(SizeTransform(clip = false))
                },
                label = "eqMode",
            ) { currentMode ->
                val isDirty by viewModel.isDirty.collectAsState()
                var showSaveDialog by remember { mutableStateOf(false) }

                if (showSaveDialog) {
                    SavePresetDialog(
                        onDismiss = { showSaveDialog = false },
                        onSave = { name ->
                            viewModel.saveCustomProfile(name)
                            showSaveDialog = false
                        }
                    )
                }

                when (currentMode) {
                    0 -> SimpleEqMode(
                        bandGains = bandGains,
                        enabled = enabled,
                        viewModel = viewModel,
                        isDirty = isDirty,
                        onSaveClick = { showSaveDialog = true }
                    )
                    1 -> AdvancedEqMode(
                        bandGains = bandGains,
                        bandQ = bandQ,
                        enabled = enabled,
                        onBandChange = { band, value ->
                            viewModel.setBandGain(band, value)
                        },
                        onBandQChange = { band, q ->
                            viewModel.setBandQ(band, q)
                        },
                        onReset = {
                            viewModel.reset()
                        }
                    )
                    else -> {
                        // "Master" — master-bus rotary knobs (Preamp, Balance, Bass
                        // Boost, Stereo Width, Limiter — the Poweramp/Wavelet-style
                        // knobs combined with a Neutron-style final limiter/stereo
                        // enhancer stage) plus the full parametric band editor below
                        // them (arbitrary bands, free frequency/gain/Q, JSON/AutoEQ
                        // import, profile management). This used to live on its own
                        // screen at settings/eq under the "Echo Equalizer" name;
                        // it's the same EqualizerService/EQProfileRepository backend
                        // as Simple/Advanced above, so a profile saved here shows up
                        // there too.
                        Column {
                            MasterBusControls(
                                preampDb = preampDb,
                                balance = balance,
                                bassBoostDb = bassBoostDb,
                                stereoWidth = stereoWidth,
                                limiterEnabled = limiterEnabled,
                                limiterCeilingDb = limiterCeilingDb,
                                enabled = enabled,
                                accentColor = MaterialTheme.colorScheme.primary,
                                onPreampChange = { viewModel.setPreampDb(it) },
                                onBalanceChange = { viewModel.setBalance(it) },
                                onBassBoostChange = { viewModel.setBassBoostDb(it) },
                                onStereoWidthChange = { viewModel.setStereoWidth(it) },
                                onLimiterEnabledChange = { viewModel.setLimiterEnabled(it) },
                                onLimiterCeilingChange = { viewModel.setLimiterCeilingDb(it) },
                            )
                            ConvolutionSection(
                                enabled = enabled,
                                convolutionEnabled = convolutionEnabled,
                                impulseResponseInfo = impulseResponseInfo,
                                importing = convolutionImporting,
                                importError = convolutionImportError,
                                onLoadClick = {
                                    // audio/x-wav and audio/wav cover most
                                    // providers; application/octet-stream is
                                    // included since many Android file
                                    // pickers report WAV files under that
                                    // generic type instead of a proper
                                    // audio/* MIME.
                                    irPickerLauncher.launch(
                                        arrayOf("audio/x-wav", "audio/wav", "audio/*", "application/octet-stream")
                                    )
                                },
                                onEnabledChange = { viewModel.setConvolutionEnabled(it) },
                                onClearClick = { viewModel.clearImpulseResponse() },
                                onBrowsePresetsClick = {
                                    showPresetSheet = true
                                    if (presetManufacturers.isEmpty()) {
                                        viewModel.loadPresetManufacturers()
                                    }
                                },
                            )
                            if (showPresetSheet) {
                                PresetLibrarySheet(
                                    manufacturers = presetManufacturers,
                                    models = presetModels,
                                    selectedManufacturer = presetSelectedManufacturer,
                                    loading = presetBrowserLoading,
                                    error = presetBrowserError,
                                    downloadingName = presetDownloadingName,
                                    onManufacturerClick = { viewModel.openPresetManufacturer(it) },
                                    onBackClick = { viewModel.closePresetManufacturer() },
                                    onRefreshClick = {
                                        val current = presetSelectedManufacturer
                                        if (current != null) {
                                            viewModel.openPresetManufacturer(current, forceRefresh = true)
                                        } else {
                                            viewModel.loadPresetManufacturers(forceRefresh = true)
                                        }
                                    },
                                    onModelClick = { model ->
                                        viewModel.importFromPresetLibrary(model)
                                        showPresetSheet = false
                                    },
                                    onDismiss = {
                                        showPresetSheet = false
                                        viewModel.closePresetManufacturer()
                                    },
                                )
                            }
                            val parametricContext = LocalContext.current
                            val parametricViewModel: EQViewModel = viewModel(
                                factory = EQViewModelFactory(parametricContext)
                            )
                            ParametricEqEditor(viewModel = parametricViewModel)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

/**
 * Master-bus rotary knobs — Preamp, Balance, Bass Boost — sitting above the
 * per-band parametric editor in the Master tab, styled after Poweramp/Wavelet/
 * Neutron's own master-bus panels. Preamp feeds the active profile the same
 * way Simple/Advanced do; Balance and Bass Boost apply directly at the audio
 * processor, independent of which profile/curve is active — see
 * AxionEqViewModel.setBalance/setBassBoostDb and EqualizerService.
 */
@Composable
private fun MasterBusControls(
    preampDb: Float,
    balance: Float,
    bassBoostDb: Float,
    stereoWidth: Float,
    limiterEnabled: Boolean,
    limiterCeilingDb: Float,
    enabled: Boolean,
    accentColor: Color,
    onPreampChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit,
    onBassBoostChange: (Float) -> Unit,
    onStereoWidthChange: (Float) -> Unit,
    onLimiterEnabledChange: (Boolean) -> Unit,
    onLimiterCeilingChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PreferenceGroupTitle(title = stringResource(R.string.eq_master_bus))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RotaryKnob(
                value = ((preampDb + 12f) / 24f).coerceIn(0f, 1f),
                onValueChange = { onPreampChange((it * 24f - 12f).coerceIn(-12f, 12f)) },
                label = stringResource(R.string.eq_preamp),
                valueLabel = "%+.1f dB".format(preampDb),
                enabled = enabled,
                accentColor = accentColor,
            )
            RotaryKnob(
                value = ((balance + 1f) / 2f).coerceIn(0f, 1f),
                onValueChange = { onBalanceChange((it * 2f - 1f).coerceIn(-1f, 1f)) },
                label = stringResource(R.string.eq_balance),
                valueLabel = when {
                    balance > 0.02f -> "R %.0f%%".format(balance * 100)
                    balance < -0.02f -> "L %.0f%%".format(-balance * 100)
                    else -> stringResource(R.string.eq_balance_center)
                },
                enabled = enabled,
                accentColor = accentColor,
            )
            RotaryKnob(
                value = (bassBoostDb / 12f).coerceIn(0f, 1f),
                onValueChange = { onBassBoostChange((it * 12f).coerceIn(0f, 12f)) },
                label = stringResource(R.string.eq_bass_boost),
                valueLabel = "+%.1f dB".format(bassBoostDb),
                enabled = enabled,
                accentColor = accentColor,
            )
        }
        // Stereo width and limiter — the Neutron-style "mastering" half of
        // the master bus, distinct from the Poweramp-style tone knobs above:
        // width reshapes the stereo image instead of tone, the limiter caps
        // final output instead of shaping it. Kept in their own row so the
        // Master tab visually groups "tone" controls from "output" controls,
        // the same separation Neutron's own master chain uses.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RotaryKnob(
                value = (stereoWidth / 2f).coerceIn(0f, 1f),
                onValueChange = { onStereoWidthChange((it * 2f).coerceIn(0f, 2f)) },
                label = stringResource(R.string.eq_stereo_width),
                valueLabel = "%.0f%%".format(stereoWidth * 100),
                enabled = enabled,
                accentColor = accentColor,
            )
            RotaryKnob(
                value = ((limiterCeilingDb + 12f) / 12f).coerceIn(0f, 1f),
                onValueChange = { onLimiterCeilingChange((it * 12f - 12f).coerceIn(-12f, 0f)) },
                label = stringResource(R.string.eq_limiter_ceiling),
                valueLabel = "%.1f dB".format(limiterCeilingDb),
                enabled = enabled && limiterEnabled,
                accentColor = accentColor,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.eq_limiter),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Switch(
                checked = limiterEnabled,
                onCheckedChange = onLimiterEnabledChange,
                enabled = enabled,
            )
        }
    }
}

/**
 * Convolution (impulse-response) tone shaping — the UI for the engine
 * built in the previous session (Fft/PartitionedConvolver/
 * ConvolutionAudioProcessor/ImpulseResponse), which had no screen wired to
 * it at all until now. Sits in the Master tab below the rotary knobs,
 * since it's the same "master bus" layer conceptually — it runs ahead of
 * the per-band EQ, the same way the limiter runs after it.
 */
@Composable
private fun ConvolutionSection(
    enabled: Boolean,
    convolutionEnabled: Boolean,
    impulseResponseInfo: ImpulseResponseInfo?,
    importing: Boolean,
    importError: String?,
    onLoadClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onClearClick: () -> Unit,
    onBrowsePresetsClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PreferenceGroupTitle(title = stringResource(R.string.eq_convolution))
        Text(
            text = stringResource(R.string.eq_convolution_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (impulseResponseInfo == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedButton(
                    onClick = onLoadClick,
                    enabled = enabled && !importing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (importing) stringResource(R.string.eq_convolution_loading)
                        else stringResource(R.string.eq_convolution_load_ir)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = impulseResponseInfo.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(
                            R.string.eq_convolution_ir_info,
                            impulseResponseInfo.durationSeconds,
                            impulseResponseInfo.channels,
                            impulseResponseInfo.sampleRateHz,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = convolutionEnabled,
                    onCheckedChange = onEnabledChange,
                    enabled = enabled && !importing,
                )
                com.nikhil.yt.ui.component.IconButton(
                    onClick = onClearClick,
                    onLongClick = {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = stringResource(R.string.eq_convolution_clear),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = onLoadClick, enabled = enabled && !importing) {
                    Text(
                        text = if (importing) stringResource(R.string.eq_convolution_loading)
                        else stringResource(R.string.eq_convolution_replace_ir)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onBrowsePresetsClick, enabled = enabled && !importing) {
                Text(stringResource(R.string.eq_convolution_browse_presets))
            }
        }

        AnimatedVisibility(visible = importError != null) {
            Text(
                text = importError.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Bottom sheet browsing the ASH-IR-Dataset preset library — manufacturer
 * list, drill into a manufacturer for its headphone models, tap a model
 * to download+apply it through the same validate-then-adopt path as a
 * manually picked file (AxionEqViewModel.importFromPresetLibrary).
 * Deliberately two flat lists rather than a search box or nested tree —
 * the catalog is fetched from GitHub on demand (see PresetIrRepository),
 * not bundled, so keeping the browse UI simple keeps the number of things
 * that can go wrong on a real device (vs. what the JVM harness could
 * verify) small: this composable's rendering itself is unverified beyond
 * compiling, same caveat as the rest of the Convolution UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetLibrarySheet(
    manufacturers: List<com.nikhil.yt.eq.data.PresetManufacturer>,
    models: List<com.nikhil.yt.eq.data.PresetHeadphoneModel>,
    selectedManufacturer: com.nikhil.yt.eq.data.PresetManufacturer?,
    loading: Boolean,
    error: String?,
    downloadingName: String?,
    onManufacturerClick: (com.nikhil.yt.eq.data.PresetManufacturer) -> Unit,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onModelClick: (com.nikhil.yt.eq.data.PresetHeadphoneModel) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedManufacturer?.name
                        ?: stringResource(R.string.eq_convolution_presets_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedManufacturer != null) {
                        TextButton(onClick = onBackClick) {
                            Text(stringResource(R.string.eq_convolution_presets_back))
                        }
                    }
                    TextButton(onClick = onRefreshClick, enabled = !loading) {
                        Text(stringResource(R.string.eq_convolution_presets_refresh))
                    }
                }
            }
            Text(
                text = stringResource(R.string.eq_convolution_presets_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (downloadingName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.eq_convolution_presets_downloading, downloadingName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            } else if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else if (selectedManufacturer == null) {
                if (manufacturers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.eq_convolution_presets_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(manufacturers) { manufacturer ->
                            Text(
                                text = manufacturer.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onManufacturerClick(manufacturer) }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(models) { model ->
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onModelClick(model) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleEqMode(
    bandGains: FloatArray,
    enabled: Boolean,
    viewModel: AxionEqViewModel,
    isDirty: Boolean,
    onSaveClick: () -> Unit
) {
    
    var bass by remember { mutableFloatStateOf(0f) }
    var mid by remember { mutableFloatStateOf(0f) }
    var treble by remember { mutableFloatStateOf(0f) }

    fun syncFromBands() {
        
        bass = bandGains[1] / 50f
        mid = (bandGains[4] + bandGains[5]) / 2f / 50f
        treble = bandGains[8] / 50f
    }

    fun applyTriangle() {
        val bv = (bass * 50f).coerceIn(-600f, 600f)
        val mv = (mid * 50f).coerceIn(-600f, 600f)
        val tv = (treble * 50f).coerceIn(-600f, 600f)
        val newGains = FloatArray(10)
        
        
        newGains[0] = bv * 1.1f
        newGains[1] = bv * 1.0f
        newGains[2] = bv * 0.7f + mv * 0.3f
        newGains[3] = bv * 0.2f + mv * 0.8f
        newGains[4] = mv * 1.0f
        newGains[5] = mv * 1.0f
        newGains[6] = mv * 0.8f + tv * 0.2f
        newGains[7] = mv * 0.3f + tv * 0.7f
        newGains[8] = tv * 1.0f
        newGains[9] = tv * 1.15f 
        
        viewModel.setBandsGains(newGains, fromUser = true)
    }

    LaunchedEffect(bandGains) {
        syncFromBands()
    }

    val echoPresets = listOf(
        R.string.eq_preset_flat to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        R.string.eq_preset_echo_signature to floatArrayOf(150f, 100f, 50f, 0f, -20f, 0f, 80f, 150f, 200f, 150f),
        R.string.eq_preset_acoustic to floatArrayOf(150f, 150f, 50f, 75f, 100f, 75f, 125f, 175f, 150f, 75f),

        R.string.eq_preset_bass_boost to floatArrayOf(500f, 400f, 250f, 100f, 0f, -50f, 0f, 100f, 200f, 300f),
        R.string.eq_preset_pure_clarity to floatArrayOf(-100f, -50f, 0f, 50f, 150f, 250f, 300f, 250f, 150f, 100f),
        R.string.eq_preset_soft_bass to floatArrayOf(200f, 180f, 140f, 80f, 30f, 20f, 60f, 90f, 110f, 130f),
        R.string.eq_preset_electronic to floatArrayOf(350f, 280f, 120f, -50f, -150f, 50f, 180f, 300f, 400f, 500f),
        R.string.eq_preset_rock to floatArrayOf(300f, 220f, 150f, 50f, -100f, 120f, 200f, 250f, 320f, 380f),
        R.string.eq_preset_pop to floatArrayOf(-150f, 0f, 100f, 180f, 250f, 220f, 150f, 80f, -50f, -120f),
        R.string.eq_preset_jazz to floatArrayOf(150f, 100f, 60f, 140f, 200f, 180f, 120f, 180f, 220f, 200f),
        R.string.eq_preset_voice to floatArrayOf(-250f, -150f, 0f, 200f, 400f, 380f, 200f, 120f, 0f, -120f),
    )

    val dolbyPresets = listOf(
        R.string.eq_preset_dolby_open to floatArrayOf(150f, 180f, 220f, 180f, 160f, 210f, 250f, 280f, 180f, 80f),
        R.string.eq_preset_dolby_rich to floatArrayOf(100f, 160f, 200f, 220f, 280f, 260f, 240f, 200f, 150f, 50f),
        R.string.eq_preset_dolby_focused to floatArrayOf(-300f, -50f, 130f, 180f, 220f, 120f, 140f, 100f, -50f, -300f),
    )

    val diracPresets = listOf(
        R.string.eq_preset_dirac_music to floatArrayOf(200f, 140f, 80f, 0f, 30f, 80f, 140f, 200f, 280f, 350f),
        R.string.eq_preset_dirac_movie to floatArrayOf(300f, 250f, 150f, 0f, 70f, 120f, 180f, 250f, 320f, 400f),
        R.string.eq_preset_dirac_game to floatArrayOf(150f, 250f, 200f, 0f, 80f, 150f, 300f, 450f, 400f, 280f),
    )

    val customProfiles by viewModel.customProfiles.collectAsState()
    var showManageDialog by remember { mutableStateOf(false) }

    if (showManageDialog) {
        ManagePresetsDialog(
            customProfiles = customProfiles,
            onDismiss = { showManageDialog = false },
            onDeleteSelected = { ids ->
                viewModel.deleteProfiles(ids)
                showManageDialog = false
            }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp), 
    ) {
        CircularEqControl(
            bass = bass, mid = mid, treble = treble,
            enabled = enabled,
            onBassChange = { bass = it; applyTriangle() },
            onMidChange = { mid = it; applyTriangle() },
            onTrebleChange = { treble = it; applyTriangle() },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(horizontal = 8.dp)
                .aspectRatio(1f),
        )

        AnimatedVisibility(
            visible = isDirty && enabled,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedButton(
                onClick = onSaveClick,
                modifier = Modifier.padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.eq_save),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (customProfiles.isNotEmpty()) {
            PresetSection(
                title = stringResource(R.string.eq_label_custom),
                presets = customProfiles.map { -1 to it.bands.map { it.gain.toFloat() * 50f }.toFloatArray() },
                presetNames = customProfiles.map { it.name },
                enabled = enabled,
                viewModel = viewModel,
                bandGains = bandGains,
                onEditClick = { showManageDialog = true }
            )
        }

        echoPresets.chunked(4).forEach { chunk ->
            PresetSection(
                title = if (echoPresets.first() in chunk) stringResource(R.string.eq_label_echo) else "",
                presets = chunk,
                enabled = enabled,
                viewModel = viewModel,
                bandGains = bandGains
            )
        }
        PresetSection(stringResource(R.string.eq_label_dolby), dolbyPresets, null, enabled, viewModel, bandGains)
        PresetSection(stringResource(R.string.eq_label_dirac), diracPresets, null, enabled, viewModel, bandGains)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetSection(
    title: String,
    presets: List<Pair<Int, FloatArray>>,
    presetNames: List<String>? = null,
    enabled: Boolean,
    viewModel: AxionEqViewModel,
    bandGains: FloatArray,
    onEditClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                
                if (onEditClick != null && enabled) {
                    androidx.compose.material3.IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            presets.forEachIndexed { index, (nameRes, bands) ->
                val name = presetNames?.getOrNull(index) ?: stringResource(nameRes)
                val isSelected = remember(bandGains) {
                    bandGains.size == bands.size && 
                    bandGains.zip(bands).all { (g, b) -> abs(g - b) < 10f }
                }

                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { if (enabled) viewModel.setBandsGains(bands) },
                    enabled = enabled,
                    shapes = when {
                        presets.size == 1 || index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == presets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 320.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = blockShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        
                        Text(
                            text = stringResource(R.string.eq_save_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text(stringResource(R.string.eq_save_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = actionShape,
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    
                    OutlinedButton(
                        onClick = { if (name.isNotBlank()) onSave(name) },
                        enabled = name.isNotBlank(),
                        shape = actionShape,
                    ) {
                        Text(text = stringResource(R.string.eq_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedEqMode(
    bandGains: FloatArray,
    bandQ: FloatArray,
    enabled: Boolean,
    onBandChange: (Int, Float) -> Unit,
    onBandQChange: (Int, Float) -> Unit,
    onReset: () -> Unit,
) {
    val bandLabels = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (band in 0..9) {
                    EqBandSlider(
                        label = bandLabels[band],
                        value = bandGains[band],
                        qValue = bandQ.getOrElse(band) { 1.41f },
                        enabled = enabled,
                        onValueChange = { onBandChange(band, it) },
                        onQChange = { onBandQChange(band, it) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedButton(onClick = onReset) {
                Icon(Icons.Rounded.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.eq_reset))
            }
        }
    }
}

@Composable
private fun EqBandSlider(
    label: String,
    value: Float,
    qValue: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onQChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "%+.1f".format(value / 50f),
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier.height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = -600f..600f,
                enabled = enabled,
                modifier = Modifier
                    .width(200.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                -placeable.width / 2 + placeable.height / 2,
                                placeable.width / 2 - placeable.height / 2
                            )
                        }
                    }
                    .graphicsLayer { rotationZ = -90f },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Per-band Q (bandwidth/quality) — the Poweramp-style "how narrow/
        // wide is this band's bell curve" control, previously fixed at 1.41
        // for every band regardless of what the user set gain to. Kept as a
        // compact horizontal slider under each vertical fader rather than a
        // second full-height slider, since Q is a secondary/occasional
        // adjustment compared to gain.
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Q %.1f".format(qValue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Slider(
            value = qValue,
            onValueChange = onQChange,
            valueRange = 0.4f..4.0f,
            enabled = enabled,
            modifier = Modifier.width(56.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagePresetsDialog(
    customProfiles: List<SavedEQProfile>,
    onDismiss: () -> Unit,
    onDeleteSelected: (List<String>) -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    
    val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 320.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = blockShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.eq_manage_presets),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        if (customProfiles.isEmpty()) {
                            Text(
                                text = stringResource(R.string.eq_no_custom_presets),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(customProfiles) { profile ->
                                    val isSelected = selectedIds.contains(profile.id)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable {
                                                if (isSelected) selectedIds.remove(profile.id)
                                                else selectedIds.add(profile.id)
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                if (it == true) selectedIds.add(profile.id)
                                                else selectedIds.remove(profile.id)
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = profile.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss, shape = actionShape) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    
                    if (selectedIds.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onDeleteSelected(selectedIds.toList()) },
                            shape = actionShape,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(text = stringResource(R.string.eq_delete_selected))
                        }
                    }
                }
            }
        }
    }
}
