package com.lnxv.player.core.subtitle.online

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onlineSubsStore: DataStore<Preferences> by preferencesDataStore(name = "lnxv_online_subtitles")

data class OnlineSubtitleConfig(
    val apiKey: String = "",
    val username: String = "",
    val loggedIn: Boolean = false,
    val baseUrl: String? = null,
    val languages: List<String> = listOf("en"),
    /** Remaining downloads reported by the last download response, null when unknown. */
    val remainingQuota: Int? = null
)

/**
 * User-entered OpenSubtitles settings. The API key lives in DataStore as the user typed it (it is the user's own key,
 * never bundled or logged); the session token is stored encrypted with [TokenCipher]. Passwords are never stored.
 */
class OnlineSubtitleSettings(private val context: Context) {
    private object K {
        val API_KEY = stringPreferencesKey("os_api_key")
        val USERNAME = stringPreferencesKey("os_username")
        val TOKEN = stringPreferencesKey("os_token_enc")
        val BASE_URL = stringPreferencesKey("os_base_url")
        val LANGS = stringPreferencesKey("os_languages")
        val QUOTA = intPreferencesKey("os_quota_remaining")
    }

    val config: Flow<OnlineSubtitleConfig> = context.onlineSubsStore.data.map { p ->
        OnlineSubtitleConfig(
            apiKey = p[K.API_KEY].orEmpty(),
            username = p[K.USERNAME].orEmpty(),
            loggedIn = !p[K.TOKEN].isNullOrEmpty(),
            baseUrl = p[K.BASE_URL],
            languages = p[K.LANGS]?.split(',')?.filter { it.isNotBlank() }?.ifEmpty { null } ?: listOf("en"),
            remainingQuota = p[K.QUOTA]
        )
    }

    suspend fun current(): OnlineSubtitleConfig = config.first()

    suspend fun setApiKey(key: String) = context.onlineSubsStore.edit { it[K.API_KEY] = key.trim() }
    suspend fun setUsername(name: String) = context.onlineSubsStore.edit { it[K.USERNAME] = name.trim() }
    suspend fun setLanguages(langs: List<String>) = context.onlineSubsStore.edit { it[K.LANGS] = langs.joinToString(",") }
    suspend fun setQuota(remaining: Int?) = context.onlineSubsStore.edit { if (remaining == null) it.remove(K.QUOTA) else it[K.QUOTA] = remaining }

    suspend fun saveLogin(token: String, baseUrl: String?) = context.onlineSubsStore.edit {
        it[K.TOKEN] = TokenCipher.encrypt(token)
        if (baseUrl == null) it.remove(K.BASE_URL) else it[K.BASE_URL] = baseUrl
    }

    suspend fun token(): String? = context.onlineSubsStore.data.first()[K.TOKEN]?.let { TokenCipher.decrypt(it) }

    suspend fun logout() = context.onlineSubsStore.edit {
        it.remove(K.TOKEN)
        it.remove(K.BASE_URL)
    }

    /** Removes the key, account name and token. */
    suspend fun clearAll() = context.onlineSubsStore.edit { it.clear() }
}
