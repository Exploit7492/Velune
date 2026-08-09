/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_folder",
    indices = [
        Index(value = ["isWatched"]),
        Index(value = ["dateAdded"]),
    ]
)
data class LocalFolderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val path: String,
    val trackCount: Int = 0,
    val artworkUri: String? = null,
    val isWatched: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastScan: Long = System.currentTimeMillis(),
)
