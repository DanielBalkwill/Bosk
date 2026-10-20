package com.generic.mnemos.core.sync

/**
 * Paid-tier cloud sync via Supabase (PostgreSQL + Realtime). Free tier is local-only,
 * so this is gated behind FeatureFlags (CP6). The encrypted API key is NEVER synced.
 * TODO CP6: implement push/pull of notes, graph, SRS state + conflict resolution.
 */
interface SyncManager {
    suspend fun syncNow(): Result<Unit>
    val isEnabled: Boolean
}
