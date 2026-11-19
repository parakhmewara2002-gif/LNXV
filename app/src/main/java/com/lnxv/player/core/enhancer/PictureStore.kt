package com.lnxv.player.core.enhancer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pictureStore: DataStore<Preferences> by preferencesDataStore(name = "lnxv_picture")

/**
 * Global default and per-video Picture settings. Per-video entries are keyed by the library video id and are never
 * written for vault, transient or network videos (the caller decides that).
 */
class PictureStore(private val context: Context) {
    private val globalKey = stringPreferencesKey("global")
    private fun videoKey(id: Long) = stringPreferencesKey("v_$id")

    suspend fun global(): PictureSettings = PictureSettings.decode(context.pictureStore.data.map { it[globalKey] }.first())

    /** The video's own settings, or null when none were saved for it. */
    suspend fun forVideo(id: Long): PictureSettings? =
        context.pictureStore.data.map { it[videoKey(id)] }.first()?.let { PictureSettings.decode(it) }

    suspend fun saveGlobal(s: PictureSettings) { context.pictureStore.edit { it[globalKey] = s.encode() } }
    suspend fun saveForVideo(id: Long, s: PictureSettings) { context.pictureStore.edit { it[videoKey(id)] = s.encode() } }
    suspend fun clearVideo(id: Long) { context.pictureStore.edit { it.remove(videoKey(id)) } }
}
