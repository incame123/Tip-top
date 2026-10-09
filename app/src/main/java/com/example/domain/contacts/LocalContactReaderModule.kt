package com.example.domain.contacts

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NormalizedLocalContact(
    val localContactId: String,
    val localLabelHint: String, // Kept strictly on-device; NEVER uploaded or treated as authoritative caller ID
    val e164Number: String,
    val maskedNumber: String
)

/**
 * Module 3: Local Contact Reading Module.
 *
 * Reads contacts from Android's `ContactsContract.CommonDataKinds.Phone` ONLY when:
 * 1. Native `READ_CONTACTS` runtime permission is currently granted.
 * 2. User has explicitly enabled Contact Discovery and granted local reading consent.
 * 3. Triggered by an explicit user action (never runs silently in the background).
 *
 * Never uploads raw contact names or raw address books to the server.
 */
object LocalContactReaderModule {

    suspend fun readAndNormalizeContacts(
        context: Context,
        localConsentGranted: Boolean,
        maxContactsLimit: Int = 50
    ): Result<List<NormalizedLocalContact>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!localConsentGranted) {
                throw SecurityException("Cannot read contacts: explicit user consent for Contact Discovery has not been granted.")
            }
            if (!AndroidContactPermissionModule.isPermissionGranted(context)) {
                throw SecurityException("Cannot read contacts: Android READ_CONTACTS runtime permission is not granted.")
            }

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val seenE164 = mutableSetOf<String>()
            val normalizedList = mutableListOf<NormalizedLocalContact>()

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext() && normalizedList.size < maxContactsLimit) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val rawName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else "Contact"
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""

                    val e164 = PhoneNormalizationModule.normalizeToE164(rawNumber)
                    if (e164 != null && seenE164.add(e164)) {
                        normalizedList.add(
                            NormalizedLocalContact(
                                localContactId = id,
                                localLabelHint = rawName.take(40),
                                e164Number = e164,
                                maskedNumber = PhoneNormalizationModule.maskE164ForDisplay(e164)
                            )
                        )
                    }
                }
            }

            normalizedList
        }
    }
}
