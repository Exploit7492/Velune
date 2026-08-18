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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
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
    // bandQ used to be collected here for the removed Advanced tab's
    // per-band Q sliders. AxionEqViewModel.bandQ/setBandQ stay defined —
    // Simple's applyToService()/saveCustomProfile() still read _bandQ.value
    // when building the bands it pushes to the DSP — just nothing in this
    // screen edits it directly anymore now that Advanced is gone.
    val mode by viewModel.mode.collectAsState()
    val preampDb by viewModel.preampDb.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val bassBoostDb by viewModel.bassBoostDb.collectAsState()
    val stereoWidth by viewModel.stereoWidth.collectAsState()
    val limiterEnabled by viewModel.limiterEnabled.collectAsState()
    val limiterCeilingDb by viewModel.limiterCeilingDb.collectAsState()
    val tempoRatio by viewModel.tempoRatio.collectAsState()
    val pitchSemitones by viewModel.pitchSemitones.collectAsState()
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
    val spectrumSnapshot by viewModel.spectrumSnapshot.collectAsState()
    val spectrumLabels by viewModel.spectrumLabels.collectAsState()
    var spectrumShown by remember { mutableStateOf(false) }

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
                // Advanced used to be a third tab here (mode == 1), folded
                // into Master below. Deliberately NOT renumbering Master
                // from 2 down to 1 — mode is a raw Int persisted in
                // SharedPreferences ("mode"), so a device that had Master
                // (2) or the old Advanced (1) selected before this update
                // both still fall through to the `else` branch in the
                // `when` below and land on Master, with zero migration
                // step needed. Don't "clean up" this to 0/1 later without
                // adding an actual prefs migration.
                ToggleButton(
                    checked = mode == 0,
                    onCheckedChange = { viewModel.setMode(0) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) {
                    Text(stringResource(R.string.eq_simple))
                }
                ToggleButton(
                    checked = mode != 0,
                    onCheckedChange = { viewModel.setMode(1) },
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
                            // Stops the audio-thread tap and the UI poll
                            // loop the moment this Column leaves
                            // composition — leaving the Master tab (the
                            // AnimatedContent above swaps it out), closing
                            // the EQ screen, or backgrounding the app all
                            // go through this, so the analyzer never keeps
                            // running unattended. Re-armed on re-entry only
                            // if the toggle itself was left on.
                            DisposableEffect(Unit) {
                                onDispose { viewModel.setSpectrumVisible(false) }
                            }
                            LaunchedEffect(spectrumShown, enabled) {
                                viewModel.setSpectrumVisible(spectrumShown && enabled)
                            }
                            SpectrumSection(
                                enabled = enabled,
                                shown = spectrumShown,
                                snapshot = spectrumSnapshot,
                                labels = spectrumLabels,
                                onShownChange = { spectrumShown = it },
                            )
                            MasterBusControls(
                                preampDb = preampDb,
                                balance = balance,
                                bassBoostDb = bassBoostDb,
                                stereoWidth = stereoWidth,
                                limiterEnabled = limiterEnabled,
                                limiterCeilingDb = limiterCeilingDb,
                                tempoRatio = tempoRatio,
                                pitchSemitones = pitchSemitones,
                                enabled = enabled,
                                accentColor = MaterialTheme.colorScheme.primary,
                                onPreampChange = { viewModel.setPreampDb(it) },
                                onBalanceChange = { viewModel.setBalance(it) },
                                onBassBoostChange = { viewModel.setBassBoostDb(it) },
                                onStereoWidthChange = { viewModel.setStereoWidth(it) },
                                onLimiterEnabledChange = { viewModel.setLimiterEnabled(it) },
                                onLimiterCeilingChange = { viewModel.setLimiterCeilingDb(it) },
                                onTempoChange = { viewModel.setTempoRatio(it) },
                                onPitchChange = { viewModel.setPitchSemitones(it) },
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
    tempoRatio: Float,
    pitchSemitones: Float,
    enabled: Boolean,
    accentColor: Color,
    onPreampChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit,
    onBassBoostChange: (Float) -> Unit,
    onStereoWidthChange: (Float) -> Unit,
    onLimiterEnabledChange: (Boolean) -> Unit,
    onLimiterCeilingChange: (Float) -> Unit,
    onTempoChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
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
                size = 88.dp,
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
                size = 68.dp,
            )
            RotaryKnob(
                value = (bassBoostDb / 12f).coerceIn(0f, 1f),
                onValueChange = { onBassBoostChange((it * 12f).coerceIn(0f, 12f)) },
                label = stringResource(R.string.eq_bass_boost),
                valueLabel = "+%.1f dB".format(bassBoostDb),
                enabled = enabled,
                accentColor = accentColor,
                size = 76.dp,
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
                size = 76.dp,
            )
            RotaryKnob(
                value = ((limiterCeilingDb + 12f) / 12f).coerceIn(0f, 1f),
                onValueChange = { onLimiterCeilingChange((it * 12f - 12f).coerceIn(-12f, 0f)) },
                label = stringResource(R.string.eq_limiter_ceiling),
                valueLabel = "%.1f dB".format(limiterCeilingDb),
                enabled = enabled && limiterEnabled,
                accentColor = accentColor,
                size = 64.dp,
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
        // Pro-level independent tempo/pitch (WSOLA time-stretch + resampler,
        // see TempoPitchAudioProcessor) — genuinely orthogonal knobs, unlike
        // the classic single "speed" slider most players offer: tempo alone
        // changes duration without touching pitch, pitch alone transposes
        // without touching duration, same as Neutron/Poweramp's tempo/pitch
        // tools. This replaced the old Sonic-backed tempo/pitch dialog in the
        // player menu, which now drives the same EqTempoKey/EqPitchSemitonesKey
        // this does (see PlayerMenu.kt's TempoPitchDialog).
        PreferenceGroupTitle(title = stringResource(R.string.eq_tempo_pitch))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RotaryKnob(
                value = ((tempoRatio - 0.25f) / 2.75f).coerceIn(0f, 1f),
                onValueChange = { onTempoChange((0.25f + it * 2.75f).coerceIn(0.25f, 3f)) },
                label = stringResource(R.string.eq_tempo),
                valueLabel = "x%.2f".format(tempoRatio),
                enabled = enabled,
                accentColor = accentColor,
                size = 80.dp,
            )
            RotaryKnob(
                value = ((pitchSemitones + 12f) / 24f).coerceIn(0f, 1f),
                onValueChange = { onPitchChange((it * 24f - 12f).coerceIn(-12f, 12f)) },
                label = stringResource(R.string.eq_pitch),
                valueLabel = "%+.0f st".format(pitchSemitones),
                enabled = enabled,
                accentColor = accentColor,
                size = 80.dp,
            )
        }
    }
}

