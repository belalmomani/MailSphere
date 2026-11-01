package com.mailsphere.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.mailsphere.app.data.OAuth
import com.mailsphere.app.data.Providers
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.mail.AuthenticationFailedException

@Composable
fun AddAccountScreen(onBack: () -> Unit, onDone: () -> Unit, onOpenGuide: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("993") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Exception?>(null) }

    // ---- OAuth sign-in (Google / Microsoft) ----
    val service = remember { AuthorizationService(ctx) }
    DisposableEffect(Unit) { onDispose { service.dispose() } }
    var pendingProvider by remember { mutableStateOf<String?>(null) }
    var signInError by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val resp = data?.let { AuthorizationResponse.fromIntent(it) }
        val ex = data?.let { AuthorizationException.fromIntent(it) }
        val provider = pendingProvider
        if (resp == null || provider == null) {
            signInError = if (ex == null) "cancelled" else (ex.errorDescription ?: ex.error ?: "error")
            busy = false
        } else {
            busy = true
            val state = AuthState(resp, ex)
            service.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp, tokenEx ->
                state.update(tokenResp, tokenEx)
                val email = OAuth.emailFromIdToken(tokenResp?.idToken)
                if (tokenResp == null || email == null) {
                    signInError = if (tokenResp == null) (tokenEx?.errorDescription ?: tokenEx?.error ?: "error") else "noemail"
                    busy = false
                } else {
                    val (host, port) = OAuth.imapHost(provider)
                    AccountStore.add(ctx, Account(email.lowercase(), host, port, "", provider, state.jsonSerializeString()))
                    busy = false
                    onDone()
                }
            }
        }
    }
    val startSignIn: (String) -> Unit = { provider ->
        pendingProvider = provider
        signInError = null
        launcher.launch(service.getAuthorizationRequestIntent(OAuth.request(ctx, provider)))
    }
    val googleReady = OAuth.clientId(ctx, "google").isNotBlank()
    val msReady = OAuth.clientId(ctx, "microsoft").isNotBlank()

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
            Text(tr("تسجيل الدخول السريع", "Quick sign-in"), fontWeight = FontWeight.Bold)
            Button(
                onClick = { startSignIn("google") }, enabled = googleReady && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
            ) { Text(tr("المتابعة بحساب Google (Gmail)", "Continue with Google (Gmail)"), fontWeight = FontWeight.Bold) }
            Button(
                onClick = { startSignIn("microsoft") }, enabled = msReady && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
            ) { Text(tr("المتابعة بحساب Microsoft (Outlook / Hotmail)", "Continue with Microsoft (Outlook / Hotmail)"), fontWeight = FontWeight.Bold) }
            if (!googleReady || !msReady) {
                Text(
                    tr("الزر المعطّل لم يُضبط بعد (معرّف العميل غير مُدخل).", "A disabled button is not configured yet (client ID missing)."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            signInError?.let { code ->
                val msg = when (code) {
                    "cancelled" -> tr("تم الإلغاء.", "Cancelled.")
                    "noemail" -> tr("تعذّر قراءة البريد من الحساب.", "Could not read the email from the account.")
                    else -> tr("فشل تسجيل الدخول: ", "Sign-in failed: ") + code.take(120)
                }
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text(tr("متقدم: كلمة مرور التطبيق", "Advanced: app password"), fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenGuide) { Text(tr("كيف أنشئ كلمة مرور التطبيق؟ (Yahoo / iCloud)", "How do I create an app password? (Yahoo / iCloud)")) }
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
