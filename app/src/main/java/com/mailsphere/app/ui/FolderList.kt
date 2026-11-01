package com.mailsphere.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mailsphere.app.data.MailRepo
import com.mailsphere.app.data.Organizer
import com.mailsphere.app.data.toDemoMail

@Composable
fun FolderListScreen(type: String, key: String, onBack: () -> Unit, onOpenMessage: (String, Long) -> Unit) {
    val all = MailRepo.items
    val matched = remember(all, type, key) { Organizer.filter(all, type, key) }
    val shown = remember(matched) { matched.map { it.toDemoMail() } }
    val title = when (type) {
        "sender" -> Organizer.senderLabel(key, matched)
        "cat" -> Organizer.byKey(key).title.t()
        else -> key
    }
    Column(Modifier.fillMaxSize().screenInsets(bottom = true)) {
        ScreenHeader(title, tr("${shown.size} رسالة", "${shown.size} messages"), onBack)
        if (shown.isEmpty()) {
            EmptyState(Icons.Filled.Folder, tr("لا توجد رسائل", "No messages"), tr("افتح تبويب المجلدات لتحميل الرسائل أولًا.", "Open the Folders tab to load messages first."))
        } else {
            LazyColumn {
                items(shown) { mail ->
                    MailRow(mail, dotOnly = true, onClick = { onOpenMessage(mail.account ?: "", mail.uid) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
}
