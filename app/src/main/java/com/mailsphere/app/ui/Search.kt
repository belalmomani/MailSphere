package com.mailsphere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mailsphere.app.data.AccountStore
import com.mailsphere.app.data.Deleter
import com.mailsphere.app.data.MailDb
import com.mailsphere.app.data.Organizer
import com.mailsphere.app.data.toDemoMail
import androidx.compose.foundation.layout.imePadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import com.mailsphere.app.data.DemoMail
import com.mailsphere.app.data.Bi
import com.mailsphere.app.data.DemoData

@Composable
private fun DemoSearch(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable { mutableStateOf<String?>(null) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val selectedTiles = remember { mutableStateListOf<Int>() }

    val q = query.trim()
    val results = DemoData.mails.filter { m ->
        (sender == null || m.sender == sender) &&
            (q.isEmpty() || listOf(m.sender, m.subject.ar, m.subject.en, m.preview.ar, m.preview.en).any { it.contains(q, ignoreCase = true) })
    }
    val tiles: List<Pair<ImageVector, Bi>> = listOf(
        Icons.Filled.Payments to Bi("المبالغ المالية", "Amounts"),
        Icons.Filled.CalendarMonth to Bi("التواريخ والمواعيد", "Dates & events"),
        Icons.Filled.Tag to Bi("الأرقام والطلبات", "Numbers & orders"),
        Icons.Filled.Link to Bi("الروابط", "Links"),
        Icons.Filled.Person to Bi("المرسلون", "Senders"),
        Icons.Filled.Phone to Bi("أرقام الهواتف", "Phone numbers")
    )

    Column(Modifier.fillMaxSize().screenInsets(bottom = true)) {
        ScreenHeader(tr("ابحث عن معلومة", "Find information"), tr("ابحث في كل رسائلك بسهولة", "Search all your messages easily"), onBack)
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DemoBanner() }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it; submitted = false },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(tr("مثال: فواتير، ملفات PDF، مبالغ...", "e.g. invoices, PDF files, amounts...")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitted = true })
                )
            }
            tiles.chunked(2).forEachIndexed { row, pair ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { col, (icon, label) ->
                            val idx = row * 2 + col
                            val on = idx in selectedTiles
                            Surface(
                                onClick = { if (on) selectedTiles.remove(idx) else selectedTiles.add(idx) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp
                            ) {
                                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(6.dp))
                                    Text(label.t(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            item { Text(tr("بحث متقدم — من", "Advanced search — from"), fontWeight = FontWeight.Bold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = sender == null, onClick = { sender = null; submitted = false }, label = { Text(tr("الكل", "All")) }) }
                    items(DemoData.mails.map { it.sender }) { s ->
                        FilterChip(selected = sender == s, onClick = { sender = s; submitted = false }, label = { Text(s) })
                    }
                }
            }
            item {
                Button(onClick = { submitted = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(tr("بحث", "Search"), fontWeight = FontWeight.Bold)
                }
            }
            if (submitted) {
                if (results.isEmpty()) {
                    item {
                        EmptyState(Icons.Filled.Search, tr("لا توجد نتائج", "No results"), tr("جرّب كلمات أخرى أو غيّر الجهة المرسلة.", "Try other words or change the sender."))
                    }
                } else {
                    item { Text(tr("النتائج (${results.size}) — بيانات تجريبية", "Results (${results.size}) — demo data"), fontWeight = FontWeight.Bold) }
                    items(results) { MailRow(it) }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenMessage: (String, Long) -> Unit) {
    val ctx = LocalContext.current
    val hasAccounts = remember { AccountStore.list(ctx).isNotEmpty() }
    if (hasAccounts) RealSearch(onBack, onOpenMessage) else DemoSearch(onBack)
}

@Composable
private fun RealSearch(onBack: () -> Unit, onOpenMessage: (String, Long) -> Unit) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableIntStateOf(0) }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var hasAtt by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<DemoMail>?>(null) }
    var indexed by remember { mutableIntStateOf(-1) }
    val periods = listOf(tr("أي وقت", "Any time"), tr("30 يومًا", "30 days"), tr("6 أشهر", "6 months"), tr("سنة", "1 year"))

    LaunchedEffect(query, period, cat, unreadOnly, hasAtt, Deleter.version) {
        delay(250)
        val day = 24L * 3600 * 1000
        val since = when (period) {
            1 -> System.currentTimeMillis() - 30 * day
            2 -> System.currentTimeMillis() - 182 * day
            3 -> System.currentTimeMillis() - 365 * day
            else -> 0L
        }
        withContext(Dispatchers.IO) {
            val db = MailDb.get(ctx)
            indexed = db.count()
            results = db.search(query, cat, unreadOnly, since, hasAtt).map { it.toDemoMail() }
        }
    }

    Column(Modifier.fillMaxSize().screenInsets(bottom = true).imePadding()) {
        ScreenHeader(tr("بحث متقدم", "Advanced search"), tr("في المرسل والموضوع والتاريخ", "In sender, subject and date"), onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(tr("مثال: github فاتورة 2025", "e.g. github invoice 2025")) }
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(periods.size) { i -> FilterChip(selected = period == i, onClick = { period = i }, label = { Text(periods[i]) }) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text(tr("كل التصنيفات", "All categories")) }) }
                items(Organizer.categories + Organizer.other) { c ->
                    FilterChip(selected = cat == c.key, onClick = { cat = c.key }, label = { Text(c.title.t()) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = unreadOnly, onClick = { unreadOnly = !unreadOnly }, label = { Text(tr("غير مقروءة", "Unread")) })
                FilterChip(selected = hasAtt, onClick = { hasAtt = !hasAtt }, label = { Text(tr("بمرفقات", "With attachments")) })
            }
            Text(
                tr("البحث في الرسائل المرتّبة على جهازك (العنوان والمرسل والتاريخ) وليس في نص الرسالة. «بمرفقات» يتطلب فحص المرفقات من الخزانة.",
                    "Searches the messages organized on your device (subject, sender, date), not the message text. “With attachments” needs the attachment scan from the vault."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val list = results
        when {
            indexed == 0 -> EmptyState(Icons.Filled.Search, tr("لم يُرتَّب بريدك بعد", "Your mail is not organized yet"), tr("افتح «رتّب بريدي بالكامل» أولًا ليُبنى فهرس البحث.", "Open “Organize my whole mail” first to build the search index."))
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(Icons.Filled.Search, tr("لا توجد نتائج", "No results"), tr("جرّب كلمات أخرى أو خفّف المرشحات.", "Try other words or loosen the filters."))
            else -> {
                Text(tr("${list.size} نتيجة", "${list.size} results"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyColumn {
                    items(list) { mail ->
                        MailRow(mail, dotOnly = true, onClick = { onOpenMessage(mail.account ?: "", mail.uid) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
