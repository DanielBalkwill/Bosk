package app.bosk.core.ai

/**
 * Maintains the persistent [KnowledgeProfile] (Mulch pattern): a compact, evolving summary
 * of the user's knowledge prepended to every AI call so Gemini has context without
 * re-ingesting the corpus. Persisted locally as a JSON blob (singleton Room row);
 * optionally synced on the paid tier.
 *
 * CP2 implements load / render / save. The richer incremental update logic (after each
 * review session) lands in CP5 via [save] called from the curriculum/updater layer.
 */
interface KnowledgeProfileManager {
    suspend fun current(): KnowledgeProfile

    /** The system-context string to prepend to Gemini calls. Empty if no profile yet. */
    suspend fun currentContextBlock(): String

    suspend fun save(profile: KnowledgeProfile)

    /** Convenience read-modify-write. */
    suspend fun update(transform: (KnowledgeProfile) -> KnowledgeProfile)
}
