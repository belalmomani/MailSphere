package com.mailsphere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mailsphere.app.data.DemoData

@Composable
fun FoldersScreen() {
    Column(Modifier.fillMaxSize().screenInsets()) {
        ScreenHeader(tr("المجلدات والتصنيفات", "Folders & categories"))
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { DemoBanner() } }
            item { SectionLabel(tr("التصنيفات الرئيسية", "Main categories")) }
            items(DemoData.categories) { c ->
                FolderRow(count = c.count, title = c.title.t()) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.color),
                        contentAlignment = Alignment.Center
                    ) { Icon(c.icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
            }
            item { SectionLabel(tr("المجلدات الخاصة بالجهات", "Sender folders")) }
            items(DemoData.senderFolders) { (name, count, color) ->
                FolderRow(count = count, title = name) { Avatar(name.first().toString(), color, 40.dp) }
            }
        }
    }
}

@Composable
private fun FolderRow(count: Int, title: String, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
