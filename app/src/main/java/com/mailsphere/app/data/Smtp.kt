package com.mailsphere.app.data

import java.util.Properties
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** In-memory hand-off between Message/Inbox screens and the compose screen (never persisted). */
object ComposeDraft {
    var from: String? = null
    var to: String = ""
    var subject: String = ""
    var body: String = ""
    var inReplyTo: String? = null

    fun reset() {
        from = null; to = ""; subject = ""; body = ""; inReplyTo = null
    }
}

/** Blocking SMTP sender (SSL or STARTTLS, password or XOAUTH2). Call from a background dispatcher. */
object Smtp {
    data class Server(val host: String, val port: Int, val ssl: Boolean)

    fun serverFor(acc: Account): Server = when (acc.host) {
        "imap.gmail.com" -> Server("smtp.gmail.com", 465, true)
        "imap.mail.yahoo.com" -> Server("smtp.mail.yahoo.com", 465, true)
        "imap.mail.me.com" -> Server("smtp.mail.me.com", 587, false)
        "outlook.office365.com" -> Server("smtp.office365.com", 587, false)
        else -> Server(acc.host.replaceFirst("imap", "smtp"), 465, true) // best guess for custom servers
    }

    fun send(acc: Account, token: String?, to: String, subject: String, body: String, inReplyTo: String?) {
        val srv = serverFor(acc)
        val props = Properties().apply {
            put("mail.smtp.host", srv.host)
            put("mail.smtp.port", srv.port.toString())
            put("mail.smtp.auth", "true")
            if (srv.ssl) put("mail.smtp.ssl.enable", "true")
            else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "20000")
            put("mail.smtp.writetimeout", "20000")
            if (token != null) put("mail.smtp.auth.mechanisms", "XOAUTH2")
        }
        val session = Session.getInstance(props)
        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(acc.email))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
            if (!inReplyTo.isNullOrBlank()) {
                setHeader("In-Reply-To", inReplyTo)
                setHeader("References", inReplyTo)
            }
        }
        val transport = session.getTransport("smtp")
        try {
            transport.connect(srv.host, srv.port, acc.email, token ?: acc.password)
            transport.sendMessage(msg, msg.allRecipients)
        } finally {
            transport.close()
        }
    }
}
