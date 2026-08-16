package com.nikhil.yt.ui.screens.equalizer.axion

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.constants.EqBalanceKey
import com.nikhil.yt.constants.EqBassBoostKey
import com.nikhil.yt.constants.EqBandQPrefix
import com.nikhil.yt.constants.EqConvolutionEnabledKey
import com.nikhil.yt.constants.EqConvolutionIrNameKey
import com.nikhil.yt.constants.EqConvolutionIrPathKey
import com.nikhil.yt.constants.EqLimiterCeilingKey
import com.nikhil.yt.constants.EqLimiterEnabledKey
import com.nikhil.yt.constants.EqStereoWidthKey
import com.nikhil.yt.constants.ParametricEQEnabledKey
import com.nikhil.yt.R
import com.nikhil.yt.eq.EqualizerService
import com.nikhil.yt.eq.data.EQProfileRepository
import com.nikhil.yt.eq.data.FilterType
import com.nikhil.yt.eq.data.ImpulseResponseLoader
import com.nikhil.yt.eq.data.ParametricEQBand
import com.nikhil.yt.eq.data.SavedEQProfile
import com.nikhil.yt.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

/**
 * Metadata surfaced to the Convolution UI about the currently-loaded
 * impulse response. Read once at import (and again at app start, to
 * re-validate the persisted copy) — see
 * [AxionEqViewModel.importImpulseResponse] and the convolution restore
 * block in [AxionEqViewModel.init]. Deliberately doesn't carry the parsed
 * sample data itself (that lives only inside the DSP layer, via
 * [EqualizerService.loadImpulseResponse]) — this is display-only.
 */
data class ImpulseResponseInfo(
    val displayName: String,
    val durationSeconds: Float,
    val channels: Int,
    val sampleRateHz: Int,
)

