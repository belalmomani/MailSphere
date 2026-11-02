package com.mailsphere.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Moves messages to the provider's Trash (never permanent deletion) and removes them from the local index. */
object Deleter {
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set

    /** Incremented whenever index rows were removed, so screens can reload. */
    var version by mutableIntStateOf(0)
        private set

    /** Returns null on full success, otherwise the first error (already-moved messages stay moved). */
    suspend fun delete(ctx: Context, targets: List<Pair<String, Long>>): String? = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val errors = mutableListOf<String>()
        val db = MailDb.get(app)
        running = true
        done = 0
        total = targets.distinct().size
        try {
            val accounts = AccountStore.list(app).associateBy { it.email }
            for ((email, uids) in targets.distinct().groupBy({ it.first }, { it.second })) {
                val acc = accounts[email]
                if (acc == null) {
                    errors.add("$email: account not found")
                    continue
                }
                try {
                    val token = if (acc.authState != null) OAuth.accessToken(app, acc) else null
                    ImapClient.moveToTrash(acc, token, uids) { chunk ->
                        db.deleteRows(email, chunk)
                        done += chunk.size
                        version += 1
                    }
                } catch (e: Exception) {
                    errors.add(email + ": " + (e.message ?: e.javaClass.simpleName))
                }
            }
        } finally {
            running = false
            version += 1
        }
        errors.firstOrNull()?.take(160)
    }
}
