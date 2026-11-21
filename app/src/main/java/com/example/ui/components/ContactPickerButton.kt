package com.example.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ContactPickResult
import com.example.core.ContactPickerHelper
import com.example.core.PickedContact
import com.example.ui.theme.HesabatyThemeExtras

@Composable
fun ContactPickerButton(
    label: String = "اختيار الاسم والرقم من جهات الاتصال",
    testTag: String = "pick_from_contacts_button",
    onContactPicked: (PickedContact) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiScale = HesabatyThemeExtras.uiScale

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data
        if (uri == null) {
            onError("لم يتم استلام بيانات جهة الاتصال.")
            return@rememberLauncherForActivityResult
        }
        when (val extracted = ContactPickerHelper.extractContactFromUri(context.contentResolver, uri)) {
            is ContactPickResult.Success -> onContactPicked(extracted.contact)
            is ContactPickResult.Error -> onError(extracted.message)
            ContactPickResult.Cancelled -> Unit
        }
    }

    FilledTonalButton(
        onClick = {
            try {
                launcher.launch(ContactPickerHelper.createPickPhoneContactIntent())
            } catch (_: ActivityNotFoundException) {
                onError("لا يوجد تطبيق جهات اتصال متاح على هذا الهاتف.")
            } catch (_: Exception) {
                onError("تعذر فتح جهات الاتصال على هذا الجهاز.")
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        contentPadding = PaddingValues(
            horizontal = if (uiScale.isCompact) 12.dp else 14.dp,
            vertical = 10.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Contacts,
                contentDescription = label,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
