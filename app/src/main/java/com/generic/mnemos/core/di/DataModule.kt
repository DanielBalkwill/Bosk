package com.generic.mnemos.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.generic.mnemos.core.data.MnemosDatabase
import com.generic.mnemos.core.data.dao.GraphDao
import com.generic.mnemos.core.data.dao.KnowledgeProfileDao
import com.generic.mnemos.core.data.dao.NoteDao
import com.generic.mnemos.core.data.dao.QueueDao
import com.generic.mnemos.core.data.dao.ReviewDao
import com.generic.mnemos.core.data.dao.SpeechQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Provides the concrete singletons: database, DAOs, HTTP client, JSON. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** v5 → v6: add the FTS4 full-text index over notes and backfill it from existing rows. */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(`title` TEXT, `body` TEXT, content=`notes`)",
            )
            db.execSQL("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
        }
    }

    /** v6 → v7: note_versions — historical body snapshots ("restore previous version"). */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `note_versions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `noteId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_versions_noteId` ON `note_versions` (`noteId`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MnemosDatabase =
        Room.databaseBuilder(context, MnemosDatabase::class.java, MnemosDatabase.NAME)
            // Real migrations keep the vault (v5→v6 FTS, v6→v7 version history); destructive
            // fallback remains only for ancient versions with no migration path.
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideNoteDao(db: MnemosDatabase): NoteDao = db.noteDao()
    @Provides fun provideNoteVersionDao(db: MnemosDatabase): com.generic.mnemos.core.data.dao.NoteVersionDao =
        db.noteVersionDao()
    @Provides fun provideGraphDao(db: MnemosDatabase): GraphDao = db.graphDao()
    @Provides fun provideReviewDao(db: MnemosDatabase): ReviewDao = db.reviewDao()
    @Provides fun provideKnowledgeProfileDao(db: MnemosDatabase): KnowledgeProfileDao =
        db.knowledgeProfileDao()
    @Provides fun provideSpeechQueueDao(db: MnemosDatabase): SpeechQueueDao = db.speechQueueDao()
    @Provides fun provideQueueDao(db: MnemosDatabase): QueueDao = db.queueDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // generous for streamed generations
        .writeTimeout(30, TimeUnit.SECONDS)
        // NOTE: intentionally NO logging interceptor — the API key rides in the URL query
        // and global rule #5 forbids logging it. Add a key-redacting logger if ever needed.
        .build()
}
