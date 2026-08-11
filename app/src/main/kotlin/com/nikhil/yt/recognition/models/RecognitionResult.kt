package com.nikhil.yt.recognition.models

data class RecognitionResult(
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val shazamUrl: String? = null,
    val appleMusicUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
