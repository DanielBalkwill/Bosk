package com.generic.mnemos.core.cache

import com.generic.mnemos.core.graph.GraphSnapshot
import com.generic.mnemos.core.notes.Note
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tiny, app-lifetime warm cache of the last values the repositories emitted.
 *
 * Every screen's flow uses `stateIn(..., WhileSubscribed(5_000), <empty>)`, so when a flow is
 * re-subscribed — switching tabs, returning from a full-screen route, or resuming the app a few
 * seconds later — it momentarily resets to the empty initial value and then recomputes from Room,
 * which flashes a blank UI and re-runs the graph degree pass. Seeding the `stateIn` initial value
 * from here means re-subscribers paint the last-known data on the very first frame instead.
 *
 * Populated by the repositories (see [com.generic.mnemos.core.notes.NotesRepositoryImpl] and
 * [com.generic.mnemos.core.graph.GraphRepositoryImpl]) and primed at startup by
 * [com.generic.mnemos.MnemosApplication]. In-memory only; intentionally not persisted.
 */
@Singleton
class SessionCache @Inject constructor() {
    @Volatile var notes: List<Note> = emptyList()
        private set

    @Volatile var graph: GraphSnapshot = GraphSnapshot()
        private set

    fun putNotes(list: List<Note>) { notes = list }
    fun putGraph(snapshot: GraphSnapshot) { graph = snapshot }
}
