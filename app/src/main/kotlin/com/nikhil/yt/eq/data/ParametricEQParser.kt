/*
 * Velune - Parametric EQ profile parser.
 * Supports AutoEQ / Wavelet JSON format and our own format.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.eq.data

import com.nikhil.yt.playback.EqualizerJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ParametricEQParser {

    fun parseAutoEqJson(jsonString: String): ParametricEQProfile? = runCatching {
        val root = EqualizerJson.json.parseToJsonElement(jsonString).jsonObject
        val preamp = root["preamp"]?.jsonPrimitive?.float ?: 0f
        val bands = root["bands"]?.jsonArray?.map { band ->
            val obj = band.jsonObject
            ParametricEQ(
                frequency = obj["freq"]?.jsonPrimitive?.float ?: obj["frequency"]?.jsonPrimitive?.float ?: 1000f,
                gain = obj["gain"]?.jsonPrimitive?.float ?: 0f,
                q = obj["q"]?.jsonPrimitive?.float ?: 1f,
                filterType = parseFilterType(obj["type"]?.jsonPrimitive?.content ?: "peak"),
            )
        } ?: emptyList()

        ParametricEQProfile(
            id = root["id"]?.jsonPrimitive?.content ?: "autoeq_import",
            name = root["name"]?.jsonPrimitive?.content ?: "AutoEQ Import",
            preamp = preamp,
            bands = bands,
        )
    }.getOrNull()

    fun parseWaveletJson(jsonString: String): ParametricEQProfile? = runCatching {
        val root = EqualizerJson.json.parseToJsonElement(jsonString).jsonObject
        val preamp = root["preamp"]?.jsonPrimitive?.float ?: 0f
        val bands = mutableListOf<ParametricEQ>()

        // Wavelet uses integer keys for bands
        for (i in 0 until 10) {
            val bandObj = root[i.toString()]?.jsonObject ?: continue
            val typeStr = bandObj["type"]?.jsonPrimitive?.content ?: "peak"
            val freq = bandObj["freq"]?.jsonPrimitive?.float ?: continue
            val gain = bandObj["gain"]?.jsonPrimitive?.float ?: 0f
            val q = bandObj["q"]?.jsonPrimitive?.float ?: 1f
            bands.add(ParametricEQ(frequency = freq, gain = gain, q = q, filterType = parseFilterType(typeStr)))
        }

        ParametricEQProfile(
            id = root["id"]?.jsonPrimitive?.content ?: "wavelet_import",
            name = root["name"]?.jsonPrimitive?.content ?: "Wavelet Import",
            preamp = preamp,
            bands = bands,
        )
    }.getOrNull()

    fun parseVeluneProfile(profile: com.nikhil.yt.playback.EqProfile): ParametricEQProfile {
        val bands = profile.bandCenterFreqHz.mapIndexed { index, freq ->
            val gain = profile.bandLevelsMb.getOrNull(index)?.let { it / 100f } ?: 0f
            ParametricEQ(
                frequency = freq.toFloat(),
                gain = gain,
                q = 1.414f,
                filterType = FilterType.PEAK,
            )
        }
        return ParametricEQProfile(
            id = profile.id,
            name = profile.name,
            preamp = profile.outputGainMb / 100f,
            bands = bands,
        )
    }

    private fun parseFilterType(type: String): FilterType = when (type.lowercase().trim()) {
        "peak", "peaking", "peak_eq", "parametric" -> FilterType.PEAK
        "lowshelf", "low_shelf", "ls" -> FilterType.LOW_SHELF
        "highshelf", "high_shelf", "hs" -> FilterType.HIGH_SHELF
        "lowpass", "low_pass", "lp" -> FilterType.LOW_PASS
        "highpass", "high_pass", "hp" -> FilterType.HIGH_PASS
        "bandpass", "band_pass", "bp" -> FilterType.BAND_PASS
        "notch" -> FilterType.NOTCH
        "allpass", "all_pass", "ap" -> FilterType.ALL_PASS
        else -> FilterType.PEAK
    }
}
