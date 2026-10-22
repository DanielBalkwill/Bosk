package app.bosk.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (id always [SINGLETON_ID]) holding the user's knowledge state as a JSON
 * blob (the "Mulch pattern"). Prepended to every AI call for cheap, persistent context.
 */
@Entity(tableName = "knowledge_profile")
data class KnowledgeProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val json: String,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
