package com.mailsphere.app.ui

import android.content.Intent
import android.text.TextUtils
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.mailsphere.app.data.ComposeDraft
import com.mailsphere.app.data.AccountStore
import com.mailsphere.app.data.ImapClient
import com.mailsphere.app.data.MailBody
import com.mailsphere.app.data.OAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MessageScreen(email: String, uid: Long, onBack: () -> Unit, onCompose: () -> Unit) {
    val ctx = LocalContext.current
    var body by remember { mutableStateOf<MailBody?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var loadImages by remember { mutableStateOf(false) }

    LaunchedEffect(retry) {
        loading = true
        error = null
        try {
            val acc = AccountStore.list(ctx).firstOrNull { it.email == email } ?: throw IllegalStateException("Account not found")
            body = withContext(Dispatchers.IO) {
                val token = if (acc.authState != null) OAuth.accessToken(ctx, acc) else null
                ImapClient.fetchBody(acc, token, uid)
            }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().screenInsets(bottom = true)) {
        ScreenHeader(tr("الرسالة", "Message"), null, onBack)
        val b = body
        when {
            loading -> LoadingState()
            error != null -> ErrorState(error ?: "") { retry++ }
            b != null -> {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(b.subject.ifBlank { tr("(بدون عنوان)", "(no subject)") }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(b.from, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(b.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (b.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr("المرفقات (${b.attachments.size}): ", "Attachments (${b.attachments.size}): ") + b.attachments.joinToString("، ") +
                                tr(" — التنزيل في مرحلة لاحقة", " — download comes in a later phase"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val plainText = (b.text ?: HtmlCompat.fromHtml(b.html ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY).toString()).take(20000)
                    val wroteLine = tr("في ${b.date}، كتب ${b.from}:", "On ${b.date}, ${b.from} wrote:")
                    val fwdTitle = tr("رسالة محوّلة", "Forwarded message")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            ComposeDraft.reset()
                            ComposeDraft.from = email
                            ComposeDraft.to = b.replyAddress
                            ComposeDraft.subject = if (b.subject.startsWith("Re:", ignoreCase = true)) b.subject else "Re: " + b.subject
                            ComposeDraft.body = "\n\n" + wroteLine + "\n" + plainText.lines().joinToString("\n") { "> " + it }
                            ComposeDraft.inReplyTo = b.messageId
                            onCompose()
                        }) { Text(tr("رد", "Reply")) }
                        OutlinedButton(onClick = {
                            ComposeDraft.reset()
                            ComposeDraft.from = email
                            ComposeDraft.subject = if (b.subject.startsWith("Fwd:", ignoreCase = true)) b.subject else "Fwd: " + b.subject
                            ComposeDraft.body = "\n\n---------- " + fwdTitle + " ----------\nFrom: " + b.from + "\nDate: " + b.date +
                                "\nSubject: " + b.subject + "\n\n" + plainText
                            onCompose()
                        }) { Text(tr("توجيه", "Forward")) }
                    }
                    if (b.html != null && !loadImages) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tr("الصور والمحتوى الخارجي محجوبان لحمايتك.", "Remote images and content are blocked for your protection."),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { loadImages = true }) { Text(tr("تحميل", "Load")) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                val page = when {
                    b.html != null -> b.html
                    b.text != null -> "<pre style=\"white-space:pre-wrap;font-family:sans-serif\">" + TextUtils.htmlEncode(b.text) + "</pre>"
                    else -> "<p>" + TextUtils.htmlEncode(tr("(لا يوجد نص)", "(no text)")) + "</p>"
                }
                val doc = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                    "<style>body{font-family:sans-serif;margin:12px;overflow-wrap:anywhere}img{max-width:100%;height:auto}</style></head><body>" +
                    page + "</body></html>"
                val key = "$loadImages|${doc.hashCode()}"
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { c ->
                        WebView(c).apply {
                            setBackgroundColor(0xFFFFFFFF.toInt())
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url ?: return true
                                    if (url.scheme == "http" || url.scheme == "https") {
                                        try {
                                            c.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        } catch (e: Exception) {
                                        }
                                    }
                                    return true
                                }
                            }
                        }
                    },
                    update = { w ->
                        if (w.tag != key) {
                            w.tag = key
                            w.settings.blockNetworkLoads = !loadImages
                            w.settings.blockNetworkImage = !loadImages
                            w.loadDataWithBaseURL(null, doc, "text/html", "utf-8", null)
                        }
                    }
                )
            }
        }
    }
}
