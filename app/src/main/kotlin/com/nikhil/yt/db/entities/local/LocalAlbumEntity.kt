package com.nikhil.yt.db.entities.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_album")
data class LocalAlbumEntity(
    @PrimaryKey val albumId: String,
    val albumName: String,
    val albumArtworkUri: String?,
    val trackCount: Int = 0,
    val lastScan: Long,
    val dateAdded: Long
)
