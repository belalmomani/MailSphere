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
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress

data class MailItem(val sender: String, val subject: String, val dateMillis: Long, val unread: Boolean, val time: String)

private val palette = listOf(0xFF3B82F6, 0xFF10A37F, 0xFFF59E0B, 0xFFEF4444, 0xFF6366F1, 0xFF0EA5E9, 0xFFF97316, 0xFF64748B)

fun MailItem.toDemoMail() = DemoMail(
    sender = sender,
    subject = Bi(subject, subject),
    preview = Bi("", ""),
    time = time,
    unread = if (unread) 1 else 0,
    color = Color(palette[sender.hashCode().mod(palette.size)])
)

/** Blocking IMAP-over-SSL client. Call from a background dispatcher only. Never logs credentials. */
object ImapClient {
    private fun connect(acc: Account): Store {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "20000")
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect(acc.host, acc.port, acc.email, acc.password)
        return store
    }

    fun testLogin(acc: Account) {
        val store = connect(acc)
        store.close()
    }

    fun fetchLatest(acc: Account, count: Int = 30): List<MailItem> {
        val store = connect(acc)
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
                inbox.fetch(msgs, fp)
                return msgs.reversed().map { m ->
                    val from = m.from?.firstOrNull() as? InternetAddress
                    val date: Date? = m.sentDate ?: m.receivedDate
                    MailItem(
                        sender = from?.personal?.takeIf { it.isNotBlank() } ?: from?.address ?: "?",
                        subject = m.subject ?: "",
                        dateMillis = date?.time ?: 0L,
                        unread = !m.isSet(Flags.Flag.SEEN),
                        time = formatTime(date)
                    )
                }
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
