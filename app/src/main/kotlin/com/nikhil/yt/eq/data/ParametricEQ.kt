/*
 * Velune - Parametric EQ band configuration.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.eq.data

import kotlinx.serialization.Serializable

@Serializable
data class ParametricEQ(
    val frequency: Float,
    val gain: Float,
    val q: Float,
    val filterType: FilterType,
)

@Serializable
data class ParametricEQProfile(
    val id: String,
    val name: String,
    val preamp: Float = 0f,
    val bands: List<ParametricEQ> = emptyList(),
)
