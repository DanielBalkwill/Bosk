package app.bosk.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.bosk.core.data.dao.GraphDao
import app.bosk.core.data.dao.KnowledgeProfileDao
import app.bosk.core.data.dao.NoteDao
import app.bosk.core.data.dao.NoteVersionDao
import app.bosk.core.data.dao.QueueDao
import app.bosk.core.data.dao.ReviewDao
import app.bosk.core.data.dao.SpeechQueueDao
import app.bosk.core.data.entity.EdgeEntity
import app.bosk.core.data.entity.KnowledgeProfileEntity
import app.bosk.core.data.entity.NodeEntity
import app.bosk.core.data.entity.NoteEntity
import app.bosk.core.data.entity.NoteFtsEntity
import app.bosk.core.data.entity.NoteVersionEntity
import app.bosk.core.data.entity.QueueItemEntity
import app.bosk.core.data.entity.ReviewEntity
import app.bosk.core.data.entity.SpeechItemEntity

@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        NoteVersionEntity::class,
        NodeEntity::class,
        EdgeEntity::class,
        ReviewEntity::class,
        KnowledgeProfileEntity::class,
        SpeechItemEntity::class,
        QueueItemEntity::class,
    ],
    version = 7, // v7: note_versions history snapshots (v6: notes_fts; v5: recycle bin)
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BoskDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteVersionDao(): NoteVersionDao
    abstract fun graphDao(): GraphDao
    abstract fun reviewDao(): ReviewDao
    abstract fun knowledgeProfileDao(): KnowledgeProfileDao
    abstract fun speechQueueDao(): SpeechQueueDao
    abstract fun queueDao(): QueueDao

    companion object {
        const val NAME = "bosk.db"
    }
}
