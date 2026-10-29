package com.mailsphere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun OrganizeScreen(onBack: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) } // 0 idle, 1 running, 2 done
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(phase) {
        if (phase == 1) {
            for (i in 1..20) { delay(150); progress = i / 20f }
            phase = 2
        }
    }
    val steps = listOf(
        tr("اكتشاف الجهات المرسلة", "Detect senders"),
        tr("إنشاء مجلدات تلقائية", "Create automatic folders"),
        tr("تصنيف الرسائل حسب الفئة", "Classify messages by category"),
        tr("تنظيم حسب السنوات والأشهر", "Organize by years and months")
    )

    Column(Modifier.fillMaxSize().screenInsets(bottom = true)) {
        ScreenHeader(tr("رتّب بريدي بالكامل", "Organize my whole mail"), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DemoBanner()
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(110.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(tr("سيتم تحليل رسائلك وتنظيمها تلقائيًا", "Your messages will be analyzed and organized automatically"), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))

            when (phase) {
                0 -> Button(onClick = { progress = 0f; phase = 1 }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(tr("بدء التنظيم (محاكاة)", "Start organizing (simulation)"), fontWeight = FontWeight.Bold)
                }
                1 -> Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.height(8.dp))
                    Text(tr("جارٍ التنظيم... ${(progress * 100).toInt()}%", "Organizing... ${(progress * 100).toInt()}%"), style = MaterialTheme.typography.bodySmall)
                }
                else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("اكتملت المحاكاة — لم تُعدَّل أي رسائل", "Simulation finished — no messages were changed"), fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { phase = 0 }) { Text(tr("إعادة", "Again")) }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(tr("ما الذي سيفعله التطبيق؟", "What will the app do?"), fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            steps.forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(it)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                tr("هذه شاشة تجريبية: التنظيم الفعلي سيُنفَّذ في مرحلة لاحقة. ولن يُحذف أي شيء دون تأكيد منك.",
                    "This is a preview screen: real organizing comes in a later phase. Nothing will ever be deleted without your confirmation."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
