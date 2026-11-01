package com.mailsphere.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mailsphere.app.data.Group
import com.mailsphere.app.data.MailDb
import com.mailsphere.app.data.Organizer
import com.mailsphere.app.data.SyncManager
import com.mailsphere.app.data.toDemoMail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val arMonths = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
private val enMonths = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

@Composable
private fun monthName(m: Int): String {
    val i = (m - 1).coerceIn(0, 11)
    return tr(arMonths[i], enMonths[i])
}

/** Years as folders; tap a year to open it and see its months; tap a month to list its messages. type "all"/key "_" = everything. */
@Composable
fun YearMonthTree(type: String, key: String, onOpenMonth: (String) -> Unit) {
    val ctx = LocalContext.current
    var months by remember { mutableStateOf<List<Group>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(type, key, SyncManager.lastFinished) {
        months = withContext(Dispatchers.IO) { MailDb.get(ctx).monthCounts(type, key) }
        loaded = true
    }
    val byYear = remember(months) { months.groupBy { it.key.take(4) } }
    if (loaded && months.isEmpty()) {
        EmptyState(Icons.Filled.Folder, tr("لا توجد رسائل", "No messages"), tr("لم تُرتَّب رسائل هنا بعد.", "No messages organized here yet."))
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        byYear.forEach { (year, list) ->
            item(key = "y$year") {
                TreeRow(
                    label = if (year == "0000") tr("تاريخ غير معروف", "Unknown date") else year,
                    count = list.sumOf { it.count }, indent = 0.dp, open = expanded == year
                ) { expanded = if (expanded == year) null else year }
            }
            if (expanded == year) {
                items(list, key = { it.key }) { g ->
                    val m = g.key.substringAfter('-', "1").toIntOrNull() ?: 1
                    TreeRow(label = monthName(m), count = g.count, indent = 28.dp, open = null) { onOpenMonth(g.key) }
                }
            }
        }
    }
}

@Composable
private fun TreeRow(label: String, count: Int, indent: Dp, open: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(start = 20.dp + indent, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (open == true) Icons.Filled.FolderOpen else Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            if (open == null) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
            null, tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TreeScreen(type: String, key: String, onBack: () -> Unit, onOpenMonth: (String) -> Unit) {
    val ctx = LocalContext.current
    val catTitle = if (type == "cat") Organizer.byKey(key).title.t() else null
    var label by remember { mutableStateOf(catTitle ?: key.replaceFirstChar { it.uppercase() }) }
    LaunchedEffect(type, key) {
        if (type == "sender" && key.contains('@')) {
            label = withContext(Dispatchers.IO) { MailDb.get(ctx).senderLabel(key) }
        }
    }
    Column(Modifier.fillMaxSize().screenInsets(bottom = true)) {
        ScreenHeader(label, tr("السنوات والأشهر", "Years and months"), onBack)
        YearMonthTree(type, key, onOpenMonth)
    }
}

@Composable
fun MonthMessagesScreen(type: String, key: String, ym: String, onBack: () -> Unit, onOpenMessage: (String, Long) -> Unit) {
    val ctx = LocalContext.current
    var mails by remember { mutableStateOf<List<com.mailsphere.app.data.DemoMail>?>(null) }
    LaunchedEffect(type, key, ym) {
        mails = withContext(Dispatchers.IO) { MailDb.get(ctx).messages(type, key, ym).map { it.toDemoMail() } }
    }
    val m = ym.substringAfter('-', "1").toIntOrNull() ?: 1
    val title = if (ym.startsWith("0000")) tr("تاريخ غير معروف", "Unknown date") else monthName(m) + " " + ym.take(4)
    Column(Modifier.fillMaxSize().screenInsets(bottom = true)) {
        val list = mails
        ScreenHeader(title, if (list == null) null else tr("${list.size} رسالة", "${list.size} messages"), onBack)
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(Icons.Filled.Folder, tr("لا توجد رسائل", "No messages"), tr("لا رسائل في هذا الشهر.", "No messages this month."))
            else -> LazyColumn {
                items(list) { mail ->
                    MailRow(mail, dotOnly = true, onClick = { onOpenMessage(mail.account ?: "", mail.uid) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
}
