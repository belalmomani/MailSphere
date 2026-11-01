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
            if (token != null) put("mail.imaps.auth.mechanisms", "XOAUTH2")
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

    private fun formatTime(d: Date?): String {
        if (d == null) return ""
        val a = Calendar.getInstance().apply { time = d }
        val b = Calendar.getInstance()
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return SimpleDateFormat(if (sameDay) "HH:mm" else "dd/MM", Locale.US).format(d)
    }
}
