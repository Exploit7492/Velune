package com.nikhil.yt

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.nikhil.yt.utils.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConsentManager(private val context: Context) {
    companion object {
        private val CONSENT_GIVEN_KEY = booleanPreferencesKey("consent_given")
    }

    fun isConsentGiven(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[CONSENT_GIVEN_KEY] ?: false }

    suspend fun setConsentGiven(given: Boolean) {
        context.dataStore.edit { prefs -> prefs[CONSENT_GIVEN_KEY] = given }
    }
}
