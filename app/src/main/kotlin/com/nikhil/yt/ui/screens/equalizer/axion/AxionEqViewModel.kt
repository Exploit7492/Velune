package com.nikhil.yt.ui.screens.equalizer.axion

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.constants.EqBalanceKey
import com.nikhil.yt.constants.EqBassBoostKey
import com.nikhil.yt.constants.ParametricEQEnabledKey
import com.nikhil.yt.eq.EqualizerService
import com.nikhil.yt.eq.data.EQProfileRepository
import com.nikhil.yt.eq.data.FilterType
import com.nikhil.yt.eq.data.ParametricEQBand
import com.nikhil.yt.eq.data.SavedEQProfile
import com.nikhil.yt.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import javax.inject.Inject

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
            equalizerService.setBalance(_balance.value.toDouble())
            equalizerService.setBassBoost(_bassBoostDb.value.toDouble())
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
    }

    fun saveCustomProfile(name: String) {
        viewModelScope.launch {
            val bands = _bandGains.value.mapIndexed { index, f ->
                ParametricEQBand(
                    frequency = bandFrequencies[index],
                    gain = f.toDouble() / 50.0,
                    q = 1.41,
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
                    q = 1.41,
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
