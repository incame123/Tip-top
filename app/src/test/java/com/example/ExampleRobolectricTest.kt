package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.contacts.AndroidContactPermissionModule
import com.example.domain.contacts.ContactPermissionStatus
import com.example.domain.contacts.LocalContactReaderModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_matchesTicTacToePro() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Tic-Tac-Toe Pro", appName)
    }

    @Test
    fun contactPermission_defaultsToNotGrantedAndRejectsUnconsentedRead() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // By default in a clean environment, READ_CONTACTS is not granted
        assertFalse(AndroidContactPermissionModule.isPermissionGranted(context))
        assertEquals(
            ContactPermissionStatus.NOT_REQUESTED_YET,
            AndroidContactPermissionModule.evaluatePermissionStatus(context, hasRequestedBefore = false)
        )

        // Attempting to read contacts without consent or permission fails fast with SecurityException
        val resultWithoutConsent = LocalContactReaderModule.readAndNormalizeContacts(
            context = context,
            localConsentGranted = false
        )
        assertTrue(resultWithoutConsent.isFailure)
        assertTrue(resultWithoutConsent.exceptionOrNull() is SecurityException)

        val resultWithoutPermission = LocalContactReaderModule.readAndNormalizeContacts(
            context = context,
            localConsentGranted = true
        )
        assertTrue(resultWithoutPermission.isFailure)
        assertTrue(resultWithoutPermission.exceptionOrNull() is SecurityException)
    }
}
