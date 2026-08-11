package com.nikhil.yt.recognition

object VibraSignature {
    const val REQUIRED_SAMPLE_RATE = 16_000
    fun fromI16(pcmData: ByteArray): String = ShazamSignatureGenerator.fromI16(pcmData)
}
