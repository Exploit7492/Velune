/*
 * Velune - Parametric EQ screen state.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.ui.screens.equalizer

import com.nikhil.yt.eq.data.SavedEQProfile

data class EQState(
    val isLoading: Boolean = false,
    val profiles: List<SavedEQProfile> = emptyList(),
    val selectedProfile: SavedEQProfile? = null,
    val enabled: Boolean = false,
    val errorMessage: String? = null,
    val showImportDialog: Boolean = false,
    val showSaveDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val importError: String? = null,
)
