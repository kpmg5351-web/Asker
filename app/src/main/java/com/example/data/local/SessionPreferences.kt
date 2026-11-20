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
    val darkMode: Boolean = false,
    val savedLoginUsername: String? = null,
    val savedLoginPasswordEnc: String? = null,
    val savedRememberChecked: Boolean = false,
    val biometricUserId: String? = null,
    val biometricUsername: String? = null
)

class SessionPreferences(private val context: Context) {

    private object Keys {
        val USER_ID = stringPreferencesKey("session_user_id")
        val TENANT_ID = stringPreferencesKey("session_tenant_id")
        val ROLE = stringPreferencesKey("session_role")
        val USERNAME = stringPreferencesKey("session_username")
        val REMEMBER_ME = booleanPreferencesKey("session_remember_me")
        val DARK_MODE = booleanPreferencesKey("global_dark_mode")
        val SAVED_LOGIN_USERNAME = stringPreferencesKey("saved_login_username")
        val SAVED_LOGIN_PASSWORD_ENC = stringPreferencesKey("saved_login_password_enc")
        val SAVED_REMEMBER_CHECKED = booleanPreferencesKey("saved_remember_checked")
        val BIOMETRIC_USER_ID = stringPreferencesKey("biometric_user_id")
        val BIOMETRIC_USERNAME = stringPreferencesKey("biometric_username")
    }

    val sessionFlow: Flow<StoredSession> = context.hesabatyDataStore.data.map { prefs ->
        StoredSession(
            userId = prefs[Keys.USER_ID],
            tenantId = prefs[Keys.TENANT_ID],
            role = prefs[Keys.ROLE],
            username = prefs[Keys.USERNAME],
            rememberMe = prefs[Keys.REMEMBER_ME] ?: true,
            darkMode = prefs[Keys.DARK_MODE] ?: false,
            savedLoginUsername = prefs[Keys.SAVED_LOGIN_USERNAME],
            savedLoginPasswordEnc = prefs[Keys.SAVED_LOGIN_PASSWORD_ENC],
            savedRememberChecked = prefs[Keys.SAVED_REMEMBER_CHECKED] ?: false,
            biometricUserId = prefs[Keys.BIOMETRIC_USER_ID],
            biometricUsername = prefs[Keys.BIOMETRIC_USERNAME]
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

    suspend fun saveRememberedLogin(username: String, encryptedPassword: String) {
        context.hesabatyDataStore.edit { prefs ->
            prefs[Keys.SAVED_LOGIN_USERNAME] = username
            prefs[Keys.SAVED_LOGIN_PASSWORD_ENC] = encryptedPassword
            prefs[Keys.SAVED_REMEMBER_CHECKED] = true
        }
    }

    suspend fun clearRememberedLogin() {
        context.hesabatyDataStore.edit { prefs ->
            prefs.remove(Keys.SAVED_LOGIN_USERNAME)
            prefs.remove(Keys.SAVED_LOGIN_PASSWORD_ENC)
            prefs[Keys.SAVED_REMEMBER_CHECKED] = false
        }
    }

    suspend fun saveBiometricAccount(userId: String, username: String) {
        context.hesabatyDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_USER_ID] = userId
            prefs[Keys.BIOMETRIC_USERNAME] = username
        }
    }

    suspend fun clearBiometricAccount() {
        context.hesabatyDataStore.edit { prefs ->
            prefs.remove(Keys.BIOMETRIC_USER_ID)
            prefs.remove(Keys.BIOMETRIC_USERNAME)
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.hesabatyDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }
}
