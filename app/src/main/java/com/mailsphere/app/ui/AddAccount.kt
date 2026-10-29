package com.mailsphere.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mailsphere.app.data.Account
import com.mailsphere.app.data.AccountStore
import com.mailsphere.app.data.ImapClient
import com.mailsphere.app.data.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.mail.AuthenticationFailedException

@Composable
fun AddAccountScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("993") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Exception?>(null) }

    val preset = Providers.presetFor(email.trim())
    val hasAt = email.contains("@")
    val needsHost = hasAt && preset == null
    val unsupported = preset?.supported == false
    val valid = hasAt && password.isNotBlank() && !unsupported && (!needsHost || host.isNotBlank())

    Column(Modifier.fillMaxSize().screenInsets(bottom = true)) {
        ScreenHeader(
            tr("إضافة حساب بريد", "Add email account"),
            tr("اتصال مباشر بخادم بريدك (IMAP)", "Direct connection to your mail server (IMAP)"),
            onBack
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
                Text(
                    tr(
                        "Gmail وYahoo وiCloud: استخدم «كلمة مرور التطبيق» (App Password) وليس كلمة مرورك العادية؛ تتطلب تفعيل التحقق بخطوتين من إعدادات الحساب.",
                        "Gmail, Yahoo and iCloud: use an App Password, not your normal password. It requires 2-step verification in your account settings."
                    ),
                    modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it; failure = null },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text(tr("البريد الإلكتروني", "Email address")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it; failure = null },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text(tr("كلمة مرور التطبيق", "App password")) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            if (needsHost) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                    label = { Text(tr("خادم IMAP (مثال: imap.example.com)", "IMAP server (e.g. imap.example.com)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                    label = { Text(tr("المنفذ (SSL)", "Port (SSL)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            if (unsupported) {
                Text(
                    tr("حسابات Outlook/Hotmail/Live لا تقبل كلمة مرور IMAP؛ تحتاج تسجيل دخول OAuth وسيُضاف في مرحلة لاحقة.",
                        "Outlook/Hotmail/Live accounts no longer accept IMAP passwords; they need OAuth sign-in, which will come in a later phase."),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
            }
            failure?.let { e ->
                val msg = if (e is AuthenticationFailedException)
                    tr("البريد أو كلمة المرور غير صحيحة. تأكد أنك تستخدم كلمة مرور التطبيق وأن IMAP مفعّل.",
                        "Wrong email or password. Make sure you use an app password and that IMAP is enabled.")
                else
                    tr("تعذّر الاتصال بالخادم. تحقق من الخادم والمنفذ والإنترنت.", "Could not connect. Check the server, port and your internet.") +
                        (e.message?.take(100)?.let { " ($it)" } ?: "")
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    val acc = Account(
                        email.trim().lowercase(),
                        preset?.host ?: host.trim(),
                        preset?.port ?: (port.toIntOrNull() ?: 993),
                        password
                    )
                    busy = true
                    failure = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { ImapClient.testLogin(acc) }
                            AccountStore.add(ctx, acc)
                            onDone()
                        } catch (e: Exception) {
                            failure = e
                        }
                        busy = false
                    }
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(tr("اختبار الاتصال وإضافة الحساب", "Test connection & add account"), fontWeight = FontWeight.Bold)
            }
            Text(
                tr("تُحفظ بيانات الدخول مشفّرة على هذا الجهاز فقط، وتُجلب الرسائل مباشرة من خادم بريدك. لا يُرسل شيء إلينا.",
                    "Credentials are stored encrypted on this device only, and messages are fetched directly from your mail server. Nothing is sent to us."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
