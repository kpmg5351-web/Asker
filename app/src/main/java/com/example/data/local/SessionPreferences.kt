package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hesabatyDataStore: DataStore<Preferences> by preferencesDataStore(name = "hesabaty_session_prefs")

data class StoredSession(
    val userId: String? = null,
    val tenantId: String? = null,
    val role: String? = null,
    val username: String? = null,
    val rememberMe: Boolean = true,
    val darkMode: Boolean = false
)

class SessionPreferences(private val context: Context) {

    private object Keys {
        val USER_ID = stringPreferencesKey("session_user_id")
        val TENANT_ID = stringPreferencesKey("session_tenant_id")
        val ROLE = stringPreferencesKey("session_role")
        val USERNAME = stringPreferencesKey("session_username")
        val REMEMBER_ME = booleanPreferencesKey("session_remember_me")
        val DARK_MODE = booleanPreferencesKey("global_dark_mode")
    }

    val sessionFlow: Flow<StoredSession> = context.hesabatyDataStore.data.map { prefs ->
        StoredSession(
            userId = prefs[Keys.USER_ID],
            tenantId = prefs[Keys.TENANT_ID],
            role = prefs[Keys.ROLE],
            username = prefs[Keys.USERNAME],
            rememberMe = prefs[Keys.REMEMBER_ME] ?: true,
            darkMode = prefs[Keys.DARK_MODE] ?: false
        )
    }

    suspend fun saveSession(
        userId: String,
        tenantId: String?,
        role: String,
        username: String,
        rememberMe: Boolean
    ) {
        context.hesabatyDataStore.edit { prefs ->
            prefs[Keys.REMEMBER_ME] = rememberMe
            if (rememberMe) {
                prefs[Keys.USER_ID] = userId
                if (tenantId != null) prefs[Keys.TENANT_ID] = tenantId else prefs.remove(Keys.TENANT_ID)
                prefs[Keys.ROLE] = role
                prefs[Keys.USERNAME] = username
            } else {
                prefs.remove(Keys.USER_ID)
                prefs.remove(Keys.TENANT_ID)
                prefs.remove(Keys.ROLE)
                prefs.remove(Keys.USERNAME)
            }
        }
    }

    suspend fun clearSession() {
        context.hesabatyDataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.TENANT_ID)
            prefs.remove(Keys.ROLE)
            prefs.remove(Keys.USERNAME)
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.hesabatyDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }
}
