/*
 * Velune - Parametric EQ service singleton.
 * Holds the current EQ profile and provides the AudioProcessor instance.
 * Can be toggled independently of the system Equalizer.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.eq

import com.nikhil.yt.eq.audio.CustomEqualizerAudioProcessor
import com.nikhil.yt.eq.data.ParametricEQProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EqualizerService {

    private val _currentProfile = MutableStateFlow<ParametricEQProfile?>(null)
    val currentProfile: StateFlow<ParametricEQProfile?> = _currentProfile.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val audioProcessor: CustomEqualizerAudioProcessor = CustomEqualizerAudioProcessor()

    fun setProfile(profile: ParametricEQProfile?) {
        _currentProfile.value = profile
        audioProcessor.setProfile(profile)
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        audioProcessor.setEnabled(value)
    }

    fun applyCurrent() {
        audioProcessor.setProfile(_currentProfile.value)
        audioProcessor.setEnabled(_enabled.value)
    }
}
