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
import com.mailsphere.app.data.DemoData
import com.mailsphere.app.data.FileKind

@Composable
fun VaultScreen() {
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
