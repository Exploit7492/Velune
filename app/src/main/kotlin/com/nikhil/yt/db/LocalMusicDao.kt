/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nikhil.yt.db.entities.LocalFolderEntity
import com.nikhil.yt.db.entities.LocalTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMusicDao {

    // Folder Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceFolders(folders: List<LocalFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceFolder(folder: LocalFolderEntity)

    @Query("SELECT * FROM local_folder WHERE isWatched = 1 ORDER BY dateAdded DESC")
    fun watchedFoldersFlow(): Flow<List<LocalFolderEntity>>

    @Query("SELECT * FROM local_folder ORDER BY name ASC")
    fun allFoldersFlow(): Flow<List<LocalFolderEntity>>

    @Query("SELECT * FROM local_folder WHERE isWatched = 1 ORDER BY dateAdded DESC")
    suspend fun getWatchedFolders(): List<LocalFolderEntity>

    @Query("SELECT * FROM local_folder ORDER BY name ASC")
    suspend fun getAllFolders(): List<LocalFolderEntity>

    @Query("UPDATE local_folder SET isWatched = :isWatched WHERE id = :folderId")
    suspend fun setFolderWatched(folderId: String, isWatched: Boolean)

    @Query("UPDATE local_folder SET name = :name WHERE id = :folderId")
    suspend fun renameFolder(folderId: String, name: String)

    @Query("UPDATE local_folder SET trackCount = :count, lastScan = :lastScan WHERE id = :folderId")
    suspend fun updateFolderTrackCount(folderId: String, count: Int, lastScan: Long)

    @Query("DELETE FROM local_folder WHERE id = :folderId")
    suspend fun deleteFolder(folderId: String)

    @Query("DELETE FROM local_folder WHERE isWatched = 0 AND lastScan < :beforeTimestamp")
    suspend fun deleteOldUnwatchedFolders(beforeTimestamp: Long)

    // Track Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceTracks(tracks: List<LocalTrackEntity>)

    @Query("SELECT * FROM local_track WHERE folderId = :folderId ORDER BY title ASC")
    fun tracksByFolderFlow(folderId: String): Flow<List<LocalTrackEntity>>

    @Query("SELECT * FROM local_track WHERE folderId = :folderId ORDER BY title ASC")
    suspend fun getTracksByFolder(folderId: String): List<LocalTrackEntity>

    @Query("SELECT * FROM local_track WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): LocalTrackEntity?

    @Query("DELETE FROM local_track WHERE folderId = :folderId")
    suspend fun deleteTracksByFolder(folderId: String)

    @Query("SELECT COUNT(*) FROM local_track")
    suspend fun getTotalTrackCount(): Int

    @Query("UPDATE local_track SET isValidated = :isValid WHERE id = :trackId")
    suspend fun updateTrackValidation(trackId: String, isValid: Boolean)

    // Bulk Operations
    @Transaction
    suspend fun replaceTracksForFolder(folderId: String, tracks: List<LocalTrackEntity>) {
        deleteTracksByFolder(folderId)
        insertOrReplaceTracks(tracks)
        updateFolderTrackCount(folderId, tracks.size, System.currentTimeMillis())
    }
}
