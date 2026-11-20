package com.lnxv.player.core.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.audioFxStore: DataStore<Preferences> by preferencesDataStore(name = "lnxv_audiofx")

/** Audio effect settings remembered per output route. Stored on this device only. */
class AudioFxStore(private val context: Context) {
    private fun key(route: OutputRoute) = stringPreferencesKey("route_${route.name}")

    suspend fun load(route: OutputRoute): AudioFxSettings =
        AudioFxSettings.decode(context.audioFxStore.data.map { it[key(route)] }.first())

    suspend fun save(route: OutputRoute, s: AudioFxSettings) {
        context.audioFxStore.edit { it[key(route)] = s.encode() }
    }
}
