package com.generic.mnemos.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-managed queue/agenda item (separate from the FSRS review queue). [kind] is one of
 * TASK / ALARM / EVENT; [timeMs] is the (optional) scheduled time for alarms/events; [position]
 * gives the manual ordering.
 */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String,
    val timeMs: Long? = null,
    val position: Int = 0,
    val done: Boolean = false,
)
