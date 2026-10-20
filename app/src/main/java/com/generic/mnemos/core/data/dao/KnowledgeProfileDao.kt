package com.generic.mnemos.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.generic.mnemos.core.data.entity.KnowledgeProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeProfileDao {
    @Query("SELECT * FROM knowledge_profile WHERE id = :id")
    suspend fun get(id: Int = KnowledgeProfileEntity.SINGLETON_ID): KnowledgeProfileEntity?

    @Query("SELECT * FROM knowledge_profile WHERE id = :id")
    fun observe(id: Int = KnowledgeProfileEntity.SINGLETON_ID): Flow<KnowledgeProfileEntity?>

    @Upsert
    suspend fun upsert(profile: KnowledgeProfileEntity)
}
