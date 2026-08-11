package com.nikhil.yt.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nikhil.yt.db.entities.SpeedDialItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dial ORDER BY `index` ASC")
    fun getAll(): Flow<List<SpeedDialItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SpeedDialItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SpeedDialItem>)

    @Update
    suspend fun update(item: SpeedDialItem)

    @Query("DELETE FROM speed_dial WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM speed_dial")
    suspend fun deleteAll()
}
