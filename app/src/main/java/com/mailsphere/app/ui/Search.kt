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
import com.mailsphere.app.data.Bi
import com.mailsphere.app.data.DemoData

@Composable
fun SearchScreen(onBack: () -> Unit) {
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
