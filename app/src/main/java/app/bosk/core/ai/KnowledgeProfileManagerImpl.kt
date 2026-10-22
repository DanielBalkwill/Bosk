package app.bosk.core.ai

import app.bosk.core.data.dao.KnowledgeProfileDao
import app.bosk.core.data.entity.KnowledgeProfileEntity
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeProfileManagerImpl @Inject constructor(
    private val dao: KnowledgeProfileDao,
    private val json: Json,
) : KnowledgeProfileManager {

    override suspend fun current(): KnowledgeProfile {
        val row = dao.get() ?: return KnowledgeProfile()
        return runCatching {
            json.decodeFromString(KnowledgeProfile.serializer(), row.json)
        }.getOrDefault(KnowledgeProfile())
    }

    override suspend fun currentContextBlock(): String {
        val profile = current()
        // No profile yet → no context block (avoids sending an empty/noisy preamble).
        return if (profile.updatedAtMs == 0L && profile.totalCards == 0) "" else profile.toContextBlock()
    }

    override suspend fun save(profile: KnowledgeProfile) {
        val stamped = profile.copy(updatedAtMs = System.currentTimeMillis())
        dao.upsert(
            KnowledgeProfileEntity(
                json = json.encodeToString(KnowledgeProfile.serializer(), stamped),
                updatedAt = stamped.updatedAtMs,
            ),
        )
    }

    override suspend fun update(transform: (KnowledgeProfile) -> KnowledgeProfile) {
        save(transform(current()))
    }
}
