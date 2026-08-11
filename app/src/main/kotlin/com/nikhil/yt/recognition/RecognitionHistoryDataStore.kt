package com.nikhil.yt.recognition

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nikhil.yt.recognition.models.RecognitionResult
import com.nikhil.yt.utils.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RecognitionHistoryDataStore {
    private val HISTORY_KEY = stringPreferencesKey("recognition_history_v1")

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getHistory(context: Context): Flow<List<RecognitionResult>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[HISTORY_KEY] ?: "[]"
            try {
                json.decodeFromString<List<RecognitionResult>>(raw)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addResult(context: Context, result: RecognitionResult) {
        context.dataStore.edit { prefs ->
            val current = try {
                json.decodeFromString<List<RecognitionResult>>(prefs[HISTORY_KEY] ?: "[]")
            } catch (e: Exception) {
                emptyList()
            }
            val updated = listOf(result) + current.take(99)
            prefs[HISTORY_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun clearHistory(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(HISTORY_KEY)
        }
    }

    suspend fun removeResult(context: Context, timestamp: Long) {
        context.dataStore.edit { prefs ->
            val current = try {
                json.decodeFromString<List<RecognitionResult>>(prefs[HISTORY_KEY] ?: "[]")
            } catch (e: Exception) {
                emptyList()
            }
            val updated = current.filter { it.timestamp != timestamp }
            prefs[HISTORY_KEY] = json.encodeToString(updated)
        }
    }
}
