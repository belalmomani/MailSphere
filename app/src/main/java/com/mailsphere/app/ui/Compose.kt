package com.mailsphere.app.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mailsphere.app.data.AccountStore
import com.mailsphere.app.data.ComposeDraft
import com.mailsphere.app.data.OAuth
import com.mailsphere.app.data.Smtp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ComposeScreen(onBack: () -> Unit, onSent: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts = remember { AccountStore.list(ctx) }
    var from by remember { mutableStateOf(ComposeDraft.from ?: accounts.firstOrNull()?.email ?: "") }
    var to by remember { mutableStateOf(ComposeDraft.to) }
    var subject by remember { mutableStateOf(ComposeDraft.subject) }
    var body by remember { mutableStateOf(ComposeDraft.body) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Exception?>(null) }
    val sentText = tr("تم الإرسال", "Sent")
    val valid = to.contains("@") && from.isNotBlank()

    Column(Modifier.fillMaxSize().screenInsets(bottom = true).imePadding()) {
        ScreenHeader(tr("رسالة جديدة", "New message"), null, onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (accounts.size > 1) {
                Text(tr("من:", "From:"), fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a ->
                        FilterChip(selected = from == a.email, onClick = { from = a.email }, label = { Text(a.email) })
                    }
                }
            } else {
                Text(tr("من: ", "From: ") + from, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = to, onValueChange = { to = it; failure = null },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text(tr("إلى", "To")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = subject, onValueChange = { subject = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text(tr("الموضوع", "Subject")) }
            )
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth(), minLines = 8, shape = RoundedCornerShape(14.dp),
                label = { Text(tr("الرسالة", "Message")) }
            )
            failure?.let { e ->
                Text(
                    tr("تعذّر الإرسال: ", "Could not send: ") + (e.message ?: e.javaClass.simpleName).take(140),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
                if (accounts.firstOrNull { it.email == from }?.provider == "microsoft") {
                    Text(
                        tr("إن كان حساب Outlook أُضيف قبل هذا الإصدار فاحذفه من الإعدادات وأعد تسجيل الدخول لمنح صلاحية الإرسال.",
                            "If this Outlook account was added before this version, remove it in Settings and sign in again to grant the send permission."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = {
                    val acc = accounts.firstOrNull { it.email == from }
                    if (acc != null) {
                        busy = true
                        failure = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val token = if (acc.authState != null) OAuth.accessToken(ctx, acc) else null
                                    Smtp.send(acc, token, to.trim(), subject, body, ComposeDraft.inReplyTo)
                                }
                                Toast.makeText(ctx, sentText, Toast.LENGTH_SHORT).show()
                                ComposeDraft.reset()
                                onSent()
                            } catch (e: Exception) {
                                failure = e
                            }
                            busy = false
                        }
                    }
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(tr("إرسال", "Send"), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
