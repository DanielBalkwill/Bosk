package app.bosk.core.notes

/**
 * A linear undo/redo history of note-body snapshots for one editor session. Deliberately simple and
 * pure (no Android/Compose) so it's unit-testable: the editor commits a snapshot on each settled
 * change (a typing pause, a redaction, an AI rewrite), and [undo]/[redo] walk the stack.
 *
 * Invariant: [current] is always the snapshot at the cursor; committing a new state after an undo
 * discards the redo tail (standard editor behaviour). The stack is capped at [cap] entries (oldest
 * dropped) so a long session can't grow without bound.
 */
class EditHistory(private val cap: Int = 200) {
    private val stack = ArrayList<String>()
    private var index = -1

    /** Seed the history with the loaded body; clears any prior state. */
    fun reset(initial: String) {
        stack.clear()
        stack.add(initial)
        index = 0
    }

    /** The snapshot currently pointed at, or null before [reset]. */
    val current: String? get() = stack.getOrNull(index)

    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index in 0 until stack.lastIndex

    /**
     * Record [text] as a new snapshot. No-op if it equals the current snapshot (avoids dup entries
     * from idempotent saves). Truncates the redo tail and enforces the [cap].
     */
    fun commit(text: String) {
        if (index >= 0 && stack[index] == text) return
        // Drop any redo tail past the current point.
        while (stack.lastIndex > index) stack.removeAt(stack.lastIndex)
        stack.add(text)
        index = stack.lastIndex
        if (stack.size > cap) {
            stack.removeAt(0)
            index--
        }
    }

    /** Step back one snapshot, returning it, or null if there's nothing to undo. */
    fun undo(): String? {
        if (!canUndo) return null
        index--
        return stack[index]
    }

    /** Step forward one snapshot, returning it, or null if there's nothing to redo. */
    fun redo(): String? {
        if (!canRedo) return null
        index++
        return stack[index]
    }
}
