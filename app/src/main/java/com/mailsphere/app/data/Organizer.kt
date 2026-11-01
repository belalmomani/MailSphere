package com.mailsphere.app.data

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** In-memory (session only) cache of fetched message headers shared by Folders / Organize screens. */
object MailRepo {
    var items by mutableStateOf<List<MailItem>>(emptyList())
        private set

    /** Fetches the latest [count] headers per account. Returns an error text only if everything failed. */
    suspend fun loadAll(ctx: Context, count: Int = 200): String? {
        val accounts = AccountStore.list(ctx)
        val errors = mutableListOf<String>()
        val result = withContext(Dispatchers.IO) {
            accounts.flatMap { acc ->
                try {
                    val token = if (acc.authState != null) OAuth.accessToken(ctx, acc) else null
                    ImapClient.fetchLatest(acc, token, count)
                } catch (e: Exception) {
                    errors.add(acc.email + ": " + (e.message ?: e.javaClass.simpleName))
                    emptyList()
                }
            }.sortedByDescending { it.dateMillis }
        }
        items = result
        return if (result.isEmpty() && errors.isNotEmpty()) errors.first() else null
    }
}

data class Group(val key: String, val label: String, val count: Int, val unread: Int)

data class Category(val key: String, val title: Bi, val icon: ImageVector, val color: Color, val words: List<String>)

/** On-device, rule-based organizing (no external service). Category rules are simple keyword matches => approximate. */
object Organizer {
    val categories = listOf(
        Category("invoices", Bi("الفواتير", "Invoices"), Icons.Filled.ReceiptLong, Color(0xFF22C55E),
            listOf("invoice", "receipt", "payment", "billing", "فاتورة", "إيصال", "دفع")),
        Category("security", Bi("الأمان والحسابات", "Security & accounts"), Icons.Filled.Security, Color(0xFF6366F1),
            listOf("security", "verify", "verification", "password", "sign-in", "sign in", "login", "one-time", "code", "أمان", "تحقق", "كلمة المرور", "تسجيل دخول")),
        Category("ai", Bi("الذكاء الاصطناعي", "AI"), Icons.Filled.SmartToy, Color(0xFF3B82F6),
            listOf("chatgpt", "openai", "anthropic", "claude", "gemini", "perplexity", "midjourney", "copilot", "mistral", "huggingface")),
        Category("shopping", Bi("التسوق", "Shopping"), Icons.Filled.ShoppingCart, Color(0xFFEF4444),
            listOf("amazon", "temu", "aliexpress", "ebay", "shein", "noon", "order", "shipping", "delivery", "طلب", "شحن", "تسوق", "خصم", "عرض")),
        Category("travel", Bi("السفر والحجوزات", "Travel & bookings"), Icons.Filled.Flight, Color(0xFF0EA5E9),
            listOf("booking", "airbnb", "flight", "ticket", "hotel", "reservation", "trip", "حجز", "رحلة", "تذكرة")),
        Category("work", Bi("العمل", "Work"), Icons.Filled.Work, Color(0xFFF59E0B),
            listOf("github", "gitlab", "slack", "jira", "linkedin", "zoom", "meeting", "project", "repository", "اجتماع", "مشروع")),
        Category("newsletters", Bi("النشرات البريدية", "Newsletters"), Icons.Filled.Campaign, Color(0xFFEAB308),
            listOf("newsletter", "digest", "weekly", "unsubscribe", "نشرة"))
    )
    val other = Category("other", Bi("غير مصنّف", "Uncategorized"), Icons.Filled.Folder, Color(0xFF64748B), emptyList())

    fun categoryOf(m: MailItem): Category {
        val text = (m.senderAddress + " " + m.sender + " " + m.subject).lowercase()
        return categories.firstOrNull { c -> c.words.any { text.contains(it) } } ?: other
    }

    fun byKey(key: String): Category = categories.firstOrNull { it.key == key } ?: other

    private val freeMail = setOf(
        "gmail.com", "googlemail.com", "yahoo.com", "ymail.com", "outlook.com", "hotmail.com",
        "live.com", "msn.com", "icloud.com", "me.com", "aol.com", "proton.me", "protonmail.com"
    )

    fun senderKey(m: MailItem): String {
        val addr = m.senderAddress.lowercase()
        if (!addr.contains('@')) return m.sender.lowercase()
        val d = addr.substringAfter('@')
        if (d in freeMail) return addr
        val p = d.split('.')
        return when {
            p.size >= 3 && p[p.size - 2].length <= 3 && p.last().length == 2 -> p[p.size - 3]
            p.size >= 2 -> p[p.size - 2]
            else -> d
        }
    }

    fun senderLabel(key: String, ofSender: List<MailItem>): String =
        if (key.contains('@')) (ofSender.firstOrNull()?.sender ?: key) else key.replaceFirstChar { it.uppercase() }

    fun monthKey(m: MailItem): String {
        if (m.dateMillis == 0L) return "0000-00"
        val c = Calendar.getInstance().apply { timeInMillis = m.dateMillis }
        return String.format("%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    fun senderGroups(items: List<MailItem>): List<Group> =
        items.groupBy { senderKey(it) }
            .map { (k, v) -> Group(k, senderLabel(k, v), v.size, v.count { it.unread }) }
            .sortedByDescending { it.count }

    fun categoryGroups(items: List<MailItem>): List<Group> =
        items.groupBy { categoryOf(it).key }
            .map { (k, v) -> Group(k, k, v.size, v.count { it.unread }) }
            .sortedByDescending { it.count }

    fun monthGroups(items: List<MailItem>): List<Group> =
        items.groupBy { monthKey(it) }
            .map { (k, v) -> Group(k, k, v.size, v.count { it.unread }) }
            .sortedByDescending { it.key }

    fun filter(items: List<MailItem>, type: String, key: String): List<MailItem> = when (type) {
        "sender" -> items.filter { senderKey(it) == key }
        "cat" -> items.filter { categoryOf(it).key == key }
        else -> items.filter { monthKey(it) == key }
    }
}
