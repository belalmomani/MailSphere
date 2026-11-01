package com.mailsphere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mailsphere.app.data.AccountStore
import com.mailsphere.app.data.DemoData
import com.mailsphere.app.data.Group
import com.mailsphere.app.data.MailDb
import com.mailsphere.app.data.Organizer
import com.mailsphere.app.data.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FoldersScreen(
    onOpenTree: (String, String) -> Unit,
    onOpenMonth: (String, String, String) -> Unit,
    onOpenOrganize: () -> Unit
) {
    val ctx = LocalContext.current
    val isDemo = remember { AccountStore.list(ctx).isEmpty() }
    if (isDemo) DemoFolders() else RealFolders(onOpenTree, onOpenMonth, onOpenOrganize)
}

@Composable
private fun RealFolders(
    onOpenTree: (String, String) -> Unit,
    onOpenMonth: (String, String, String) -> Unit,
    onOpenOrganize: () -> Unit
) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var senders by remember { mutableStateOf<List<Group>>(emptyList()) }
    var cats by remember { mutableStateOf<List<Group>>(emptyList()) }
    var total by remember { mutableIntStateOf(-1) }
    LaunchedEffect(SyncManager.lastFinished, SyncManager.done / 1500) {
        withContext(Dispatchers.IO) {
            val db = MailDb.get(ctx)
            senders = db.topGroups("sender")
            cats = db.topGroups("cat")
            total = db.count()
        }
    }
    val tabs = listOf(tr("الجهات", "Senders"), tr("التصنيفات", "Categories"), tr("التواريخ", "Dates"))

    Column(Modifier.fillMaxSize().screenInsets()) {
        ScreenHeader(tr("المجلدات والتصنيفات", "Folders & categories"))
        if (SyncManager.running) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val frac = if (SyncManager.total > 0) SyncManager.done.toFloat() / SyncManager.total else 0f
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                Text(
                    tr("جارٍ ترتيب بريدك: ${SyncManager.done} / ${SyncManager.total}", "Organizing your mail: ${SyncManager.done} / ${SyncManager.total}"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (total > 0) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("$total رسالة مرتّبة. التصنيف بكلمات مفتاحية وهو تقريبي.", "$total messages organized. Categories use keywords and are approximate."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { SyncManager.start(ctx) }) { Icon(Icons.Filled.Refresh, tr("تحديث", "Update")) }
            }
        }
        if (total == 0 && !SyncManager.running) {
            EmptyState(Icons.Filled.Folder, tr("لم يُرتَّب بريدك بعد", "Your mail is not organized yet"), tr("اضغط الزر لبدء ترتيب كل رسائلك.", "Tap the button to organize all your messages."))
            Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                Button(onClick = onOpenOrganize, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(tr("رتّب بريدي بالكامل", "Organize my whole mail"))
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs.size) { i -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(tabs[i]) }) }
            }
            when (tab) {
                0 -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(senders) { g ->
                        val label = if (g.key.contains('@')) g.label.ifBlank { g.key } else g.key.replaceFirstChar { it.uppercase() }
                        FolderRow(g.count, label, { onOpenTree("sender", g.key) }) {
                            Avatar(label.firstOrNull()?.uppercase() ?: "?", Color(0xFF3B82F6), 40.dp)
                        }
                    }
                }
                1 -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(cats) { g ->
                        val c = Organizer.byKey(g.key)
                        FolderRow(g.count, c.title.t(), { onOpenTree("cat", g.key) }) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.color), contentAlignment = Alignment.Center) {
                                Icon(c.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                else -> YearMonthTree("all", "_") { ym -> onOpenMonth("all", "_", ym) }
            }
        }
    }
}

@Composable
private fun DemoFolders() {
    Column(Modifier.fillMaxSize().screenInsets()) {
        ScreenHeader(tr("المجلدات والتصنيفات", "Folders & categories"))
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { DemoBanner() } }
            item { SectionLabel(tr("التصنيفات الرئيسية", "Main categories")) }
            items(DemoData.categories) { c ->
                FolderRow(c.count, c.title.t(), null) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.color),
                        contentAlignment = Alignment.Center
                    ) { Icon(c.icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
            }
            item { SectionLabel(tr("المجلدات الخاصة بالجهات", "Sender folders")) }
            items(DemoData.senderFolders) { (name, count, color) ->
                FolderRow(count, name, null) { Avatar(name.first().toString(), color, 40.dp) }
            }
        }
    }
}

@Composable
internal fun FolderRow(count: Int, title: String, onClick: (() -> Unit)?, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
