/*
 * Velune - Parametric EQ ViewModel.
 * Manages profile selection, editing, import/export.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.ui.screens.equalizer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.eq.EqualizerService
import com.nikhil.yt.eq.data.EQProfileRepository
import com.nikhil.yt.eq.data.ParametricEQ
import com.nikhil.yt.eq.data.ParametricEQ
import com.nikhil.yt.eq.data.FilterType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.datastore.preferences.core.edit
import com.nikhil.yt.constants.ParametricEQEnabledKey
import com.nikhil.yt.constants.ParametricEQSelectedProfileIdKey
import com.nikhil.yt.utils.dataStore

class EQViewModel(private val context: Context) : ViewModel() {

    private val repository = EQProfileRepository(context)

    private val _state = MutableStateFlow(EQState())
    val state: StateFlow<EQState> = _state.asStateFlow()

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val profiles = repository.getAllProfiles()
            val currentProfile = EqualizerService.currentProfile.value
            val enabled = EqualizerService.enabled.value
            _state.update {
                it.copy(
                    isLoading = false,
                    profiles = profiles,
                    selectedProfile = currentProfile ?: profiles.firstOrNull(),
                    enabled = enabled,
                )
            }
        }
    }

    fun selectProfile(profile: ParametricEQ?) {
        _state.update { it.copy(selectedProfile = profile) }
        EqualizerService.setProfile(profile)
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[ParametricEQSelectedProfileIdKey] = profile?.id ?: "flat"
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        EqualizerService.setEnabled(enabled)
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[ParametricEQEnabledKey] = enabled
            }
        }
    }

    fun updatePreamp(preamp: Float) {
        _state.update { current ->
            val updated = current.selectedProfile?.copy(preamp = preamp) ?: return
            current.copy(selectedProfile = updated)
        }
        applyCurrentProfile()
    }

    fun updateBandGain(index: Int, gain: Float) {
        _state.update { current ->
            val profile = current.selectedProfile ?: return
            val bands = profile.bands.toMutableList()
            if (index in bands.indices) {
                bands[index] = bands[index].copy(gain = gain)
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
                bands[index] = bands[index].copy(frequency = frequency)
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
                bands[index] = bands[index].copy(q = q.coerceAtLeast(0.1f))
            }
            current.copy(selectedProfile = profile.copy(bands = bands))
        }
        applyCurrentProfile()
    }

    fun addBand() {
        _state.update { current ->
            val profile = current.selectedProfile ?: return
            val newBand = ParametricEQ(
                frequency = 1000f,
                gain = 0f,
                q = 1.0f,
                filterType = FilterType.PEAK,
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
            val profile = when (format) {
                ImportFormat.AUTO_EQ -> repository.importFromAutoEq(json)
                ImportFormat.WAVELET -> repository.importFromWavelet(json)
            }
            if (profile != null) {
                repository.saveUserProfile(profile)
                _state.update {
                    it.copy(
                        profiles = repository.getAllProfiles(),
                        selectedProfile = profile,
                        showImportDialog = false,
                        importError = null,
                    )
                }
                EqualizerService.setProfile(profile)
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
            )
            repository.saveUserProfile(profile)
            _state.update {
                it.copy(
                    profiles = repository.getAllProfiles(),
                    selectedProfile = profile,
                    showSaveDialog = false,
                )
            }
        }
    }

    fun deleteProfile(profile: ParametricEQ) {
        viewModelScope.launch {
            repository.deleteUserProfile(profile.id)
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
            EqualizerService.setProfile(selected)
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
        EqualizerService.setProfile(profile)
    }

    enum class ImportFormat {
        AUTO_EQ,
        WAVELET,
    }
}
