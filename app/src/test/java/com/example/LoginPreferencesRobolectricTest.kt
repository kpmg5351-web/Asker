package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.SecurityUtils
import com.example.data.local.SessionPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoginPreferencesRobolectricTest {

    @Test
    fun encryptAndDecryptLocalSecret_roundTripsCleanly() {
        val secret = "ShopPass#2026!عربي"
        val encoded = SecurityUtils.encryptLocalSecret(secret)
        assertFalse(encoded.contains(secret))
        assertEquals(secret, SecurityUtils.decryptLocalSecret(encoded))
        assertNull(SecurityUtils.decryptLocalSecret("invalid-payload"))
    }

    @Test
    fun rememberedLoginAndBiometricAccount_surviveSessionLogout() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SessionPreferences(context)

        val encryptedPass = SecurityUtils.encryptLocalSecret("mySecret99")
        prefs.saveSession(
            userId = "user-1",
            tenantId = "tenant-1",
            role = "OWNER",
            username = "mahmoud_shop",
            rememberMe = true
        )
        prefs.saveRememberedLogin("mahmoud_shop", encryptedPass)
        prefs.saveBiometricAccount("user-1", "mahmoud_shop")

        // Simulate user tapping Logout -> active session cleared, remembered & biometric stay
        prefs.clearSession()

        val afterLogout = prefs.sessionFlow.first()
        assertNull(afterLogout.userId)
        assertNull(afterLogout.tenantId)
        assertTrue(afterLogout.savedRememberChecked)
        assertEquals("mahmoud_shop", afterLogout.savedLoginUsername)
        assertEquals("mySecret99", SecurityUtils.decryptLocalSecret(afterLogout.savedLoginPasswordEnc!!))
        assertEquals("user-1", afterLogout.biometricUserId)
        assertEquals("mahmoud_shop", afterLogout.biometricUsername)

        // Unchecking remember me clears saved credentials cleanly
        prefs.clearRememberedLogin()
        val afterClear = prefs.sessionFlow.first()
        assertFalse(afterClear.savedRememberChecked)
        assertNull(afterClear.savedLoginUsername)
        assertNull(afterClear.savedLoginPasswordEnc)
        assertEquals("user-1", afterClear.biometricUserId)
    }

    @Test
    fun darkModePreference_togglesAndPersists() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SessionPreferences(context)
        prefs.setDarkMode(true)
        assertTrue(prefs.sessionFlow.first().darkMode)
        prefs.setDarkMode(false)
        assertFalse(prefs.sessionFlow.first().darkMode)
    }
}
