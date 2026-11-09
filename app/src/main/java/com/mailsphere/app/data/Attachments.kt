package com.mailsphere.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AttInfo(val name: String, val size: Long, val mime: String, val kind: String)

data class AttRow(
    val account: String, val uid: Long, val idx: Int, val name: String, val size: Long,
    val kind: String, val sender: String, val date: Long
)

fun attachmentKind(name: String, mime: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "svg") -> "image"
        mime == "application/pdf" || ext == "pdf" -> "pdf"
        mime.startsWith("video/") || ext in setOf("mp4", "mov", "avi", "mkv", "3gp", "webm") -> "video"
        ext in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt", "ods", "odp", "pages", "numbers", "key") -> "doc"
        ext in setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2") -> "zip"
        else -> "other"
    }
}

/** Extensions that are never opened from the app (executable / script / installer files). */
val blockedExtensions = setOf("apk", "exe", "bat", "cmd", "com", "scr", "js", "vbs", "jar", "msi", "ps1", "sh", "dmg", "pif")

/**
 * Scans indexed messages for attachments (names, sizes, types) using IMAP BODYSTRUCTURE only: no attachment is downloaded.
 * Resumable: only messages not yet scanned are processed.
 */
object AttachmentScanner {
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var current by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastFinished by mutableLongStateOf(0L)
        private set

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(ctx: Context) {
        if (running) return
        val app = ctx.applicationContext
        running = true
        error = null
        done = 0
        total = 0
        job = scope.launch {
            val me = coroutineContext[Job]
            try {
                val db = MailDb.get(app)
                val accounts = AccountStore.list(app)
                total = accounts.sumOf { db.unscannedCount(it.email) }
                val errors = mutableListOf<String>()
                for (acc in accounts) {
                    current = acc.email
                    var failed = false
                    while (me?.isActive != false && !failed) {
                        val uids = db.unscanned(acc.email, 300)
                        if (uids.isEmpty()) break
                        var ok = false
                        var lastErr: Exception? = null
                        for (attempt in 1..3) {
                            try {
                                val token = if (acc.authState != null) OAuth.accessToken(app, acc) else null
                                ImapClient.scanAttachments(acc, token, uids, db)
                                ok = true
                                break
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                lastErr = e
                                delay(1500)
                            }
                        }
                        if (ok) {
                            done += uids.size
                        } else {
                            val err = lastErr
                            errors.add(acc.email + ": " + (err?.javaClass?.simpleName ?: "error") + ": " + (err?.message ?: ""))
                            failed = true
                        }
                    }
                }
                if (errors.isNotEmpty()) error = errors.first().take(200)
            } catch (e: CancellationException) {
                // stopped by the user: progress is saved
            } finally {
                running = false
                lastFinished = System.currentTimeMillis()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
