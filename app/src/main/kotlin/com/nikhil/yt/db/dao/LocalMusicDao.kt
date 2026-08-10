package com.nikhil.yt.db.dao

import androidx.room.*
import com.nikhil.yt.db.entities.local.LocalAlbumEntity
import com.nikhil.yt.db.entities.local.LocalTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMusicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: LocalAlbumEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<LocalAlbumEntity>)
    @Query("SELECT * FROM local_album ORDER BY dateAdded DESC")
    fun getAllAlbums(): Flow<List<LocalAlbumEntity>>
    @Query("SELECT * FROM local_album WHERE albumId = :albumId")
    suspend fun getAlbum(albumId: String): LocalAlbumEntity?
    @Query("DELETE FROM local_album WHERE albumId = :albumId")
    suspend fun deleteAlbum(albumId: String)
    @Query("UPDATE local_album SET albumName = :name WHERE albumId = :albumId")
    suspend fun renameAlbum(albumId: String, name: String)
    @Query("UPDATE local_album SET trackCount = :count, lastScan = :lastScan WHERE albumId = :albumId")
    suspend fun updateAlbumTrackCount(albumId: String, count: Int, lastScan: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: LocalTrackEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<LocalTrackEntity>)
    @Query("SELECT * FROM local_track WHERE albumId = :albumId ORDER BY title ASC")
    fun getTracksByAlbum(albumId: String): Flow<List<LocalTrackEntity>>
    @Query("DELETE FROM local_track WHERE albumId = :albumId")
    suspend fun deleteTracksByAlbum(albumId: String)
    @Query("SELECT COUNT(*) FROM local_track WHERE albumId = :albumId")
    suspend fun getTrackCount(albumId: String): Int
    @Transaction
    suspend fun deleteAlbumWithTracks(albumId: String) {
        deleteTracksByAlbum(albumId)
        deleteAlbum(albumId)
    }
}
