package com.lnxv.player.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM guard for the 3->4 migration. It cannot replace Room's own schema validation (that needs a device or
 * exported schemas), but it pins the migration SQL to the entity so the two cannot drift apart silently.
 */
class VideoPrefsMigrationTest {
    private fun fakeDb(executed: MutableList<String>): SupportSQLiteDatabase =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf<Class<*>>(SupportSQLiteDatabase::class.java)) { _, m, args ->
            if (m.name == "execSQL") executed += args[0] as String
            when (m.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

    @Test fun migration_covers_version_3_to_4() {
        assertEquals(3, LNXVDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, LNXVDatabase.MIGRATION_3_4.endVersion)
    }

    @Test fun migration_only_creates_the_new_table() {
        val executed = mutableListOf<String>()
        LNXVDatabase.MIGRATION_3_4.migrate(fakeDb(executed))
        assertEquals(1, executed.size)
        assertTrue(executed[0].startsWith("CREATE TABLE IF NOT EXISTS video_playback_prefs"))
        assertTrue(executed.none { it.contains("ALTER", ignoreCase = true) || it.contains("DROP", ignoreCase = true) })
    }

    @Test fun migration_sql_matches_every_entity_field() {
        val sql = LNXVDatabase.MIGRATION_3_4_SQL.single()
        val fields = VideoPlaybackPrefsEntity::class.java.declaredFields.map { it.name }.filter { !it.startsWith("$") }
        assertEquals(setOf("videoId", "decoderMode", "audioTrackId", "subtitleTrackId", "subtitlesOff"), fields.toSet())
        fields.forEach { assertTrue("column $it missing in migration SQL", sql.contains("$it ")) }
        assertTrue(sql.contains("videoId INTEGER NOT NULL"))
        assertTrue(sql.contains("subtitlesOff INTEGER NOT NULL"))
        assertTrue(sql.contains("PRIMARY KEY(videoId)"))
    }
}
