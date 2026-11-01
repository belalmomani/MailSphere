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
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.mailsphere.app.data.MailRepo
import com.mailsphere.app.data.Organizer

@Composable
fun FoldersScreen(onOpenList: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val isDemo = remember { AccountStore.list(ctx).isEmpty() }
    if (isDemo) DemoFolders() else RealFolders(onOpenList)
}

@Composable
private fun RealFolders(onOpenList: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(MailRepo.items.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshKey) {
        if (MailRepo.items.isEmpty() || refreshKey > 0) {
            loading = true
            error = MailRepo.loadAll(ctx)
        }
        loading = false
    }
    val items = MailRepo.items
    val senders = remember(items) { Organizer.senderGroups(items) }
    val cats = remember(items) { Organizer.categoryGroups(items) }
    val months = remember(items) { Organizer.monthGroups(items) }
    val tabs = listOf(tr("الجهات", "Senders"), tr("التصنيفات", "Categories"), tr("التواريخ", "Dates"))

    Column(Modifier.fillMaxSize().screenInsets()) {
        ScreenHeader(tr("المجلدات والتصنيفات", "Folders & categories"))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("مجلدات افتراضية داخل التطبيق من آخر 200 رسالة لكل حساب (يمكنك توسيعها من «رتّب بريدي»). التصنيف بقواعد كلمات بسيطة وتقريبي.",
                    "Virtual folders built in the app from the latest 200 messages per account (extend it from “Organize my mail”). Categories use simple keyword rules and are approximate."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Filled.Refresh, tr("تحديث", "Refresh")) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs.size) { i -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(tabs[i]) }) }
        }
        when {
            loading -> LoadingState()
            error != null -> ErrorState(error ?: "") { refreshKey++ }
            items.isEmpty() -> EmptyState(Icons.Filled.Folder, tr("لا توجد رسائل", "No messages"), tr("لم يُعثر على رسائل في الحسابات المتصلة.", "No messages were found in the connected accounts."))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                when (tab) {
                    0 -> items(senders) { g ->
                        FolderRow(g.count, g.label, { onOpenList("sender", g.key) }) { Avatar(g.label.firstOrNull()?.uppercase() ?: "?", Color(0xFF3B82F6), 40.dp) }
                    }
                    1 -> items(cats) { g ->
                        val c = Organizer.byKey(g.key)
                        FolderRow(g.count, c.title.t(), { onOpenList("cat", g.key) }) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.color), contentAlignment = Alignment.Center) {
                                Icon(c.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    else -> {
                        val byYear = months.groupBy { it.key.take(4) }
                        byYear.forEach { (year, list) ->
                            item { SectionLabel(year + "  (" + list.sumOf { it.count } + ")") }
                            items(list) { g ->
                                FolderRow(g.count, g.key, { onOpenList("month", g.key) }) {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
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
private fun FolderRow(count: Int, title: String, onClick: (() -> Unit)?, leading: @Composable () -> Unit) {
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
