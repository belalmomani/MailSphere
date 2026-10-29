package com.mailsphere.app.ads

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Central ads switches. Change here (one place). */
object AdsConfig {
    /** Master switch: false = SDK is never initialized and no ad is ever shown. */
    const val ADS_ENABLED = true
    /** true = Start.io TEST ads (use while testing; set to false for real ads). */
    const val TEST_MODE = true
    /** Max ad impressions per device per day (not simultaneous ads). */
    const val MAX_IMPRESSIONS_PER_DAY = 3
    /** Minimum gap between two ads. */
    const val MIN_INTERVAL_MS = 3 * 60 * 1000L
}

/** Caps ad impressions per device per day; the counter survives app restarts (SharedPreferences). */
class AdFrequencyLimiter(
    context: Context,
    private val maxPerDay: Int = AdsConfig.MAX_IMPRESSIONS_PER_DAY
) {
    private val prefs = context.getSharedPreferences("ad_limiter", Context.MODE_PRIVATE)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun resetIfNewDay() {
        if (prefs.getString("day", null) != today()) {
            prefs.edit().putString("day", today()).putInt("count", 0).apply()
        }
    }

    fun canShow(): Boolean {
        if (!AdsConfig.ADS_ENABLED) return false
        resetIfNewDay()
        return prefs.getInt("count", 0) < maxPerDay
    }

    fun recordImpression() {
        resetIfNewDay()
        prefs.edit().putInt("count", prefs.getInt("count", 0) + 1).apply()
    }
}
