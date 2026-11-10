package com.mailsphere.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.sp
import com.mailsphere.app.data.AccountStore
import com.mailsphere.app.data.ComposeDraft
import com.mailsphere.app.data.DemoData
import com.mailsphere.app.data.DemoMail
import com.mailsphere.app.data.Deleter
import com.mailsphere.app.data.ImapClient
import com.mailsphere.app.data.OAuth
import com.mailsphere.app.data.toDemoMail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(onOpenSearch: () -> Unit, onOpenMessage: (String, Long) -> Unit, onCompose: () -> Unit) {
    val ctx = LocalContext.current
    val accounts = remember { AccountStore.list(ctx) }
    val isDemo = accounts.isEmpty()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var real by remember { mutableStateOf<List<DemoMail>>(emptyList()) }
    var partial by remember { mutableStateOf<String?>(null) }
    var follow by remember { mutableStateOf<List<DemoMail>>(emptyList()) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<String>() }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected.clear() }
    val flow = rememberDeleteFlow {
        selected.clear()
        refreshKey++
    }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        partial = null
        if (isDemo) {
            delay(700) // simulated loading for the demo state
        } else {
            val errors = mutableListOf<String>()
            val result = withContext(Dispatchers.IO) {
                accounts.flatMap { acc ->
                    try {
                        val token = if (acc.authState != null) OAuth.accessToken(ctx, acc) else null
                        ImapClient.fetchLatest(acc, token)
                    } catch (e: Exception) {
                        errors.add(acc.email + ": " + (e.message ?: e.javaClass.simpleName))
                        emptyList()
                    }
                }.sortedByDescending { it.dateMillis }.map { it.toDemoMail() }
            }
            real = result
            if (result.isEmpty() && errors.isNotEmpty()) error = errors.first()
            else if (errors.isNotEmpty()) partial = errors.first()
        }
        loading = false
    }

    LaunchedEffect(filter, refreshKey, Deleter.version) {
        if (filter == 3 && !isDemo) {
            follow = withContext(Dispatchers.IO) { com.mailsphere.app.data.MailDb.get(ctx).followUps().map { it.toDemoMail() } }
        }
    }

    val base = if (isDemo) DemoData.mails else if (filter == 3) follow else real
    val shown = base.filter {
        when (filter) {
            1 -> it.important
            2 -> it.unread > 0
            3 -> true
            else -> true
        }
    }
    val inboxLabel = tr("صندوق الوارد", "Inbox")
    val labels = listOf(tr("الكل", "All"), tr("مهم", "Important"), tr("غير مقروءة", "Unread"), tr("المتابعة", "Follow-up"))
    fun keyOf(m: DemoMail) = (m.account ?: "") + "|" + m.uid
    fun toggle(k: String) {
        if (k in selected) selected.remove(k) else selected.add(k)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsets()) {
            if (selecting) {
                SelectionBar(
                    count = selected.size,
                    onCancel = { selected.clear() },
                    onSelectAll = { selected.clear(); selected.addAll(shown.map { keyOf(it) }) },
                    onDelete = {
                        flow.request(
                            selected.map { it.substringBefore('|') to it.substringAfter('|').toLong() },
                            inboxLabel
                        )
                    }
                )
            } else {
                ScreenHeader(tr("صندوق البريد الموحد", "Unified Inbox"), tr("جميع حساباتك في مكان واحد", "All your accounts in one place"))
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (isDemo) DemoBanner() else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("${accounts.size} حساب متصل — آخر 30 رسالة لكل حساب. اضغط مطولًا على رسالة لتحديدها.", "${accounts.size} account(s) connected — latest 30 messages each. Long-press a message to select."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { refreshKey++ }) { Icon(Icons.Filled.Refresh, tr("تحديث", "Refresh")) }
                }
                partial?.let {
                    Text(tr("تعذّر تحديث بعض الحسابات: ", "Some accounts failed: ") + it.take(80), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = onOpenSearch,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("ابحث في الرسائل...", "Search messages..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(labels.size) { i ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(labels[i]) })
                }
            }
            when {
                loading -> LoadingState()
                error != null -> ErrorState(error ?: "") { refreshKey++ }
                shown.isEmpty() -> EmptyState(
                    Icons.Filled.Email,
                    tr("لا توجد رسائل هنا", "No messages here"),
                    tr("لا توجد رسائل مطابقة. لوضع رسالة هنا افتحها واضغط «للمتابعة».", "No matching messages. To add one here, open it and tap “Follow up”.")
                )
                else -> LazyColumn {
                    items(shown) { mail ->
                        val k = keyOf(mail)
                        val canAct = !isDemo && mail.account != null
                        MailRow(
                            mail, dotOnly = !isDemo,
                            onClick = when {
                                !canAct -> null
                                selecting -> ({ toggle(k) })
                                else -> ({ onOpenMessage(mail.account ?: "", mail.uid) })
                            },
                            onLongClick = if (canAct) ({ toggle(k) }) else null,
                            selected = if (selecting) (k in selected) else null
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
        if (!isDemo && !selecting) {
            FloatingActionButton(
                onClick = { ComposeDraft.reset(); onCompose() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Icon(Icons.Filled.Edit, tr("رسالة جديدة", "New message")) }
        }
    }
    flow.Dialogs()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MailRow(
    mail: DemoMail,
    dotOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (selected == true) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) else Modifier)
            .then(
                if (onClick != null || onLongClick != null)
                    Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (selected != null) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
        }
        Avatar(mail.sender.firstOrNull()?.uppercase() ?: "?", mail.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mail.sender, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(mail.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mail.subject.t(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (mail.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (mail.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    if (dotOnly) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    } else {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) { Text(mail.unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (mail.preview.t().isNotBlank()) {
                Text(
                    mail.preview.t(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
