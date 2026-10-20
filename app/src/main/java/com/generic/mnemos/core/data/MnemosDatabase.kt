package com.generic.mnemos.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.generic.mnemos.core.data.dao.GraphDao
import com.generic.mnemos.core.data.dao.KnowledgeProfileDao
import com.generic.mnemos.core.data.dao.NoteDao
import com.generic.mnemos.core.data.dao.NoteVersionDao
import com.generic.mnemos.core.data.dao.QueueDao
import com.generic.mnemos.core.data.dao.ReviewDao
import com.generic.mnemos.core.data.dao.SpeechQueueDao
import com.generic.mnemos.core.data.entity.EdgeEntity
import com.generic.mnemos.core.data.entity.KnowledgeProfileEntity
import com.generic.mnemos.core.data.entity.NodeEntity
import com.generic.mnemos.core.data.entity.NoteEntity
import com.generic.mnemos.core.data.entity.NoteFtsEntity
import com.generic.mnemos.core.data.entity.NoteVersionEntity
import com.generic.mnemos.core.data.entity.QueueItemEntity
import com.generic.mnemos.core.data.entity.ReviewEntity
import com.generic.mnemos.core.data.entity.SpeechItemEntity

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
abstract class MnemosDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteVersionDao(): NoteVersionDao
    abstract fun graphDao(): GraphDao
    abstract fun reviewDao(): ReviewDao
    abstract fun knowledgeProfileDao(): KnowledgeProfileDao
    abstract fun speechQueueDao(): SpeechQueueDao
    abstract fun queueDao(): QueueDao

    companion object {
        const val NAME = "mnemos.db"
    }
}
