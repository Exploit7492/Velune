/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_track",
    foreignKeys = [
        ForeignKey(
            entity = LocalFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
    ]
)
data class LocalTrackEntity(
    @PrimaryKey
    val id: String,
    val folderId: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val fileUri: String,
    val artworkUri: String?,
    val trackNumber: Int,
    val dateAdded: Long,
    val dateModified: Long,
    val isValidated: Boolean = true,
)
