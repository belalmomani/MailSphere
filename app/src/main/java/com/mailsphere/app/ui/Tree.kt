package com.mailsphere.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
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
import com.mailsphere.app.data.DemoMail
import com.mailsphere.app.data.Deleter
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

/**
 * Years as folders; tap a year to open it and see its months; tap a month to list its messages.
 * Long-press a year or month to select it (several allowed) and move all its messages to Trash.
 * type "all"/key "_" = everything.
 */
@Composable
fun YearMonthTree(type: String, key: String, scopeLabel: String, onOpenMonth: (String) -> Unit) {
    val ctx = LocalContext.current
    var months by remember { mutableStateOf<List<Group>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<String>() } // "y2026" or "m2026-03"
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected.clear() }
    val flow = rememberDeleteFlow { selected.clear() }
    LaunchedEffect(type, key, SyncManager.lastFinished, Deleter.version) {
        months = withContext(Dispatchers.IO) { MailDb.get(ctx).monthCounts(type, key) }
        loaded = true
    }
    val byYear = remember(months) { months.groupBy { it.key.take(4) } }
    fun toggle(k: String) {
        if (k in selected) selected.remove(k) else selected.add(k)
    }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onCancel = { selected.clear() },
                onSelectAll = {
                    selected.clear()
                    byYear.keys.forEach { selected.add("y$it") }
                },
                onDelete = {
                    flow.requestFolders(selected.map { Triple(type, key, it.substring(1)) }, scopeLabel)
                }
            )
        }
        if (loaded && months.isEmpty()) {
            EmptyState(Icons.Filled.Folder, tr("لا توجد رسائل", "No messages"), tr("لم تُرتَّب رسائل هنا بعد.", "No messages organized here yet."))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            byYear.forEach { (year, list) ->
                item(key = "y$year") {
                    TreeRow(
                        label = if (year == "0000") tr("تاريخ غير معروف", "Unknown date") else year,
                        count = list.sumOf { it.count }, indent = 0.dp, open = expanded == year,
                        selected = if (selecting) ("y$year" in selected) else null,
                        onLongClick = { toggle("y$year") }
                    ) { if (selecting) toggle("y$year") else expanded = if (expanded == year) null else year }
                }
                if (expanded == year) {
                    items(list, key = { it.key }) { g ->
                        val m = g.key.substringAfter('-', "1").toIntOrNull() ?: 1
                        TreeRow(
                            label = monthName(m), count = g.count, indent = 28.dp, open = null,
                            selected = if (selecting) ("m" + g.key in selected) else null,
                            onLongClick = { toggle("m" + g.key) }
                        ) { if (selecting) toggle("m" + g.key) else onOpenMonth(g.key) }
                    }
                }
            }
        }
    }
    flow.Dialogs()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    label: String, count: Int, indent: Dp, open: Boolean?,
    selected: Boolean? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (selected == true) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp + indent, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected != null) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
        }
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
        ScreenHeader(label, tr("السنوات والأشهر — اضغط مطولًا لتحديد سنة أو شهر", "Years and months — long-press to select a year or month"), onBack)
        YearMonthTree(type, key, label, onOpenMonth)
    }
}

@Composable
fun MonthMessagesScreen(type: String, key: String, ym: String, onBack: () -> Unit, onOpenMessage: (String, Long) -> Unit) {
    val ctx = LocalContext.current
    var mails by remember { mutableStateOf<List<DemoMail>?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected.clear() }
    val flow = rememberDeleteFlow { selected.clear() }
    LaunchedEffect(type, key, ym, Deleter.version) {
        mails = withContext(Dispatchers.IO) { MailDb.get(ctx).messages(type, key, ym).map { it.toDemoMail() } }
    }
    val m = ym.substringAfter('-', "1").toIntOrNull() ?: 1
    val title = if (ym.startsWith("0000")) tr("تاريخ غير معروف", "Unknown date") else monthName(m) + " " + ym.take(4)
    fun keyOf(x: DemoMail) = (x.account ?: "") + "|" + x.uid
    fun toggle(k: String) {
        if (k in selected) selected.remove(k) else selected.add(k)
    }
    Column(Modifier.fillMaxSize().screenInsets(bottom = true)) {
        val list = mails
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onCancel = { selected.clear() },
                onSelectAll = { selected.clear(); selected.addAll((list ?: emptyList()).map { keyOf(it) }) },
                onDelete = {
                    flow.request(selected.map { it.substringBefore('|') to it.substringAfter('|').toLong() }, title)
                }
            )
        } else {
            ScreenHeader(
                title,
                if (list == null) null else tr("${list.size} رسالة — اضغط مطولًا للتحديد", "${list.size} messages — long-press to select"),
                onBack
            )
        }
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(Icons.Filled.Folder, tr("لا توجد رسائل", "No messages"), tr("لا رسائل في هذا الشهر.", "No messages this month."))
            else -> LazyColumn {
                items(list) { mail ->
                    val k = keyOf(mail)
                    MailRow(
                        mail, dotOnly = true,
                        onClick = if (selecting) ({ toggle(k) }) else ({ onOpenMessage(mail.account ?: "", mail.uid) }),
                        onLongClick = { toggle(k) },
                        selected = if (selecting) (k in selected) else null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
    flow.Dialogs()
}
