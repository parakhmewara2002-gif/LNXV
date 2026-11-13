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
        VaultThumbEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LNXVDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao

    companion object {
        /** v2: encrypted vault. Legacy isVault rows are kept untouched (vaultFile = NULL). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN vaultFile TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS vault_thumbs (videoId INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY(videoId))")
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
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
