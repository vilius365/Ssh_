package com.pocketssh.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionProfileDao {

    @Query("SELECT * FROM connection_profiles ORDER BY last_connected_at DESC")
    fun observeAll(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun getById(id: Long): ConnectionProfileEntity?

    @Query("SELECT * FROM connection_profiles WHERE is_default = 1 LIMIT 1")
    suspend fun getDefault(): ConnectionProfileEntity?

    @Query("SELECT * FROM connection_profiles ORDER BY last_connected_at DESC LIMIT 1")
    suspend fun getMostRecent(): ConnectionProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ConnectionProfileEntity): Long

    @Update
    suspend fun update(profile: ConnectionProfileEntity)

    @Delete
    suspend fun delete(profile: ConnectionProfileEntity)

    @Query("UPDATE connection_profiles SET is_default = 0")
    suspend fun clearDefaults()

    @Query("UPDATE connection_profiles SET last_connected_at = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long)

    // Fix I5: Atomic setDefault to prevent race between clearDefaults and upsert
    @Transaction
    suspend fun setDefaultTransactional(id: Long) {
        clearDefaults()
        setDefaultById(id, System.currentTimeMillis())
    }

    @Query("UPDATE connection_profiles SET is_default = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun setDefaultById(id: Long, updatedAt: Long)
}
