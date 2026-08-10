package com.nikhil.yt.db.entities.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_track")
data class LocalTrackEntity(
    @PrimaryKey val trackId: String,
    val albumId: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val artworkUri: String?,
    val fileUri: String,
    val lastModified: Long,
    val addedToLibrary: Long,
    val isValidated: Boolean = true
)
