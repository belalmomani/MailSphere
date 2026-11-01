package com.mailsphere.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GuideScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    fun open(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
    }
    Column(Modifier.fillMaxSize().screenInsets(bottom = true)) {
        ScreenHeader(tr("كيف أنشئ كلمة مرور التطبيق؟", "How to create an app password"), null, onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                tr("كلمة مرور التطبيق كلمة خاصة تنشئها مرة واحدة من إعدادات حسابك، وتُستخدم لهذا التطبيق فقط، ويمكنك حذفها في أي وقت دون تغيير كلمة مرورك الأصلية.",
                    "An app password is a special password you create once in your account settings. It works for this app only and you can delete it any time without changing your real password."),
                style = MaterialTheme.typography.bodyMedium
            )
            GuideCard(
                "Yahoo",
                tr("1) افتح صفحة أمان الحساب وسجّل الدخول.\n2) اختر «إنشاء كلمة مرور التطبيق» (Generate app password).\n3) سمّها MailSphere ثم أنشئها.\n4) انسخ الكلمة الظاهرة (16 حرفًا) والصقها في خانة كلمة مرور التطبيق.",
                    "1) Open Account security and sign in.\n2) Choose “Generate app password”.\n3) Name it MailSphere and generate it.\n4) Copy the 16-character password and paste it in the app-password field."),
                tr("فتح صفحة Yahoo", "Open Yahoo page")
            ) { open("https://login.yahoo.com/account/security") }
            GuideCard(
                "iCloud",
                tr("1) سجّل الدخول إلى حساب Apple (يلزم تفعيل التحقق بخطوتين).\n2) اختر «تسجيل الدخول والأمان» ثم «كلمات المرور الخاصة بالتطبيقات».\n3) أنشئ كلمة جديدة باسم MailSphere وانسخها.",
                    "1) Sign in to your Apple Account (two-factor authentication is required).\n2) Open “Sign-In and Security” then “App-Specific Passwords”.\n3) Create a new one named MailSphere and copy it."),
                tr("فتح حساب Apple", "Open Apple Account")
            ) { open("https://account.apple.com") }
            GuideCard(
                "Gmail",
                tr("الأفضل لـ Gmail استخدام زر «المتابعة بحساب Google». وإن أردت كلمة مرور تطبيق: فعّل التحقق بخطوتين ثم أنشئ كلمة من صفحة App passwords.",
                    "For Gmail the “Continue with Google” button is best. If you still want an app password: turn on 2-step verification, then create one on the App passwords page."),
                tr("فتح صفحة Google", "Open Google page")
            ) { open("https://myaccount.google.com/apppasswords") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GuideCard(title: String, steps: String, button: String, onOpen: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(steps, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpen) { Text(button) }
        }
    }
}
