package com.nikhil.yt.recognition.models

sealed class RecognitionStatus {
    data object Ready : RecognitionStatus()
    data object Listening : RecognitionStatus()
    data object Processing : RecognitionStatus()
    data class Success(val result: RecognitionResult) : RecognitionStatus()
    data class Error(val message: String) : RecognitionStatus()
}