/**
 * Spectrum analyzer — a toggleable live view of the fully-processed signal
 * (SpectrumAnalyzer taps post-limiter, see CustomEqualizerAudioProcessor),
 * sitting at the top of the Master tab above the rotary knobs it visualizes
 * the effect of. Off by default: the switch is what actually starts the
 * audio-thread tap and the UI poll loop (AxionEqViewModel.setSpectrumVisible)
 * — there's a real per-sample cost to the analysis, so it only runs while
 * this section is both expanded and the equalizer itself is on.
 *
 * Unverified beyond compiling, same caveat as the rest of this screen (see
 * HANDOVER.md §3): the DSP behind it (SpectrumAnalyzer) was checked with a
 * JVM harness — windowing, dB scaling, log-frequency binning, a real 1kHz
 * tone landing in the expected bar, ballistics (instant attack, timed
 * release) and peak-hold decaying on schedule against simulated elapsed
 * audio time — but this Canvas's actual on-device rendering/frame pacing
 * has not been.
 */
@Composable
private fun SpectrumSection(
    enabled: Boolean,
    shown: Boolean,
    snapshot: com.nikhil.yt.eq.audio.SpectrumSnapshot,
    labels: List<Pair<String, Int>>,
    onShownChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.eq_spectrum),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (enabled) stringResource(R.string.eq_spectrum_summary)
                    else stringResource(R.string.eq_spectrum_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = shown && enabled,
                onCheckedChange = onShownChange,
                enabled = enabled,
            )
        }
        AnimatedVisibility(visible = shown && enabled) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SpectrumBarsCanvas(
                    snapshot = snapshot,
                    barColor = MaterialTheme.colorScheme.primary,
                    peakColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(120.dp),
                )
                SpectrumLabelRow(
                    labels = labels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/**
 * Draws the analyzer's bar levels (already ballistics-smoothed by
 * [com.nikhil.yt.eq.audio.SpectrumAnalyzer] — instant attack, timed
 * release) plus a peak-hold cap per bar. This composable does no
 * smoothing of its own: both curves it draws are already correctly paced
 * against real audio time by the DSP layer, so redoing any of that here
 * (like the old frame-rate-coupled decay this replaced) would just double
 * up on it and desync from the analyzer's own timing.
 *
 * Styled to match the rotary knobs: each bar is a vertical neon gradient
 * (dim at the baseline, bright at its current level) rather than a flat
 * fill, and the peak cap gets the same layered-glow treatment as the
 * knobs' indicator dot, so a hot transient visibly lights up instead of
 * just leaving a thin line.
 */
@Composable
private fun SpectrumBarsCanvas(
    snapshot: com.nikhil.yt.eq.audio.SpectrumSnapshot,
    barColor: Color,
    peakColor: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val levels = snapshot.levels
        val peaks = snapshot.peaks
        if (levels.isEmpty()) return@Canvas
        val barCount = levels.size
        val gap = 3.dp.toPx()
        val totalGap = gap * (barCount - 1)
        val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(1f)
        val peakCapHeight = 2.dp.toPx()
        for (i in 0 until barCount) {
            val level = levels[i].coerceIn(0f, 1f)
            val barHeight = size.height * level
            val x = i * (barWidth + gap)
            val top = size.height - barHeight

            // Vertical neon gradient: dim near the baseline, bright at the
            // current level — reads as a bar that's "lit" from the level
            // it's actually at, the same idea as the knobs' under-glow.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        barColor.copy(alpha = 0.9f),
                        barColor.copy(alpha = 0.35f + 0.4f * level),
                    ),
                    startY = top,
                    endY = size.height,
                ),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
            )
            // A faint bright cap right at the bar's own top edge, separate
            // from the peak-hold line below — makes the live level itself
            // read as slightly luminous rather than a flat-topped block.
            drawRect(
                color = barColor.copy(alpha = 0.6f),
                topLeft = Offset(x, top),
                size = Size(barWidth, (1.5.dp.toPx()).coerceAtMost(barHeight)),
            )

            // Peak-hold cap: a thin bright line at the bar's recent peak,
            // trailing above the bar as it releases — the standard RTA
            // "peak indicator" look (Neutron/Poweramp both draw this).
            // Given a soft halo above/below it so it reads as a small glow
            // riding the bar rather than a plain ruled line.
            val peak = peaks.getOrElse(i) { level }.coerceIn(0f, 1f)
            if (peak > level + 0.01f) {
                val peakY = size.height - size.height * peak
                val capCenter = Offset(x + barWidth / 2f, peakY)
                drawRect(
                    color = peakColor.copy(alpha = 0.25f),
                    topLeft = Offset(x, peakY - peakCapHeight * 2f),
                    size = Size(barWidth, peakCapHeight * 4f),
                )
                drawRect(
                    color = peakColor,
                    topLeft = Offset(x, peakY - peakCapHeight / 2f),
                    size = Size(barWidth, peakCapHeight),
                )
                drawCircle(
                    color = peakColor.copy(alpha = 0.5f),
                    radius = barWidth * 0.7f,
                    center = capCenter,
                )
            }
        }
    }
}

