package com.lnxv.player.core.bookmarks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.bookmarkStore: DataStore<Preferences> by preferencesDataStore(name = "lnxv_bookmarks")

/** Per-video bookmarks keyed by library video id. The caller never uses it for vault, transient or network videos. */
class BookmarkStore(private val context: Context) {
    private fun key(id: Long) = stringPreferencesKey("b_$id")

    suspend fun load(id: Long): List<Bookmark> = BookmarkList.decode(context.bookmarkStore.data.map { it[key(id)] }.first())

    suspend fun save(id: Long, list: List<Bookmark>) {
        context.bookmarkStore.edit { if (list.isEmpty()) it.remove(key(id)) else it[key(id)] = BookmarkList.encode(list) }
    }
}
