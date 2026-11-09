package com.mailsphere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mailsphere.app.data.AccountStore
import com.mailsphere.app.data.AttRow
import com.mailsphere.app.data.AttachmentScanner
import com.mailsphere.app.data.Deleter
import com.mailsphere.app.data.ImapClient
import com.mailsphere.app.data.MailDb
import com.mailsphere.app.data.OAuth
import com.mailsphere.app.data.blockedExtensions
import com.mailsphere.app.data.formatShortDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import com.mailsphere.app.data.DemoData
import com.mailsphere.app.data.FileKind

@Composable
private fun DemoVault() {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(tr("الكل", "All"), tr("صور", "Images"), "PDF", tr("فيديو", "Video"))
    val files = DemoData.files.filter {
        when (filter) {
            1 -> it.kind == FileKind.IMAGE
            2 -> it.kind == FileKind.PDF
            3 -> it.kind == FileKind.VIDEO
            else -> true
        }
    }
    Column(Modifier.fillMaxSize().screenInsets()) {
        ScreenHeader(tr("خزانة المرفقات", "Attachments vault"), tr("جميع ملفاتك في مكان واحد", "All your files in one place"))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { DemoBanner() }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(labels.size) { i -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(labels[i]) }) }
                }
            }
            if (files.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Filled.AttachFile, tr("لا توجد ملفات", "No files"), tr("لا توجد مرفقات من هذا النوع.", "No attachments of this type."))
                }
            }
            items(files) { f ->
                val (icon, color) = when (f.kind) {
                    FileKind.PDF -> Icons.Filled.PictureAsPdf to Color(0xFFE5393B)
                    FileKind.IMAGE -> Icons.Filled.Image to Color(0xFF3B82F6)
                    FileKind.VIDEO -> Icons.Filled.PlayArrow to Color(0xFF6366F1)
                    FileKind.DOC -> Icons.Filled.Description to Color(0xFF2563EB)
                    FileKind.ZIP -> Icons.Filled.FolderZip to Color(0xFFF59E0B)
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(icon, null, tint = color, modifier = Modifier.size(30.dp)) }
                        Spacer(Modifier.height(10.dp))
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(f.size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun VaultScreen() {
    val ctx = LocalContext.current
    val hasAccounts = remember { AccountStore.list(ctx).isNotEmpty() }
    if (hasAccounts) RealVault() else DemoVault()
}

private fun fmtSize(b: Long): String = when {
    b >= 1_048_576 -> String.format("%.1f MB", b / 1_048_576.0)
    b >= 1024 -> (b / 1024).toString() + " KB"
    else -> "$b B"
}

@Composable
private fun RealVault() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<AttRow>>(emptyList()) }
    var unscanned by remember { mutableIntStateOf(0) }
    var indexed by remember { mutableIntStateOf(0) }
    var totalAtt by remember { mutableIntStateOf(0) }
    var busyName by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val kinds = listOf<String?>(null, "image", "pdf", "doc", "video", "zip", "other")
    val labels = listOf(tr("الكل", "All"), tr("صور", "Images"), "PDF", tr("مستندات", "Documents"), tr("فيديو", "Video"), tr("مضغوطات", "Archives"), tr("أخرى", "Other"))
    val blockedText = tr("لأمانك لا يفتح التطبيق ملفات التثبيت أو التنفيذ (apk وexe وغيرها).", "For your safety the app does not open installer or executable files (apk, exe, ...).")
    val noAppText = tr("لا يوجد تطبيق على هاتفك يفتح هذا النوع من الملفات.", "No app on your phone can open this type of file.")

    LaunchedEffect(filter, query, AttachmentScanner.lastFinished, AttachmentScanner.done / 200, Deleter.version) {
        withContext(Dispatchers.IO) {
            val db = MailDb.get(ctx)
            rows = db.attachments(kinds[filter], query)
            unscanned = db.unscannedCount()
            indexed = db.count()
            totalAtt = db.attachmentTotal()
        }
    }

    fun open(r: AttRow) {
        val ext = r.name.substringAfterLast('.', "").lowercase()
        if (ext in blockedExtensions) {
            message = "blocked"
            return
        }
        busyName = r.name
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val acc = AccountStore.list(ctx).firstOrNull { it.email == r.account } ?: throw IllegalStateException("Account not found")
                    val dir = File(ctx.cacheDir, "attachments").apply { mkdirs() }
                    val cutoff = System.currentTimeMillis() - 24L * 3600 * 1000
                    dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
                    val safe = r.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val f = File(dir, r.uid.toString() + "_" + r.idx + "_" + safe)
                    val token = if (acc.authState != null) OAuth.accessToken(ctx, acc) else null
                    ImapClient.downloadAttachment(acc, token, r.uid, r.idx, f)
                    f
                }
                val uri = FileProvider.getUriForFile(ctx, "com.mailsphere.app.files", file)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                busyName = null
                try {
                    ctx.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    message = "noapp"
                }
            } catch (e: Exception) {
                busyName = null
                message = (e.javaClass.simpleName + ": " + (e.message ?: "")).take(160)
            }
        }
    }

    Column(Modifier.fillMaxSize().screenInsets()) {
        ScreenHeader(tr("خزانة المرفقات", "Attachments vault"), tr("مرفقات رسائلك في مكان واحد", "Your message attachments in one place"))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                AttachmentScanner.running -> Column {
                    val frac = if (AttachmentScanner.total > 0) AttachmentScanner.done.toFloat() / AttachmentScanner.total else 0f
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                    Text(
                        tr("جارٍ فحص المرفقات: ${AttachmentScanner.done} / ${AttachmentScanner.total}", "Scanning attachments: ${AttachmentScanner.done} / ${AttachmentScanner.total}"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { AttachmentScanner.cancel() }) { Text(tr("إيقاف", "Stop")) }
                }
                indexed == 0 -> Text(
                    tr("رتّب بريدك أولًا من «رتّب بريدي بالكامل» حتى يتمكن التطبيق من فحص المرفقات.", "Organize your mail first (“Organize my whole mail”) so the app can scan attachments."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                unscanned > 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("$unscanned رسالة لم تُفحص بعد بحثًا عن مرفقات. الفحص لا يحمّل أي ملف.", "$unscanned messages are not scanned for attachments yet. Scanning downloads no files."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { AttachmentScanner.start(ctx) }) { Text(tr("فحص", "Scan")) }
                }
                else -> Text(
                    tr("$totalAtt مرفق في $indexed رسالة.", "$totalAtt attachments in $indexed messages."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AttachmentScanner.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(tr("ابحث باسم الملف...", "Search by file name...")) }
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(labels.size) { i -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(labels[i]) }) }
            }
        }
        if (rows.isEmpty()) {
            EmptyState(
                Icons.Filled.AttachFile, tr("لا توجد مرفقات", "No attachments"),
                tr("لم يُعثر على مرفقات مطابقة. إن لم تفحص رسائلك بعد فاضغط «فحص».", "No matching attachments found. If you have not scanned your messages yet, tap “Scan”.")
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                items(rows) { r ->
                    val (icon, color) = when (r.kind) {
                        "pdf" -> Icons.Filled.PictureAsPdf to Color(0xFFE5393B)
                        "image" -> Icons.Filled.Image to Color(0xFF3B82F6)
                        "video" -> Icons.Filled.PlayArrow to Color(0xFF6366F1)
                        "doc" -> Icons.Filled.Description to Color(0xFF2563EB)
                        "zip" -> Icons.Filled.FolderZip to Color(0xFFF59E0B)
                        else -> Icons.Filled.AttachFile to Color(0xFF64748B)
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { open(r) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(icon, null, tint = color) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                fmtSize(r.size) + "  •  " + r.sender + "  •  " + formatShortDate(r.date),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }

    busyName?.let { n ->
        AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text(tr("جارٍ تنزيل الملف...", "Downloading file...")) },
            text = { Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Text(n, maxLines = 2) } }
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text(tr("حسنًا", "OK")) } },
            title = { Text(tr("تعذّر فتح الملف", "Could not open the file")) },
            text = { Text(if (m == "blocked") blockedText else if (m == "noapp") noAppText else m) }
        )
    }
}
