package com.mailsphere.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mailsphere.app.data.Deleter
import com.mailsphere.app.data.MailDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SelectionBar(count: Int, onCancel: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, tr("إلغاء التحديد", "Cancel selection")) }
        Text(tr("$count محدد", "$count selected"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onSelectAll) { Text(tr("تحديد الكل", "Select all")) }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(Icons.Filled.Delete, tr("حذف", "Delete"), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** Holds the confirm -> progress -> error dialogs for moving messages to Trash. Call Dialogs() once in the screen. */
class DeleteFlow(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onFinished: (String?) -> Unit
) {
    var pending by mutableStateOf<List<Pair<String, Long>>?>(null)
    var what by mutableStateOf("")
    var error by mutableStateOf<String?>(null)

    fun request(targets: List<Pair<String, Long>>, what: String) {
        if (targets.isEmpty()) return
        this.what = what
        pending = targets.distinct()
    }

    /** specs: (type, key, period) folders; looks up their messages in the local index first. */
    fun requestFolders(specs: List<Triple<String, String, String?>>, what: String) {
        scope.launch {
            val t = withContext(Dispatchers.IO) {
                val db = MailDb.get(ctx)
                specs.flatMap { db.targets(it.first, it.second, it.third) }.distinct()
            }
            request(t, what)
        }
    }

    private fun confirm() {
        val t = pending ?: return
        pending = null
        scope.launch {
            val e = Deleter.delete(ctx, t)
            error = e
            onFinished(e)
        }
    }

    @Composable
    fun Dialogs() {
        pending?.let { list ->
            val count = list.size
            val need = count > 20
            val word = tr("حذف", "DELETE")
            var typed by remember(list) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text(tr("نقل $count رسالة إلى سلة المهملات؟", "Move $count messages to Trash?")) },
                text = {
                    Column {
                        Text(
                            tr("من: $what\nستُنقل الرسائل من صندوق الوارد في بريدك الفعلي إلى سلة المهملات، ويمكن عادةً استعادتها من هناك إلى أن تفرغها خدمة بريدك. لا يُحذف شيء نهائيًا من التطبيق.",
                                "From: $what\nThe messages will be moved from your real inbox to the Trash folder, where you can usually restore them until your mail provider empties it. The app never deletes permanently.")
                        )
                        if (need) {
                            Spacer(Modifier.height(12.dp))
                            Text(tr("للتأكيد اكتب: $word", "To confirm type: $word"), fontWeight = FontWeight.Bold)
                            OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { confirm() }, enabled = !need || typed.trim().equals(word, ignoreCase = true)) {
                        Text(tr("نقل إلى السلة", "Move to Trash"), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { pending = null }) { Text(tr("إلغاء", "Cancel")) } }
            )
        }
        if (Deleter.running) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(tr("جارٍ النقل إلى السلة...", "Moving to Trash...")) },
                text = {
                    Column {
                        val frac = if (Deleter.total > 0) Deleter.done.toFloat() / Deleter.total else 0f
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${Deleter.done} / ${Deleter.total}")
                    }
                }
            )
        }
        error?.let { e ->
            AlertDialog(
                onDismissRequest = { error = null },
                confirmButton = { TextButton(onClick = { error = null }) { Text(tr("حسنًا", "OK")) } },
                title = { Text(tr("تعذّر إكمال الحذف", "Could not finish")) },
                text = { Text(e + "\n" + tr("الرسائل التي لم تُنقل ما زالت في بريدك.", "Messages that were not moved are still in your mailbox.")) }
            )
        }
    }
}

@Composable
fun rememberDeleteFlow(onFinished: (String?) -> Unit): DeleteFlow {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cb = rememberUpdatedState(onFinished)
    return remember { DeleteFlow(ctx, scope) { cb.value(it) } }
}
