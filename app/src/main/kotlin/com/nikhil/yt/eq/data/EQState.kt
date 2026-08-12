package com.nikhil.yt.eq.data

import com.nikhil.yt.eq.data.SavedEQProfile


data class EQState(
    val profiles: List<SavedEQProfile> = emptyList(),
    val activeProfileId: String? = null,
    val importStatus: String? = null,
    val error: String? = null,
    // UI-state fields used by EQViewModel/EqScreen (Parametric EQ screen).
    val isLoading: Boolean = false,
    val selectedProfile: SavedEQProfile? = null,
    val enabled: Boolean = false,
    val showImportDialog: Boolean = false,
    val importError: String? = null,
    val showSaveDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
)
