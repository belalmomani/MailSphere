package com.mailsphere.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Moves messages to the provider's Trash (never permanent deletion) and removes them from the local index only when verified gone. */
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

    private const val CHUNK = 200

    /** Returns null on full success, otherwise a readable error summary (already-moved messages stay moved). */
    suspend fun delete(ctx: Context, targets: List<Pair<String, Long>>): String? = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val errors = mutableListOf<String>()
        val db = MailDb.get(app)
        val distinct = targets.distinct()
        running = true
        done = 0
        total = distinct.size
        try {
            val accounts = AccountStore.list(app).associateBy { it.email }
            for ((email, uids) in distinct.groupBy({ it.first }, { it.second })) {
                val acc = accounts[email]
                if (acc == null) {
                    errors.add("$email: account not found")
                    continue
                }
                var moved = 0
                for (chunk in uids.chunked(CHUNK)) {
                    var gone: List<Long>? = null
                    var lastErr: Exception? = null
                    for (attempt in 1..3) {
                        try {
                            val token = if (acc.authState != null) OAuth.accessToken(app, acc) else null
                            gone = ImapClient.moveChunk(acc, token, chunk)
                            break
                        } catch (e: Exception) {
                            lastErr = e
                            delay(1500)
                        }
                    }
                    val g = gone
                    if (g == null) {
                        val err = lastErr
                        errors.add(
                            email + ": moved " + moved + " of " + uids.size + " - " +
                                (err?.javaClass?.simpleName ?: "error") + ": " + (err?.message ?: "")
                        )
                        break // stop this account; what was moved stays moved
                    }
                    if (g.isNotEmpty()) {
                        db.deleteRows(email, g)
                        moved += g.size
                        done += g.size
                        version += 1
                    }
                    if (g.size < chunk.size) {
                        errors.add(email + ": " + (chunk.size - g.size) + " message(s) could not be moved (the server kept them)")
                    }
                }
            }
        } finally {
            running = false
            version += 1
        }
        if (errors.isEmpty()) null else errors.joinToString("\n").take(320)
    }
}