/**
 * Axis labels ("20", "100", "1k", ...) under the bars they correspond to
 * — the frequencies in [com.nikhil.yt.eq.audio.SpectrumAnalyzer.LABEL_FREQUENCIES_HZ],
 * each already resolved to a bar index by
 * [AxionEqViewModel.setSpectrumVisible]. Approximates the bar canvas's own
 * geometry with equal-weight cells rather than sharing exact pixel math
 * with it — close enough for axis labels (same loose alignment
 * Poweramp's own graphic-EQ band labels use), and avoids a second Layout
 * pass just to line up text under a bar-gap canvas exactly.
 */
@Composable
private fun SpectrumLabelRow(
    labels: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val labelByBar = remember(labels) { labels.toMap() }
    Row(modifier = modifier) {
        for (bar in 0 until com.nikhil.yt.eq.audio.SpectrumAnalyzer.BAR_COUNT) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val text = labelByBar.entries.firstOrNull { it.value == bar }?.key
                if (text != null) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
    var showPresetPicker by remember { mutableStateOf(false) }

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

    // Single categorized preset picker, replacing what used to be four+
    // rows of ToggleButton chips (custom / echo / dolby / dirac) stacked
    // inline down the whole tab — asked to be one button at the top
    // instead. Selecting a preset here calls the exact same
    // viewModel.setBandsGains(bands) every chip used to, so which presets
    // exist and how a match is detected against the current bands is
    // unchanged; only how you get to them changed.
    val allPresetGroups = buildList {
        if (customProfiles.isNotEmpty()) {
            add(
                stringResource(R.string.eq_label_custom) to
                    customProfiles.map { it.name to it.bands.map { b -> b.gain.toFloat() * 50f }.toFloatArray() }
            )
        }
        add(stringResource(R.string.eq_label_echo) to echoPresets.map { (res, bands) -> stringResource(res) to bands })
        add(stringResource(R.string.eq_label_dolby) to dolbyPresets.map { (res, bands) -> stringResource(res) to bands })
        add(stringResource(R.string.eq_label_dirac) to diracPresets.map { (res, bands) -> stringResource(res) to bands })
    }
    val flatPresets = remember(allPresetGroups) { allPresetGroups.flatMap { it.second } }
    val activePresetName = remember(bandGains, flatPresets) {
        flatPresets.firstOrNull { (_, bands) ->
            bandGains.size == bands.size && bandGains.zip(bands).all { (g, b) -> abs(g - b) < 10f }
        }?.first
    }

    if (showPresetPicker) {
        SimplePresetPickerSheet(
            groups = allPresetGroups,
            activePresetName = activePresetName,
            onPresetClick = { bands ->
                viewModel.setBandsGains(bands)
                showPresetPicker = false
            },
            onManageClick = {
                showPresetPicker = false
                showManageDialog = true
            },
            hasCustomProfiles = customProfiles.isNotEmpty(),
            onDismiss = { showPresetPicker = false },
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp), 
    ) {
        OutlinedButton(
            onClick = { showPresetPicker = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = activePresetName ?: stringResource(R.string.eq_presets),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }

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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimplePresetPickerSheet(
    groups: List<Pair<String, List<Pair<String, FloatArray>>>>,
    activePresetName: String?,
    onPresetClick: (FloatArray) -> Unit,
    onManageClick: () -> Unit,
    hasCustomProfiles: Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val customLabel = stringResource(R.string.eq_label_custom)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.eq_presets),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            groups.forEach { (title, presets) ->
                val isCustomGroup = hasCustomProfiles && title == customLabel
                item(key = "header_$title") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (isCustomGroup) {
                            androidx.compose.material3.IconButton(
                                onClick = onManageClick,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                itemsIndexed(presets, key = { index, item -> "preset_${title}_${index}_${item.first}" }) { _, (name, bands) ->
                    val isSelected = activePresetName == name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPresetClick(bands) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
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
