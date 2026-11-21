package com.example.core

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

data class PickedContact(
    val name: String,
    val phone: String
)

sealed class ContactPickResult {
    data class Success(val contact: PickedContact) : ContactPickResult()
    data object Cancelled : ContactPickResult()
    data class Error(val message: String) : ContactPickResult()
}

object ContactPickerHelper {

    fun createPickPhoneContactIntent(): Intent {
        return Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    }

    /**
     * Extracts contact display name and cleaned phone number from the URI returned by
     * [ContactsContract.CommonDataKinds.Phone.CONTENT_URI] without requiring READ_CONTACTS.
     */
    fun extractContactFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        countryCode: String = "20"
    ): ContactPickResult {
        return try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return ContactPickResult.Error("تعذر قراءة بيانات جهة الاتصال المختارة.")
                }
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val fallbackNameIdx = if (nameIdx >= 0) nameIdx else cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

                val rawName = if (fallbackNameIdx >= 0) cursor.getString(fallbackNameIdx).orEmpty().trim() else ""
                val rawPhone = if (phoneIdx >= 0) cursor.getString(phoneIdx).orEmpty().trim() else ""
                val cleanedPhone = PhoneUtils.cleanPickedContactPhone(rawPhone, countryCode)

                if (rawName.isBlank() && cleanedPhone.isBlank()) {
                    ContactPickResult.Error("جهة الاتصال المختارة لا تحتوي على اسم أو رقم هاتف.")
                } else {
                    ContactPickResult.Success(PickedContact(name = rawName, phone = cleanedPhone))
                }
            } ?: ContactPickResult.Error("تعذر فتح بيانات جهة الاتصال.")
        } catch (_: SecurityException) {
            ContactPickResult.Error("تعذر الوصول إلى جهة الاتصال المختارة من هذا الجهاز.")
        } catch (_: IllegalArgumentException) {
            // Fallback query without projection in case an OEM Contacts provider uses different columns
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return ContactPickResult.Error("تعذر قراءة بيانات جهة الاتصال المختارة.")
                    }
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?: cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val rawName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val rawPhone = if (phoneIdx >= 0) cursor.getString(phoneIdx).orEmpty().trim() else ""
                    val cleanedPhone = PhoneUtils.cleanPickedContactPhone(rawPhone, countryCode)
                    if (rawName.isBlank() && cleanedPhone.isBlank()) {
                        ContactPickResult.Error("جهة الاتصال المختارة لا تحتوي على اسم أو رقم هاتف.")
                    } else {
                        ContactPickResult.Success(PickedContact(name = rawName, phone = cleanedPhone))
                    }
                } ?: ContactPickResult.Error("تعذر فتح بيانات جهة الاتصال.")
            } catch (_: Exception) {
                ContactPickResult.Error("تعذر قراءة بيانات جهة الاتصال المختارة.")
            }
        } catch (_: Exception) {
            ContactPickResult.Error("حدث خطأ أثناء استيراد جهة الاتصال.")
        }
    }
}
