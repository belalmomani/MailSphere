package com.mailsphere.app.ads

import android.app.Activity
import android.os.SystemClock
import com.mailsphere.app.R
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import java.lang.ref.WeakReference

/**
 * Start.io interstitial manager. Rules: at most AdsConfig.MAX_IMPRESSIONS_PER_DAY per device per day
 * (persisted), at least MIN_INTERVAL_MS between ads, shown only when leaving a task screen
 * (never on taps, never while reading mail, never over buttons).
 */
object AdManager {
    private var activityRef: WeakReference<Activity>? = null
    private var limiter: AdFrequencyLimiter? = null
    private var ad: StartAppAd? = null
    private var ready = false
    private var loading = false
    private var lastShownAt = 0L
    private var sdkInitialized = false

    fun attach(activity: Activity) {
        if (!AdsConfig.ADS_ENABLED) return
        activityRef = WeakReference(activity)
        if (!sdkInitialized) {
            StartAppSDK.setTestAdsEnabled(AdsConfig.TEST_MODE)
            StartAppSDK.initParams(activity.applicationContext, activity.getString(R.string.startio_app_id)).init()
            sdkInitialized = true
        }
        limiter = AdFrequencyLimiter(activity.applicationContext)
        preload(activity)
    }

    fun detach(activity: Activity) {
        if (activityRef?.get() === activity) {
            activityRef = null
            ad = null
            ready = false
            loading = false
        }
    }

    private fun preload(activity: Activity) {
        if (loading || ready) return
        loading = true
        val newAd = StartAppAd(activity)
        newAd.loadAd(object : AdEventListener {
            override fun onReceiveAd(p0: Ad) {
                ad = newAd
                ready = true
                loading = false
            }

            override fun onFailedToReceiveAd(p0: Ad?) {
                loading = false // do not call loadAd() from here (SDK docs)
            }
        })
    }

    /** Call at a natural pause (e.g. after leaving the search screen). Silently does nothing if any rule blocks it. */
    fun maybeShow() {
        val activity = activityRef?.get() ?: return
        val lim = limiter ?: return
        val now = SystemClock.elapsedRealtime()
        val tooSoon = lastShownAt != 0L && now - lastShownAt < AdsConfig.MIN_INTERVAL_MS
        val current = ad
        if (!ready || current == null || tooSoon || !lim.canShow()) {
            preload(activity)
            return
        }
        ready = false
        ad = null
        current.showAd(object : AdDisplayListener {
            override fun adHidden(p0: Ad) { activityRef?.get()?.let { preload(it) } }
            override fun adDisplayed(p0: Ad) {
                lim.recordImpression()
                lastShownAt = SystemClock.elapsedRealtime()
            }
            override fun adClicked(p0: Ad) {}
            override fun adNotDisplayed(p0: Ad) { activityRef?.get()?.let { preload(it) } }
        })
    }
}
