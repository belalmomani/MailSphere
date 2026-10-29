package com.mailsphere.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mailsphere.app.R
import com.mailsphere.app.ui.theme.BrandGradient
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(BrandGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = "MailSphere", modifier = Modifier.size(112.dp))
            Spacer(Modifier.height(20.dp))
            Text("MailSphere", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(tr("كل بريدك .. في مكان واحد", "All your mail .. in one place"), color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun WelcomeScreen(canGoBack: Boolean, onBack: () -> Unit, onExplore: () -> Unit, onAddAccount: () -> Unit) {
    val providers = listOf(
        Triple("Gmail", "gmail.com", Color(0xFFEA4335)),
        Triple("Yahoo", "yahoo.com", Color(0xFF6001D2)),
        Triple("Outlook", "outlook.com", Color(0xFF0A64D8))
    )

    Column(
        Modifier.fillMaxSize().background(BrandGradient).screenInsets(bottom = true)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (canGoBack) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع", "Back"), tint = Color.White)
                }
            }
        } else Spacer(Modifier.height(24.dp))
        Text(tr("مرحبًا بك في", "Welcome to"), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
        Text("MailSphere", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            tr("إدارة بريدك الذكي .. ببساطة", "Smart email management .. made simple"),
            color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(96.dp))
        Spacer(Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = { onAddAccount() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(tr("إضافة حساب بريد إلكتروني", "Add an email account"), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Add, null)
                }
                Spacer(Modifier.height(12.dp))
                providers.forEach { (name, domain, color) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onAddAccount() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name.first().toString(), color, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold)
                            Text(domain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onExplore,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) { Text(tr("استكشاف التطبيق ببيانات تجريبية", "Explore the app with demo data")) }
    }
}