@HiltViewModel
class AxionEqViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val equalizerService: EqualizerService,
    private val eqProfileRepository: EQProfileRepository
) : ViewModel() {

    private val prefs = context.getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE)

    // "Enabled" is shared with the Custom tab's ParametricEqEditor/EQViewModel via the
    // same ParametricEQEnabledKey DataStore entry — Simple, Advanced, and Custom used
    // to each track their own independent on/off flag (this one in SharedPreferences,
    // Custom's in DataStore), so the three tabs could silently disagree about whether
    // the equalizer was even on, and switching tabs could look like the EQ "stopped
    // working" even though whichever tab you'd last touched was still applying fine.
    // One flag now, read/written the same way both places.
    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled = _enabled.asStateFlow()

    private val bandFrequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    
    private val _bandGains = MutableStateFlow(
        FloatArray(10) { prefs.getFloat("band_$it", 0f) }
    )
    val bandGains = _bandGains.asStateFlow()

    // Per-band Q (bandwidth/quality) for the Advanced tab — Poweramp-style
    // per-band control, independent of gain. Every band defaulted to a
    // fixed 1.41 before; now each is individually adjustable and persisted
    // the same per-band way gains already are.
    private val _bandQ = MutableStateFlow(
        FloatArray(10) { prefs.getFloat("$EqBandQPrefix$it", 1.41f) }
    )
    val bandQ = _bandQ.asStateFlow()

    private val _mode = MutableStateFlow(prefs.getInt("mode", 0)) 
    val mode = _mode.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

    // ─── Master bus (Master tab rotary knobs) ──────────────────────────────────
    // Preamp affects the active profile (Simple/Advanced/Custom all apply it the
    // same way, via SavedEQProfile.preamp). Balance and bass boost are applied on
    // top of whichever profile is active, at the audio-processor level, the same
    // way a mixer's master bus sits above individual channel EQ — see
    // EqualizerService.setBalance/setBassBoost.
    private val _preampDb = MutableStateFlow(prefs.getFloat("preamp_db", 0f))
    val preampDb = _preampDb.asStateFlow()

    private val _balance = MutableStateFlow(0f)
    val balance = _balance.asStateFlow()

    private val _bassBoostDb = MutableStateFlow(0f)
    val bassBoostDb = _bassBoostDb.asStateFlow()

    // Stereo width and limiter — additive master-bus controls, same
    // pending/apply-immediately pattern as balance/bass boost above. See
    // CustomEqualizerAudioProcessor for why these don't duplicate balance
    // or bass boost: width reshapes the stereo image (mid/side), the
    // limiter caps final output level after every other stage.
    private val _stereoWidth = MutableStateFlow(1f)
    val stereoWidth = _stereoWidth.asStateFlow()

    private val _limiterEnabled = MutableStateFlow(false)
    val limiterEnabled = _limiterEnabled.asStateFlow()

    private val _limiterCeilingDb = MutableStateFlow(-0.3f)
    val limiterCeilingDb = _limiterCeilingDb.asStateFlow()

    // Convolution (impulse-response) tone shaping — the UI-facing half of
    // the engine built in the previous session. The DSP itself
    // (EqualizerService.loadImpulseResponse/setConvolutionEnabled) already
    // existed; nothing here duplicates it, this just drives it from a
    // picked file and remembers the result. currentIrFile is the
    // app-private copy backing whatever's in _impulseResponseInfo — kept
    // as a plain var (not state) since the UI only needs to know *that*
    // something is loaded and its metadata, not the File itself.
    private var currentIrFile: File? = null

    private val _convolutionEnabled = MutableStateFlow(false)
    val convolutionEnabled = _convolutionEnabled.asStateFlow()

    private val _impulseResponseInfo = MutableStateFlow<ImpulseResponseInfo?>(null)
    val impulseResponseInfo = _impulseResponseInfo.asStateFlow()

    private val _convolutionImporting = MutableStateFlow(false)
    val convolutionImporting = _convolutionImporting.asStateFlow()

    private val _convolutionImportError = MutableStateFlow<String?>(null)
    val convolutionImportError = _convolutionImportError.asStateFlow()

    val customProfiles = eqProfileRepository.profiles.map { profiles ->
        profiles.filter { it.isCustom && it.id != "echo_tuning" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            context.dataStore.data
                .map { it[ParametricEQEnabledKey] ?: prefs.getBoolean("enabled", false) }
                .collect { fromDataStore ->
                    if (fromDataStore != _enabled.value) {
                        _enabled.value = fromDataStore
                    }
                }
        }
        viewModelScope.launch {
            val prefsSnapshot = context.dataStore.data.first()
            _balance.value = prefsSnapshot[EqBalanceKey] ?: 0f
            _bassBoostDb.value = prefsSnapshot[EqBassBoostKey] ?: 0f
            _stereoWidth.value = prefsSnapshot[EqStereoWidthKey] ?: 1f
            _limiterEnabled.value = prefsSnapshot[EqLimiterEnabledKey] ?: false
            _limiterCeilingDb.value = prefsSnapshot[EqLimiterCeilingKey] ?: -0.3f
            equalizerService.setBalance(_balance.value.toDouble())
            equalizerService.setBassBoost(_bassBoostDb.value.toDouble())
            equalizerService.setStereoWidth(_stereoWidth.value.toDouble())
            equalizerService.setLimiter(_limiterEnabled.value, _limiterCeilingDb.value.toDouble())
        }
        // Restore a previously-imported impulse response, off the main
        // thread since it means re-reading and re-parsing a WAV file (IR
        // files are small, per ImpulseResponseLoader's own doc, but still
        // more work than a prefs read). Re-parsing rather than trusting
        // the persisted metadata verifies the copy is still valid — if
        // app storage was cleared externally or the file's gone, this
        // just leaves convolution unloaded instead of claiming a file
        // that no longer works.
        viewModelScope.launch(Dispatchers.IO) {
            val prefsSnapshot = context.dataStore.data.first()
            val irPath = prefsSnapshot[EqConvolutionIrPathKey] ?: return@launch
            val irName = prefsSnapshot[EqConvolutionIrNameKey] ?: irPath.substringAfterLast('/')
            val persistedEnabled = prefsSnapshot[EqConvolutionEnabledKey] ?: false
            val file = File(irPath)
            if (!file.exists()) return@launch
            val ir = runCatching {
                FileInputStream(file).use { ImpulseResponseLoader.load(it, targetSampleRate = 48000) }
            }.getOrNull() ?: return@launch

            currentIrFile = file
            _impulseResponseInfo.value = ImpulseResponseInfo(
                displayName = irName,
                durationSeconds = ir.left.size.toFloat() / ir.sampleRate,
                channels = if (ir.right != null) 2 else 1,
                sampleRateHz = ir.sampleRate,
            )
            _convolutionEnabled.value = persistedEnabled
            equalizerService.loadImpulseResponse(file)
            equalizerService.setConvolutionEnabled(persistedEnabled)
        }
        if (_enabled.value) {
            applyToService()
        }
    }

    fun setPreampDb(db: Float) {
        _preampDb.value = db
        prefs.edit().putFloat("preamp_db", db).apply()
        if (_enabled.value) {
            applyToService()
        }
    }

    fun setBalance(value: Float) {
        _balance.value = value
        viewModelScope.launch {
            context.dataStore.edit { it[EqBalanceKey] = value }
        }
        equalizerService.setBalance(value.toDouble())
    }

    fun setBassBoostDb(db: Float) {
        _bassBoostDb.value = db
        viewModelScope.launch {
            context.dataStore.edit { it[EqBassBoostKey] = db }
        }
        equalizerService.setBassBoost(db.toDouble())
    }

    fun setStereoWidth(value: Float) {
        _stereoWidth.value = value
        viewModelScope.launch {
            context.dataStore.edit { it[EqStereoWidthKey] = value }
        }
        equalizerService.setStereoWidth(value.toDouble())
    }

    fun setLimiterEnabled(enabled: Boolean) {
        _limiterEnabled.value = enabled
        viewModelScope.launch {
            context.dataStore.edit { it[EqLimiterEnabledKey] = enabled }
        }
        equalizerService.setLimiter(enabled, _limiterCeilingDb.value.toDouble())
    }

    fun setLimiterCeilingDb(db: Float) {
        _limiterCeilingDb.value = db
        viewModelScope.launch {
            context.dataStore.edit { it[EqLimiterCeilingKey] = db }
        }
        equalizerService.setLimiter(_limiterEnabled.value, db.toDouble())
    }

    /**
     * Imports a picked impulse-response file: copies it out of the
     * (possibly permission-revocable, stream-only) [uri] into app-private
     * storage — the same import-time-copy pattern Wavelet/Neutron use —
     * then validates it's actually a readable WAV before touching any
     * state, so a bad file surfaces an error instead of silently
     * "loading" something the DSP layer will just fail on later. On
     * success, replaces any previously-imported IR and enables
     * convolution immediately, matching how loading a correction curve
     * behaves in those apps.
     */
    fun importImpulseResponse(uri: Uri, displayName: String) {
        _convolutionImportError.value = null
        _convolutionImporting.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "impulse_responses").apply { mkdirs() }
                    val dest = File(dir, "ir_${System.currentTimeMillis()}.wav")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw java.io.IOException("Could not open the selected file")
                    validateIrFile(dest)
                }
            }
            adoptImportResult(result, displayName)
            _convolutionImporting.value = false
        }
    }

    /**
     * Parses/validates a candidate IR file already sitting on disk —
     * shared by both [importImpulseResponse] (SAF-picked file, already
     * copied to [file]) and [importFromPresetLibrary] (already downloaded
     * to [file]). Doesn't touch any state; just throws on a bad file or
     * returns the parsed result, same "validate before adopting" shape
     * both callers rely on.
     */
    private fun validateIrFile(file: File): Pair<File, com.nikhil.yt.eq.data.ImpulseResponse> {
        // targetSampleRate here only affects the resample step, not
        // validation or the metadata read back below — the real device
        // rate is applied again when EqualizerService hands the file to
        // the DSP layer.
        val ir = FileInputStream(file).use {
            ImpulseResponseLoader.load(it, targetSampleRate = 48000)
        }
        return file to ir
    }

    /**
     * Shared success/failure handling for both import paths: on success,
     * drops the previous IR copy, updates all convolution state/prefs,
     * and pushes the new file into the DSP layer; on failure, surfaces an
     * error and leaves whatever was previously loaded untouched.
     */
    private fun adoptImportResult(
        result: Result<Pair<File, com.nikhil.yt.eq.data.ImpulseResponse>>,
        displayName: String,
    ) {
        result.onSuccess { (file, ir) ->
            // Now that the new IR has validated, drop the old copy —
            // otherwise app storage accumulates one .wav per import.
            currentIrFile?.takeIf { it.exists() && it != file }?.delete()
            currentIrFile = file
            _impulseResponseInfo.value = ImpulseResponseInfo(
                displayName = displayName,
                durationSeconds = ir.left.size.toFloat() / ir.sampleRate,
                channels = if (ir.right != null) 2 else 1,
                sampleRateHz = ir.sampleRate,
            )
            _convolutionEnabled.value = true
            viewModelScope.launch {
                context.dataStore.edit {
                    it[EqConvolutionIrPathKey] = file.absolutePath
                    it[EqConvolutionIrNameKey] = displayName
                    it[EqConvolutionEnabledKey] = true
                }
            }
            equalizerService.loadImpulseResponse(file)
            equalizerService.setConvolutionEnabled(true)
        }
        result.onFailure { e ->
            _convolutionImportError.value = e.message?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.eq_convolution_error_generic)
        }
    }

    // --- Preset IR library (ASH-IR-Dataset, browsed/downloaded on demand) ---
    // See PresetIrRepository for the licensing note (CC BY-NC-SA 4.0 — the
    // dataset's license, not Velune's). This section is UI-facing plumbing
    // only; the actual browse-and-download logic lives in the repository
    // so it can be unit-tested without Android.

    private val presetIrRepository = com.nikhil.yt.eq.data.PresetIrRepository(context)

    private val _presetManufacturers =
        MutableStateFlow<List<com.nikhil.yt.eq.data.PresetManufacturer>>(emptyList())
    val presetManufacturers = _presetManufacturers.asStateFlow()

    private val _presetModels =
        MutableStateFlow<List<com.nikhil.yt.eq.data.PresetHeadphoneModel>>(emptyList())
    val presetModels = _presetModels.asStateFlow()

    private val _presetSelectedManufacturer =
        MutableStateFlow<com.nikhil.yt.eq.data.PresetManufacturer?>(null)
    val presetSelectedManufacturer = _presetSelectedManufacturer.asStateFlow()

    private val _presetBrowserLoading = MutableStateFlow(false)
    val presetBrowserLoading = _presetBrowserLoading.asStateFlow()

    private val _presetBrowserError = MutableStateFlow<String?>(null)
    val presetBrowserError = _presetBrowserError.asStateFlow()

    /** Currently downloading model's display name, or null if idle — lets
     *  the UI show "Downloading AKG K240…" on the specific row tapped. */
    private val _presetDownloadingName = MutableStateFlow<String?>(null)
    val presetDownloadingName = _presetDownloadingName.asStateFlow()

    fun loadPresetManufacturers(forceRefresh: Boolean = false) {
        _presetBrowserError.value = null
        _presetBrowserLoading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { presetIrRepository.listManufacturers(forceRefresh) }
            }
            result.onSuccess { _presetManufacturers.value = it }
            result.onFailure { e ->
                _presetBrowserError.value = e.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.eq_convolution_presets_error_generic)
            }
            _presetBrowserLoading.value = false
        }
    }

    fun openPresetManufacturer(
        manufacturer: com.nikhil.yt.eq.data.PresetManufacturer,
        forceRefresh: Boolean = false,
    ) {
        _presetSelectedManufacturer.value = manufacturer
        _presetModels.value = emptyList()
        _presetBrowserError.value = null
        _presetBrowserLoading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { presetIrRepository.listModels(manufacturer, forceRefresh) }
            }
            result.onSuccess { _presetModels.value = it }
            result.onFailure { e ->
                _presetBrowserError.value = e.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.eq_convolution_presets_error_generic)
            }
            _presetBrowserLoading.value = false
        }
    }

    fun closePresetManufacturer() {
        _presetSelectedManufacturer.value = null
        _presetModels.value = emptyList()
        _presetBrowserError.value = null
    }

    /**
     * Downloads [model] (or reuses a previously-downloaded local copy —
     * same cache file per model, keyed off its filename, so re-picking a
     * preset you've already tried doesn't re-download it), then routes
     * through the exact same validate-then-adopt path as a manually
     * picked file. If validation fails here, that's this integration
     * surfacing a real bug rather than something to paper over — see the
     * 24-bit PCM fix earlier in this patch, found the same way.
     */
    fun importFromPresetLibrary(model: com.nikhil.yt.eq.data.PresetHeadphoneModel) {
        _convolutionImportError.value = null
        _convolutionImporting.value = true
        _presetDownloadingName.value = model.displayName
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "impulse_responses").apply { mkdirs() }
                    val safeName = model.fileName.map { c ->
                        if (c.isLetterOrDigit() || c == '.' || c == '_' || c == '-') c else '_'
                    }.joinToString("")
                    val dest = File(dir, "preset_$safeName")
                    if (!dest.exists()) {
                        presetIrRepository.download(model, dest)
                    }
                    try {
                        validateIrFile(dest)
                    } catch (e: Exception) {
                        // Don't let a corrupt/partial cached copy perma-fail
                        // this preset — clear it so the next attempt
                        // re-downloads instead of reusing the bad file.
                        dest.delete()
                        throw e
                    }
                }
            }
            adoptImportResult(result, model.displayName)
            _presetDownloadingName.value = null
            _convolutionImporting.value = false
        }
    }

    fun setConvolutionEnabled(enabled: Boolean) {
        _convolutionEnabled.value = enabled
        viewModelScope.launch {
            context.dataStore.edit { it[EqConvolutionEnabledKey] = enabled }
        }
        equalizerService.setConvolutionEnabled(enabled)
    }

    fun clearImpulseResponse() {
        currentIrFile?.takeIf { it.exists() }?.delete()
        currentIrFile = null
        _impulseResponseInfo.value = null
        _convolutionEnabled.value = false
        _convolutionImportError.value = null
        viewModelScope.launch {
            context.dataStore.edit {
                it.remove(EqConvolutionIrPathKey)
                it.remove(EqConvolutionIrNameKey)
                it[EqConvolutionEnabledKey] = false
            }
        }
        equalizerService.clearImpulseResponse()
    }

    fun setBandQ(index: Int, q: Float) {
        val newQ = _bandQ.value.copyOf()
        newQ[index] = q
        _bandQ.value = newQ
        prefs.edit().putFloat("$EqBandQPrefix$index", q).apply()
        _isDirty.value = true
        if (_enabled.value) {
            applyToService()
        }
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean("enabled", enabled).apply()
        viewModelScope.launch {
            context.dataStore.edit { it[ParametricEQEnabledKey] = enabled }
        }
        if (enabled) {
            applyToService()
        } else {
            viewModelScope.launch {
                eqProfileRepository.setActiveProfile(null)
            }
            equalizerService.disable()
        }
    }

    fun setMode(mode: Int) {
        _mode.value = mode
        prefs.edit().putInt("mode", mode).apply()
        _isDirty.value = false 
    }

    fun setBandGain(index: Int, gain: Float) {
        val newGains = _bandGains.value.copyOf()
        newGains[index] = gain
        _bandGains.value = newGains
        prefs.edit().putFloat("band_$index", gain).apply()
        _isDirty.value = true
        if (_enabled.value) {
            applyToService()
        }
    }

    fun setBandsGains(gains: FloatArray, fromUser: Boolean = false) {
        _bandGains.value = gains
        val editor = prefs.edit()
        gains.forEachIndexed { index, f -> editor.putFloat("band_$index", f) }
        editor.apply()
        _isDirty.value = fromUser 
        if (_enabled.value) {
            applyToService()
        }
    }

    fun reset() {
        val flat = FloatArray(10) { 0f }
        setBandsGains(flat)
        val flatQ = FloatArray(10) { 1.41f }
        _bandQ.value = flatQ
        val editor = prefs.edit()
        flatQ.forEachIndexed { index, q -> editor.putFloat("$EqBandQPrefix$index", q) }
        editor.apply()
    }

    fun saveCustomProfile(name: String) {
        viewModelScope.launch {
            val bands = _bandGains.value.mapIndexed { index, f ->
                ParametricEQBand(
                    frequency = bandFrequencies[index],
                    gain = f.toDouble() / 50.0,
                    q = _bandQ.value[index].toDouble(),
                    filterType = FilterType.PK,
                    enabled = true
                )
            }
            
            val id = "custom_${System.currentTimeMillis()}"
            val profile = SavedEQProfile(
                id = id,
                name = name,
                deviceModel = "Equalizer",
                bands = bands,
                preamp = 0.0,
                isCustom = true,
                isActive = true
            )
            
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            _isDirty.value = false
        }
    }

    fun deleteProfiles(ids: List<String>) {
        viewModelScope.launch {
            ids.forEach { id ->
                eqProfileRepository.deleteProfile(id)
            }
        }
    }

    private fun applyToService() {
        viewModelScope.launch {
            val bands = _bandGains.value.mapIndexed { index, f ->
                ParametricEQBand(
                    frequency = bandFrequencies[index],
                    gain = f.toDouble() / 50.0, 
                    q = _bandQ.value[index].toDouble(),
                    filterType = FilterType.PK,
                    enabled = true
                )
            }
            
            val profile = SavedEQProfile(
                id = "echo_tuning",
                name = "Echo Tuning",
                deviceModel = "Equalizer",
                bands = bands,
                preamp = _preampDb.value.toDouble(),
                isCustom = false,
                isActive = true
            )
            
            
            eqProfileRepository.saveProfile(profile)
            eqProfileRepository.setActiveProfile(profile.id)
            
            equalizerService.applyProfile(profile)
        }
    }
}
