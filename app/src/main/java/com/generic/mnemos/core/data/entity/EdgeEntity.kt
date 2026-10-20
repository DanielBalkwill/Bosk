package com.generic.mnemos.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A directed, weighted connection between two nodes. [aiGenerated] edges render as
 * dashed/pending until the user confirms them (CP3).
 */
@Entity(
    tableName = "edges",
    indices = [Index("sourceNodeId"), Index("targetNodeId")],
    foreignKeys = [
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceNodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetNodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EdgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceNodeId: Long,
    val targetNodeId: Long,
    val relationshipLabel: String? = null,
    val strength: Float = 0.5f, // 0..1, drives line opacity
    val aiGenerated: Boolean = false,
    /** True for edges derived from a `[[wiki-link]]` in note text (kept in sync with the body). */
    val wikiLink: Boolean = false,
)
