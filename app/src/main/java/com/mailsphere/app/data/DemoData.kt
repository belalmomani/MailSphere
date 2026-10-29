package com.mailsphere.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Bilingual text (Arabic / English). */
data class Bi(val ar: String, val en: String)

/** DEMO DATA ONLY — no real mail is connected. */
data class DemoMail(
    val sender: String,
    val subject: Bi,
    val preview: Bi,
    val time: String,
    val unread: Int,
    val color: Color,
    val important: Boolean = false
)

data class DemoCategory(val icon: ImageVector, val title: Bi, val count: Int, val color: Color)

enum class FileKind { PDF, IMAGE, VIDEO, DOC, ZIP }

data class DemoFile(val name: String, val size: String, val kind: FileKind)

object DemoData {
    val mails = listOf(
        DemoMail("ChatGPT", Bi("تحديثات جديدة في ChatGPT", "New updates in ChatGPT"),
            Bi("اكتشف الميزات الجديدة...", "Discover the new features..."), "10:24", 3, Color(0xFF10A37F)),
        DemoMail("Temu", Bi("عرض خاص لفترة محدودة", "Limited-time special offer"),
            Bi("خصومات حتى 70% على منتجات مختارة", "Up to 70% off selected items"), "09:50", 5, Color(0xFFFF6A00)),
        DemoMail("GitHub", Bi("تم تحديث مستودعك", "Your repository was updated"),
            Bi("تم إجراء تغييرات على ملف المشروع", "Changes were made to a project file"), "08:32", 1, Color(0xFF24292F), true),
        DemoMail("Google", Bi("أمان حسابك", "Your account security"),
            Bi("تم تسجيل دخول جديد إلى حسابك", "A new sign-in to your account"), "07:16", 2, Color(0xFF4285F4), true),
        DemoMail("Microsoft", Bi("إشعار اشتراك", "Subscription notice"),
            Bi("تم تجديد اشتراك Office 365", "Your Office 365 subscription was renewed"), "06:42", 1, Color(0xFF00A4EF))
    )

    val categories = listOf(
        DemoCategory(Icons.Filled.Work, Bi("العمل", "Work"), 234, Color(0xFFF59E0B)),
        DemoCategory(Icons.Filled.ShoppingCart, Bi("التسوق", "Shopping"), 189, Color(0xFFEF4444)),
        DemoCategory(Icons.Filled.SmartToy, Bi("الذكاء الاصطناعي", "AI"), 156, Color(0xFF3B82F6)),
        DemoCategory(Icons.Filled.ReceiptLong, Bi("الفواتير", "Invoices"), 98, Color(0xFF22C55E)),
        DemoCategory(Icons.Filled.Security, Bi("الأمان والحسابات", "Security & accounts"), 73, Color(0xFF6366F1)),
        DemoCategory(Icons.Filled.Flight, Bi("السفر والحجوزات", "Travel & bookings"), 64, Color(0xFF0EA5E9)),
        DemoCategory(Icons.Filled.Person, Bi("الشخصي", "Personal"), 48, Color(0xFFF97316)),
        DemoCategory(Icons.Filled.Campaign, Bi("النشرات البريدية", "Newsletters"), 112, Color(0xFFEAB308)),
        DemoCategory(Icons.Filled.Folder, Bi("غير مصنّف", "Uncategorized"), 36, Color(0xFF64748B))
    )

    val senderFolders = listOf(
        Triple("ChatGPT", 45, Color(0xFF10A37F)),
        Triple("GitHub", 28, Color(0xFF24292F))
    )

    val files = listOf(
        DemoFile("invoice_demo.pdf", "2.4 MB", FileKind.PDF),
        DemoFile("product_photo.jpg", "1.2 MB", FileKind.IMAGE),
        DemoFile("demo_video.mp4", "12.6 MB", FileKind.VIDEO),
        DemoFile("work_document.docx", "856 KB", FileKind.DOC),
        DemoFile("archive_demo.zip", "5.1 MB", FileKind.ZIP),
        DemoFile("trip_photo.jpg", "3.8 MB", FileKind.IMAGE),
        DemoFile("contract_demo.pdf", "640 KB", FileKind.PDF)
    )
}
