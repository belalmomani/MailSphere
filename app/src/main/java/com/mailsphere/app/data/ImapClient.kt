package com.mailsphere.app.data

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import com.sun.mail.imap.IMAPFolder
import java.io.File
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility
import javax.mail.Store
import javax.mail.internet.InternetAddress

data class MailItem(
    val sender: String, val subject: String, val dateMillis: Long, val unread: Boolean, val time: String,
    val uid: Long = 0, val account: String = "", val senderAddress: String = ""
)

data class MailBody(
    val subject: String, val from: String, val date: String,
    val html: String?, val text: String?, val attachments: List<String>,
    val replyAddress: String = "", val messageId: String? = null
)

private val palette = listOf(0xFF3B82F6, 0xFF10A37F, 0xFFF59E0B, 0xFFEF4444, 0xFF6366F1, 0xFF0EA5E9, 0xFFF97316, 0xFF64748B)

fun MailItem.toDemoMail() = DemoMail(
    sender = sender,
    subject = Bi(subject, subject),
    preview = Bi("", ""),
    time = time,
    unread = if (unread) 1 else 0,
    color = Color(palette[sender.hashCode().mod(palette.size)]),
    account = account,
    uid = uid
)

/** Blocking IMAP-over-SSL client. Call from a background dispatcher only. Never logs credentials. */
object ImapClient {
    private fun connect(acc: Account, token: String? = null): Store {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "20000")
            if (token != null) {
                put("mail.imaps.auth.mechanisms", "XOAUTH2")
                put("mail.imaps.auth.xoauth2.disable", "false")
                put("mail.imaps.auth.plain.disable", "true")
                put("mail.imaps.auth.login.disable", "true")
            }
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect(acc.host, acc.port, acc.email, token ?: acc.password)
        return store
    }

    fun testLogin(acc: Account) {
        val store = connect(acc)
        store.close()
    }

    fun fetchLatest(acc: Account, token: String? = null, count: Int = 30): List<MailItem> {
        val store = connect(acc, token)
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val total = inbox.messageCount
                if (total == 0) return emptyList()
                val msgs = inbox.getMessages(maxOf(1, total - count + 1), total)
                val fp = FetchProfile()
                fp.add(FetchProfile.Item.ENVELOPE)
                fp.add(FetchProfile.Item.FLAGS)
                fp.add(UIDFolder.FetchProfileItem.UID)
                val uidFolder = inbox as UIDFolder
                inbox.fetch(msgs, fp)
                return msgs.reversed().map { m ->
                    val from = m.from?.firstOrNull() as? InternetAddress
                    val date: Date? = m.sentDate ?: m.receivedDate
                    MailItem(
                        sender = from?.personal?.takeIf { it.isNotBlank() } ?: from?.address ?: "?",
                        subject = m.subject ?: "",
                        dateMillis = date?.time ?: 0L,
                        unread = !m.isSet(Flags.Flag.SEEN),
                        time = formatTime(date),
                        uid = uidFolder.getUID(m),
                        account = acc.email,
                        senderAddress = from?.address ?: ""
                    )
                }
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
    }

    private fun display(a: InternetAddress?): String {
        if (a == null) return "?"
        val p = a.personal
        return if (!p.isNullOrBlank()) "$p <${a.address}>" else a.address
    }

    private fun readText(part: Part): String {
        val c = part.content
        return when (c) {
            is String -> c
            is java.io.InputStream -> c.reader().readText()
            else -> ""
        }
    }

    private fun collect(part: Part, html: StringBuilder, text: StringBuilder, att: MutableList<String>) {
        when {
            part.isMimeType("multipart/*") -> {
                val mp = part.content as Multipart
                for (i in 0 until mp.count) collect(mp.getBodyPart(i), html, text, att)
            }
            Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) ->
                att.add(part.fileName?.let { MimeUtility.decodeText(it) } ?: "attachment")
            part.isMimeType("text/html") -> html.append(readText(part))
            part.isMimeType("text/plain") -> text.append(readText(part))
            part.fileName != null -> att.add(MimeUtility.decodeText(part.fileName))
        }
    }

    /** Fetches one message by IMAP UID (read-only: does not mark it as read). */
    fun fetchBody(acc: Account, token: String?, uid: Long): MailBody {
        val store = connect(acc, token)
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val m = (inbox as UIDFolder).getMessageByUID(uid) ?: throw IllegalStateException("Message not found")
                val html = StringBuilder()
                val text = StringBuilder()
                val att = mutableListOf<String>()
                collect(m, html, text, att)
                val date: Date? = m.sentDate ?: m.receivedDate
                return MailBody(
                    subject = m.subject ?: "",
                    from = display(m.from?.firstOrNull() as? InternetAddress),
                    date = if (date == null) "" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(date),
                    html = html.toString().takeIf { it.isNotBlank() }?.take(600_000),
                    text = text.toString().takeIf { it.isNotBlank() }?.take(600_000),
                    attachments = att,
                    replyAddress = (m.replyTo?.firstOrNull() as? InternetAddress)?.address
                        ?: (m.from?.firstOrNull() as? InternetAddress)?.address ?: "",
                    messageId = (m as? MimeMessage)?.messageID
                )
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
    }

    /** Indexes every INBOX message with UID greater than the last stored one (oldest first, chunks of 300). */
    fun syncAccount(
        acc: Account, token: String?, db: MailDb,
        alive: () -> Boolean, onTotal: (Int) -> Unit, onProgress: (Int) -> Unit
    ) {
        val store = connect(acc, token)
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val uf = inbox as UIDFolder
                val maxUid = db.maxUid(acc.email)
                val msgs = uf.getMessagesByUID(maxUid + 1, UIDFolder.LASTUID)
                onTotal(msgs.size)
                for (chunk in msgs.toList().chunked(300)) {
                    if (!alive()) return
                    val arr = chunk.toTypedArray()
                    val fp = FetchProfile()
                    fp.add(FetchProfile.Item.ENVELOPE)
                    fp.add(FetchProfile.Item.FLAGS)
                    fp.add(UIDFolder.FetchProfileItem.UID)
                    inbox.fetch(arr, fp)
                    val items = arr.map { m ->
                        val from = m.from?.firstOrNull() as? InternetAddress
                        val date: Date? = m.sentDate ?: m.receivedDate
                        MailItem(
                            sender = from?.personal?.takeIf { it.isNotBlank() } ?: from?.address ?: "?",
                            subject = m.subject ?: "",
                            dateMillis = date?.time ?: 0L,
                            unread = !m.isSet(Flags.Flag.SEEN),
                            time = "",
                            uid = uf.getUID(m),
                            account = acc.email,
                            senderAddress = from?.address ?: ""
                        )
                    }
                    db.insert(acc.email, items)
                    onProgress(arr.size)
                }
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
    }

    private fun findTrash(store: Store): Folder? {
        val all = store.defaultFolder.list("*")
        all.firstOrNull { f -> (f as? IMAPFolder)?.attributes?.any { it.equals("\\Trash", ignoreCase = true) } == true }?.let { return it }
        val names = listOf("[Gmail]/Trash", "[Google Mail]/Trash", "[Gmail]/Bin", "Trash", "Deleted Items", "Deleted Messages", "Bin", "INBOX.Trash")
        for (n in names) all.firstOrNull { it.fullName.equals(n, ignoreCase = true) }?.let { return it }
        return null
    }

    /**
     * Moves ONE chunk of messages (by UID) from INBOX to the provider's Trash using a fresh connection, then re-opens the
     * INBOX and VERIFIES which UIDs are really gone. Returns the UIDs confirmed gone (including ones that no longer existed).
     * Never deletes permanently; if no Trash folder is found nothing is touched.
     */
    fun moveChunk(acc: Account, token: String?, chunk: List<Long>): List<Long> {
        val store = connect(acc, token)
        try {
            val trash = findTrash(store) ?: throw IllegalStateException("Trash folder not found - nothing was deleted")
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            try {
                val uf = inbox as UIDFolder
                val msgs = uf.getMessagesByUID(chunk.toLongArray()).filterNotNull().toTypedArray()
                if (msgs.isNotEmpty()) {
                    inbox.copyMessages(msgs, trash)
                    inbox.setFlags(msgs, Flags(Flags.Flag.DELETED), true)
                    try {
                        (inbox as IMAPFolder).expunge(msgs)
                    } catch (e: Exception) {
                        inbox.expunge()
                    }
                }
            } finally {
                inbox.close(false)
            }
            // Verify with a brand-new folder object (no cached state).
            val check = store.getFolder("INBOX")
            check.open(Folder.READ_ONLY)
            try {
                val cf = check as UIDFolder
                val still = cf.getMessagesByUID(chunk.toLongArray()).filterNotNull().map { cf.getUID(it) }.toSet()
                return chunk.filter { it !in still }
            } finally {
                check.close(false)
            }
        } finally {
            store.close()
        }
    }

    // ---------- attachments ----------

    private fun isAttachmentPart(p: Part): Boolean {
        val d = p.disposition
        return Part.ATTACHMENT.equals(d, ignoreCase = true) || (p.fileName != null && !Part.INLINE.equals(d, ignoreCase = true))
    }

    private fun walkAttachments(p: Part, out: MutableList<AttInfo>) {
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as Multipart
            for (i in 0 until mp.count) walkAttachments(mp.getBodyPart(i), out)
        } else if (isAttachmentPart(p)) {
            val raw = p.fileName
            val name = if (raw == null) "attachment-" + (out.size + 1) else try { MimeUtility.decodeText(raw) } catch (e: Exception) { raw }
            val mime = (p.contentType ?: "").substringBefore(';').trim().lowercase()
            out.add(AttInfo(name, maxOf(p.size, 0).toLong(), mime, attachmentKind(name, mime)))
        }
    }

    private fun findAttachment(p: Part, target: Int, counter: IntArray): Part? {
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as Multipart
            for (i in 0 until mp.count) {
                findAttachment(mp.getBodyPart(i), target, counter)?.let { return it }
            }
            return null
        }
        if (isAttachmentPart(p)) {
            if (counter[0] == target) return p
            counter[0]++
        }
        return null
    }

    /** Reads only the MIME structure of the given messages (no attachment data) and stores the attachment list. */
    fun scanAttachments(acc: Account, token: String?, uids: List<Long>, db: MailDb) {
        val store = connect(acc, token)
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val uf = inbox as UIDFolder
                val present = uf.getMessagesByUID(uids.toLongArray()).filterNotNull().toTypedArray()
                val fp = FetchProfile()
                fp.add(FetchProfile.Item.CONTENT_INFO)
                fp.add(UIDFolder.FetchProfileItem.UID)
                inbox.fetch(present, fp)
                val found = HashSet<Long>()
                for (m in present) {
                    val uid = uf.getUID(m)
                    found.add(uid)
                    val list = mutableListOf<AttInfo>()
                    try {
                        walkAttachments(m, list)
                    } catch (e: Exception) {
                        // unreadable structure: treat as "no attachments" so the scan always progresses
                    }
                    db.saveAttachments(acc.email, uid, list)
                }
                for (u in uids) if (u !in found) db.saveAttachments(acc.email, u, emptyList())
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
    }

    /** Downloads ONE attachment (idx-th attachment of the message) into [dest]. */
    fun downloadAttachment(acc: Account, token: String?, uid: Long, idx: Int, dest: File) {
        val store = connect(acc, token)
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val m = (inbox as UIDFolder).getMessageByUID(uid) ?: throw IllegalStateException("Message not found")
                val part = findAttachment(m, idx, intArrayOf(0)) ?: throw IllegalStateException("Attachment not found")
                part.inputStream.use { ins -> dest.outputStream().use { out -> ins.copyTo(out) } }
            } finally {
                inbox.close(false)
            }
        } finally {
            store.close()
        }
    }

    private fun formatTime(d: Date?): String {
        if (d == null) return ""
        val a = Calendar.getInstance().apply { time = d }
        val b = Calendar.getInstance()
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return SimpleDateFormat(if (sameDay) "HH:mm" else "dd/MM", Locale.US).format(d)
    }
}

fun formatShortDate(millis: Long): String {
    if (millis == 0L) return ""
    val d = Date(millis)
    val a = Calendar.getInstance().apply { time = d }
    val b = Calendar.getInstance()
    val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (sameDay) "HH:mm" else "yyyy-MM-dd", Locale.US).format(d)
}
