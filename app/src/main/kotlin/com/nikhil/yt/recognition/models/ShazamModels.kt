package com.nikhil.yt.recognition.models

import kotlinx.serialization.Serializable

@Serializable
data class ShazamRequestJson(
    val geolocation: Geolocation,
    val signature: Signature,
    val timestamp: Long,
    val timezone: String
) {
    @Serializable
    data class Geolocation(
        val altitude: Double,
        val latitude: Double,
        val longitude: Double
    )

    @Serializable
    data class Signature(
        val samplems: Long,
        val timestamp: Long,
        val uri: String
    )
}

@Serializable
data class ShazamResponseJson(
    val matches: List<ShazamMatch>? = null,
    val track: ShazamTrack? = null,
    val timestamp: Long? = null,
    val tagid: String? = null
) {
    fun toRecognitionResult(): RecognitionResult? {
        val track = track ?: return null
        val title = track.title ?: return null
        val artist = track.subtitle ?: return null
        return RecognitionResult(
            title = title,
            artist = artist,
            album = track.sections?.find { it.type == "SONG" }?.metadata?.find { it.title == "Album" }?.text,
            coverUrl = track.images?.coverarthq ?: track.images?.coverart,
            shazamUrl = track.url,
            appleMusicUrl = track.hub?.actions?.find { it.type == "applemusicplay" }?.uri
        )
    }
}

@Serializable
data class ShazamMatch(val id: String? = null)

@Serializable
data class ShazamTrack(
    val title: String? = null,
    val subtitle: String? = null,
    val images: ShazamImages? = null,
    val url: String? = null,
    val hub: ShazamHub? = null,
    val sections: List<ShazamSection>? = null
)

@Serializable
data class ShazamImages(
    val coverart: String? = null,
    val coverarthq: String? = null,
    val background: String? = null
)

@Serializable
data class ShazamHub(
    val actions: List<ShazamAction>? = null
)

@Serializable
data class ShazamAction(
    val name: String? = null,
    val type: String? = null,
    val uri: String? = null
)

@Serializable
data class ShazamSection(
    val type: String? = null,
    val metadata: List<ShazamMetadata>? = null
)

@Serializable
data class ShazamMetadata(
    val title: String? = null,
    val text: String? = null
)
