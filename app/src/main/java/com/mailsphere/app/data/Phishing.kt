package com.mailsphere.app.data

/** level: 0 = nothing found, 1 = caution, 2 = suspicious. Indicators only, never proof. */
data class PhishResult(val level: Int, val reasons: List<Bi>)

/** On-device heuristics only. Nothing is sent anywhere. */
object PhishingCheck {
    private val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "cutt.ly", "rebrand.ly", "shorturl.at")
    private val urgent = listOf(
        "verify your account", "account suspended", "your account will be", "confirm your password", "unusual sign-in",
        "act now", "urgent action", "تحقق من حسابك", "سيتم تعليق", "تم تعليق حسابك", "أدخل كلمة المرور", "إجراء عاجل"
    )
    private val linkRe = Regex("<a\\s[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tagRe = Regex("<[^>]*>")
    private val domainRe = Regex("([a-z0-9-]+\\.)+[a-z]{2,}", RegexOption.IGNORE_CASE)
    private val ipRe = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val authFailRe = Regex("(spf|dkim|dmarc)=(fail|softfail|permerror)")

    fun baseDomain(host: String): String {
        val h = host.lowercase().trimEnd('.')
        val p = h.split('.')
        return when {
            p.size >= 3 && p[p.size - 2].length <= 3 && p.last().length == 2 -> p.takeLast(3).joinToString(".")
            p.size >= 2 -> p.takeLast(2).joinToString(".")
            else -> h
        }
    }

    fun analyze(b: MailBody): PhishResult {
        var score = 0
        val reasons = mutableListOf<Bi>()
        val fromDom = b.fromAddress.substringAfter('@', "").lowercase()

        if (authFailRe.containsMatchIn(b.authResults)) {
            score += 3
            reasons.add(Bi("فشل التحقق من هوية المرسل (SPF/DKIM/DMARC): قد لا تكون الرسالة من الجهة التي تدّعيها.", "Sender authentication failed (SPF/DKIM/DMARC): the message may not come from who it claims."))
        }

        val shownDomain = domainRe.find(b.fromName.lowercase())?.value
        if (shownDomain != null && fromDom.isNotEmpty() && baseDomain(shownDomain) != baseDomain(fromDom)) {
            score += 2
            reasons.add(Bi("اسم المرسل يذكر $shownDomain لكن العنوان الفعلي من $fromDom.", "The sender name mentions $shownDomain but the real address is from $fromDom."))
        }

        if (fromDom.contains("xn--")) {
            score += 1
            reasons.add(Bi("نطاق المرسل يستخدم ترميزًا غير معتاد (قد يقلّد نطاقًا آخر).", "The sender domain uses unusual encoding (it may imitate another domain)."))
        }

        val replyDom = b.replyAddress.substringAfter('@', "").lowercase()
        if (replyDom.isNotEmpty() && fromDom.isNotEmpty() && baseDomain(replyDom) != baseDomain(fromDom)) {
            score += 1
            reasons.add(Bi("الرد سيذهب إلى عنوان من نطاق مختلف ($replyDom).", "Replies would go to a different domain ($replyDom)."))
        }

        var mismatch = false
        var ipLink = false
        var shortLink = false
        val html = b.html?.take(200_000)
        if (html != null) {
            for (m in linkRe.findAll(html)) {
                val href = m.groupValues[1].trim()
                val host = try {
                    if (href.startsWith("http://", true) || href.startsWith("https://", true)) java.net.URI(href).host else null
                } catch (e: Exception) {
                    null
                } ?: continue
                val text = tagRe.replace(m.groupValues[2], "").trim()
                val textDom = domainRe.find(text)?.value
                if (!mismatch && textDom != null && baseDomain(textDom) != baseDomain(host)) {
                    mismatch = true
                    score += 2
                    reasons.add(Bi("رابط يعرض «$textDom» لكنه يفتح $host.", "A link shows “$textDom” but opens $host."))
                }
                if (!ipLink && ipRe.matches(host)) {
                    ipLink = true
                    score += 2
                    reasons.add(Bi("يوجد رابط يشير إلى عنوان رقمي (IP) بدل اسم موقع.", "A link points to a numeric IP address instead of a site name."))
                }
                if (!shortLink && baseDomain(host) in shorteners) {
                    shortLink = true
                    score += 1
                    reasons.add(Bi("يوجد رابط مختصر يخفي وجهته الحقيقية.", "A shortened link hides its real destination."))
                }
            }
        }

        val text = ((b.subject) + " " + (b.text ?: "")).lowercase()
        if (urgent.any { text.contains(it) }) {
            score += 1
            reasons.add(Bi("لغة استعجال أو تهديد (مثل تعليق الحساب).", "Urgent or threatening language (e.g. account suspension)."))
        }

        val level = when {
            score >= 3 -> 2
            score >= 1 -> 1
            else -> 0
        }
        return PhishResult(level, reasons)
    }
}
