package com.mailsphere.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mailsphere.app.MainActivity
import com.mailsphere.app.R
import com.mailsphere.app.data.MailItem
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Opt-in new-mail notifications: a periodic background check (about every 15 minutes while online). Not instant. */
object NotifyManager {
    private const val CHANNEL = "new_mail"
    private const val WORK = "new_mail_check"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("notify_state", Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("enabled", false)

    fun enable(ctx: Context) {
        prefs(ctx).edit().putBoolean("enabled", true).apply()
        val wm = WorkManager.getInstance(ctx.applicationContext)
        val req = PeriodicWorkRequestBuilder<NewMailWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        // One immediate run so the "already seen" baseline is set right now.
        wm.enqueue(OneTimeWorkRequestBuilder<NewMailWorker>().build())
    }

    fun disable(ctx: Context) {
        prefs(ctx).edit().putBoolean("enabled", false).apply()
        WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(WORK)
    }

    private fun isArabic(): Boolean {
        val loc = AppCompatDelegate.getApplicationLocales()
        val lang = if (!loc.isEmpty) loc[0]?.language else Locale.getDefault().language
        return lang == "ar"
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, if (isArabic()) "رسائل جديدة" else "New mail", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, items: List<MailItem>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val ar = isArabic()
        val n = items.size
        val title = if (ar) "$n رسالة جديدة" else (if (n == 1) "1 new message" else "$n new messages")
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicVersion = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .build()
        val style = NotificationCompat.InboxStyle()
        items.take(5).forEach { style.addLine(it.sender + ": " + it.subject) }
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(items.first().sender + ": " + items.first().subject)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE) // hidden on a locked screen
            .setPublicVersion(publicVersion)
            .build()
        NotificationManagerCompat.from(ctx).notify(1001, notification)
    }
}
