/*
 * Velune - Parametric EQ profile repository.
 * Manages built-in and user-saved EQ profiles.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.eq.data

import android.content.Context
import com.nikhil.yt.playback.EqualizerJson
import kotlinx.serialization.encodeToString
import java.io.File

class EQProfileRepository(private val context: Context) {

    private val userProfilesDir: File
        get() = context.filesDir.resolve("eq_profiles").also { it.mkdirs() }

    private val builtInProfiles: List<ParametricEQProfile> by lazy {
        listOf(
            ParametricEQProfile(
                id = "flat",
                name = "Flat",
                preamp = 0f,
                bands = emptyList(),
            ),
            ParametricEQProfile(
                id = "bass_boost",
                name = "Bass Boost",
                preamp = -3f,
                bands = listOf(
                    ParametricEQ(60f, 6f, 0.7f, FilterType.LOW_SHELF),
                    ParametricEQ(150f, 3f, 1.0f, FilterType.PEAK),
                ),
            ),
            ParametricEQProfile(
                id = "v_shape",
                name = "V-Shape",
                preamp = -4f,
                bands = listOf(
                    ParametricEQ(60f, 5f, 0.7f, FilterType.LOW_SHELF),
                    ParametricEQ(12000f, 5f, 0.7f, FilterType.HIGH_SHELF),
                ),
            ),
            ParametricEQProfile(
                id = "vocal_boost",
                name = "Vocal Boost",
                preamp = -2f,
                bands = listOf(
                    ParametricEQ(2500f, 4f, 1.2f, FilterType.PEAK),
                    ParametricEQ(4000f, 3f, 1.5f, FilterType.PEAK),
                ),
            ),
            ParametricEQProfile(
                id = "treble_boost",
                name = "Treble Boost",
                preamp = -3f,
                bands = listOf(
                    ParametricEQ(8000f, 5f, 0.7f, FilterType.HIGH_SHELF),
                    ParametricEQ(12000f, 3f, 1.0f, FilterType.PEAK),
                ),
            ),
        )
    }

    fun getAllProfiles(): List<ParametricEQProfile> {
        return builtInProfiles + loadUserProfiles()
    }

    fun getProfile(id: String): ParametricEQProfile? {
        return getAllProfiles().find { it.id == id }
    }

    fun saveUserProfile(profile: ParametricEQProfile) {
        val file = userProfilesDir.resolve("${profile.id}.json")
        file.writeText(EqualizerJson.json.encodeToString(profile))
    }

    fun deleteUserProfile(id: String) {
        userProfilesDir.resolve("$id.json").delete()
    }

    fun importFromAutoEq(json: String): ParametricEQProfile? {
        return ParametricEQParser.parseAutoEqJson(json)
    }

    fun importFromWavelet(json: String): ParametricEQProfile? {
        return ParametricEQParser.parseWaveletJson(json)
    }

    private fun loadUserProfiles(): List<ParametricEQProfile> {
        return userProfilesDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    EqualizerJson.json.decodeFromString<ParametricEQProfile>(file.readText())
                }.getOrNull()
            }
            ?: emptyList()
    }
}
