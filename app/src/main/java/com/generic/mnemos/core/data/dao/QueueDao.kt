package com.generic.mnemos.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.generic.mnemos.core.data.entity.QueueItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items ORDER BY position ASC, id ASC")
    suspend fun all(): List<QueueItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM queue_items")
    suspend fun maxPosition(): Int

    @Query("SELECT * FROM queue_items WHERE id = :id")
    suspend fun byId(id: Long): QueueItemEntity?

    @Upsert
    suspend fun upsert(item: QueueItemEntity): Long

    @Update
    suspend fun update(item: QueueItemEntity)

    @Delete
    suspend fun delete(item: QueueItemEntity)
}
