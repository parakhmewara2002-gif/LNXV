package com.lnxv.player.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VideoEntity::class,
        PlaybackHistoryEntity::class,
        FavoriteEntity::class,
        VaultThumbEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        VideoPlaybackPrefsEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LNXVDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun videoPrefsDao(): VideoPrefsDao

    companion object {
        /** v2: encrypted vault. Legacy isVault rows are kept untouched (vaultFile = NULL). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN vaultFile TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS vault_thumbs (videoId INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY(videoId))")
            }
        }

        /** v3: user playlists. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist_items (playlistId INTEGER NOT NULL, videoId INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlistId, videoId))")
            }
        }

        /** SQL of the 3->4 migration, exposed so a unit test can pin it to the entity definition. */
        val MIGRATION_3_4_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS video_playback_prefs (videoId INTEGER NOT NULL, decoderMode TEXT, audioTrackId TEXT, subtitleTrackId TEXT, subtitlesOff INTEGER NOT NULL, PRIMARY KEY(videoId))"
        )

        /** v4: per-video decoder / audio / subtitle choices. Purely additive: no existing table is touched. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4_SQL.forEach { db.execSQL(it) }
            }
        }

        @Volatile
        private var INSTANCE: LNXVDatabase? = null

        fun getInstance(context: Context): LNXVDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LNXVDatabase::class.java,
                    "lnxv_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
