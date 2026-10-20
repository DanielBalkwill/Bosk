package com.generic.mnemos.core.queue

import com.generic.mnemos.core.data.dao.QueueDao
import com.generic.mnemos.core.data.entity.QueueItemEntity
import com.generic.mnemos.service.queue.QueueAlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class QueueKind { TASK, ALARM, EVENT }

/** UI model for a queue/agenda item. */
data class QueueItem(
    val id: Long,
    val title: String,
    val kind: QueueKind,
    val timeMs: Long?,
    val position: Int,
    val done: Boolean,
)

/** The user-managed queue: ordered tasks/alarms/events with optional scheduled reminders. */
@Singleton
class QueueRepository @Inject constructor(
    private val dao: QueueDao,
    private val scheduler: QueueAlarmScheduler,
) {
    fun observe(): Flow<List<QueueItem>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun add(title: String, kind: QueueKind, timeMs: Long?): Long {
        val clean = title.trim().ifBlank { "Untitled" }
        val position = dao.maxPosition() + 1
        val id = dao.upsert(
            QueueItemEntity(title = clean, kind = kind.name, timeMs = timeMs, position = position),
        )
        if (kind != QueueKind.TASK && timeMs != null) scheduler.schedule(id, clean, timeMs)
        return id
    }

    suspend fun remove(id: Long) {
        dao.byId(id)?.let { dao.delete(it) }
        scheduler.cancel(id)
    }

    suspend fun toggleDone(id: Long) {
        dao.byId(id)?.let { dao.update(it.copy(done = !it.done)) }
    }

    /** Swap an item with its neighbour (reorder by one step). */
    suspend fun move(id: Long, up: Boolean) {
        val items = dao.all()
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val swapIndex = if (up) index - 1 else index + 1
        if (swapIndex !in items.indices) return
        val a = items[index]
        val b = items[swapIndex]
        // Swap their positions.
        dao.update(a.copy(position = b.position))
        dao.update(b.copy(position = a.position))
    }

    private fun QueueItemEntity.toModel() = QueueItem(
        id = id,
        title = title,
        kind = runCatching { QueueKind.valueOf(kind) }.getOrDefault(QueueKind.TASK),
        timeMs = timeMs,
        position = position,
        done = done,
    )
}
