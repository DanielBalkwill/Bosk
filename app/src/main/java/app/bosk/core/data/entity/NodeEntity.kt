package app.bosk.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A graph node. Usually maps to a note, but may be a standalone concept (noteId null). */
@Entity(
    tableName = "nodes",
    indices = [Index("noteId")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class NodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long?,
    val label: String,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
)
