package com.mailsphere.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mailsphere.app.data.AccountStore
import com.mailsphere.app.data.ImapClient
import com.mailsphere.app.data.MailItem
import com.mailsphere.app.data.OAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs on the device only. Checks each account's INBOX for unread mail newer than the last one seen. */
class NewMailWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!NotifyManager.isEnabled(app)) return Result.success()
        val prefs = app.getSharedPreferences("notify_state", Context.MODE_PRIVATE)
        val unread = mutableListOf<MailItem>()
        for (acc in AccountStore.list(app)) {
            try {
                val token = if (acc.authState != null) OAuth.accessToken(app, acc) else null
                val key = "last_" + acc.email
                val last = prefs.getLong(key, 0L)
                val (items, maxUid) = withContext(Dispatchers.IO) { ImapClient.newMessagesSince(acc, token, last) }
                prefs.edit().putLong(key, maxUid).apply()
                if (last > 0L) unread.addAll(items.filter { it.unread })
            } catch (e: Exception) {
                // network or auth problem: skip this round, try again next period
            }
        }
        if (unread.isNotEmpty()) NotifyManager.show(app, unread.sortedByDescending { it.dateMillis })
        return Result.success()
    }
}
