/*
 * Velune - Parametric EQ ViewModel.
 * Manages profile selection, editing, import/export.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.ui.screens.equalizer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.di.EqEntryPoint
import com.nikhil.yt.eq.EqualizerService
import com.nikhil.yt.eq.data.EQProfileRepository
import com.nikhil.yt.eq.data.FilterType
import com.nikhil.yt.eq.data.ParametricEQ
import com.nikhil.yt.eq.data.ParametricEQBand
import com.nikhil.yt.eq.data.ParametricEQParser
import com.nikhil.yt.eq.data.SavedEQProfile
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.datastore.preferences.core.edit
import com.nikhil.yt.constants.ParametricEQEnabledKey
import com.nikhil.yt.constants.ParametricEQSelectedProfileIdKey
import com.nikhil.yt.utils.dataStore

class EQViewModel(private val context: Context) : ViewModel() {

    // Fetch the app-wide Hilt singletons instead of constructing new, disconnected
    // instances — EqualizerService() would hold its own empty audioProcessors list
    // that never reaches the processor chain MusicService actually wired up.
    private val eqEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        EqEntryPoint::class.java,
    )
    private val repository = eqEntryPoint.eqProfileRepository()
    private val equalizerService = eqEntryPoint.equalizerService()

    private val _state = MutableStateFlow(EQState())
    val state: StateFlow<EQState> = _state.asStateFlow()

    init {
        loadProfiles()
        loadEnabledState()
    }

    private fun loadEnabledState() {
        viewModelScope.launch {
            val persisted = context.dataStore.data.first()[ParametricEQEnabledKey] ?: false
            _state.update { it.copy(enabled = persisted) }
        }
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val profiles = repository.getAllProfiles()
            val activeProfile = repository.getActiveProfile()
            _state.update {
                it.copy(
                    isLoading = false,
                    profiles = profiles,
                    selectedProfile = activeProfile ?: profiles.firstOrNull(),
                )
            }
        }
    }

    fun selectProfile(profile: SavedEQProfile?) {
        _state.update { it.copy(selectedProfile = profile) }
        applyCurrentProfile()
        viewModelScope.launch {
            repository.setActiveProfile(profile?.id)
            context.dataStore.edit { prefs ->
                prefs[ParametricEQSelectedProfileIdKey] = profile?.id ?: "flat"
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        if (enabled) {
            applyCurrentProfile()
        } else {
            equalizerService.disable()
        }
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[ParametricEQEnabledKey] = enabled
            }
        }
    }

    fun updatePreamp(preamp: Float) {
        _state.update { current ->
            val updated = current.selectedProfile?.copy(preamp = preamp.toDouble()) ?: return
            current.copy(selectedProfile = updated)
        }
        applyCurrentProfile()
    }

    fun updateBandGain(index: Int, gain: Float) {
        _state.update { current ->
            val profile = current.selectedProfile ?: return
            val bands = profile.bands.toMutableList()
            if (index in bands.indices) {
                bands[index] = bands[index].copy(gain = gain.toDouble())
            }
            current.copy(selectedProfile = profile.copy(bands = bands))
        }
        applyCurrentProfile()
    }

    fun updateBandFrequency(index: Int, frequency: Float) {
        _state.update { current ->
            val profile = current.selectedProfile ?: return
            val bands = profile.bands.toMutableList()
            if (index in bands.indices) {
                bands[index] = bands[index].copy(frequency = frequency.toDouble())
            }
            current.copy(selectedProfile = profile.copy(bands = bands))
        }
        applyCurrentProfile()
    }

    fun updateBandQ(index: Int, q: Float) {
        _state.update { current ->
            val profile = current.selectedProfile ?: return
            val bands = profile.bands.toMutableList()
            if (index in bands.indices) {
                bands[index] = bands[index].copy(q = q.coerceAtLeast(0.1f).toDouble())
            }
            current.copy(selectedProfile = profile.copy(bands = bands))
        }
        applyCurrentProfile()
    }

    fun addBand() {
        _state.update { current ->
            val profile = current.selectedProfile ?: return
            val newBand = ParametricEQBand(
                frequency = 1000.0,
                gain = 0.0,
                q = 1.0,
                filterType = FilterType.PK,
            )
            current.copy(selectedProfile = profile.copy(bands = profile.bands + newBand))
        }
        applyCurrentProfile()
    }

    fun removeBand(index: Int) {
        _state.update { current ->
            val profile = current.selectedProfile ?: return
            val bands = profile.bands.toMutableList()
            if (index in bands.indices) bands.removeAt(index)
            current.copy(selectedProfile = profile.copy(bands = bands))
        }
        applyCurrentProfile()
    }

    fun importFromJson(json: String, format: ImportFormat) {
        viewModelScope.launch {
            val parsed: ParametricEQ? = try {
                when (format) {
                    ImportFormat.AUTO_EQ, ImportFormat.WAVELET -> ParametricEQParser.parseText(json)
                }
            } catch (e: Exception) {
                null
            }
            if (parsed != null) {
                val name = "Imported ${System.currentTimeMillis()}"
                repository.importCustomProfile(name, parsed)
                val profiles = repository.getAllProfiles()
                val saved = profiles.firstOrNull { it.name == name } ?: profiles.firstOrNull()
                _state.update {
                    it.copy(
                        profiles = profiles,
                        selectedProfile = saved,
                        showImportDialog = false,
                        importError = null,
                    )
                }
                selectProfile(saved)
            } else {
                _state.update { it.copy(importError = "Failed to parse profile") }
            }
        }
    }

    fun saveCurrentProfile(name: String) {
        viewModelScope.launch {
            val current = _state.value.selectedProfile ?: return@launch
            val profile = current.copy(
                id = "user_${UUID.randomUUID().toString().take(8)}",
                name = name,
                isCustom = true,
            )
            repository.saveProfile(profile)
            _state.update {
                it.copy(
                    profiles = repository.getAllProfiles(),
                    selectedProfile = profile,
                    showSaveDialog = false,
                )
            }
        }
    }

    fun deleteProfile(profile: SavedEQProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile.id)
            val profiles = repository.getAllProfiles()
            val selected = if (_state.value.selectedProfile?.id == profile.id) {
                profiles.firstOrNull()
            } else {
                _state.value.selectedProfile
            }
            _state.update {
                it.copy(
                    profiles = profiles,
                    selectedProfile = selected,
                    showDeleteConfirm = false,
                )
            }
            selectProfile(selected)
        }
    }

    fun showImportDialog() {
        _state.update { it.copy(showImportDialog = true, importError = null) }
    }

    fun dismissImportDialog() {
        _state.update { it.copy(showImportDialog = false, importError = null) }
    }

    fun showSaveDialog() {
        _state.update { it.copy(showSaveDialog = true) }
    }

    fun dismissSaveDialog() {
        _state.update { it.copy(showSaveDialog = false) }
    }

    fun showDeleteConfirm() {
        _state.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirm() {
        _state.update { it.copy(showDeleteConfirm = false) }
    }

    private fun applyCurrentProfile() {
        val profile = _state.value.selectedProfile ?: return
        equalizerService.applyProfile(profile)
    }

    enum class ImportFormat {
        AUTO_EQ,
        WAVELET,
    }
}
