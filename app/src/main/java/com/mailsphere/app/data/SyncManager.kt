package com.mailsphere.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Indexes ALL inbox message headers of every account into the local database, oldest first, in chunks.
 * It is resumable: the next run continues after the highest UID already stored (also used for "update new mail").
 * Runs while the app process is alive (keep the app open for the first run on a big mailbox).
 */
object SyncManager {
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var current by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastFinished by mutableLongStateOf(0L)
        private set

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(ctx: Context) {
        if (running) return
        val app = ctx.applicationContext
        running = true
        error = null
        done = 0
        total = 0
        job = scope.launch {
            val me = coroutineContext[Job]
            try {
                val db = MailDb.get(app)
                var prevDone = 0
                var prevTotal = 0
                val errors = mutableListOf<String>()
                for (acc in AccountStore.list(app)) {
                    current = acc.email
                    var ok = false
                    var lastErr: Exception? = null
                    var accDone = 0
                    var accTotal = 0
                    for (attempt in 1..3) {
                        if (me?.isActive == false) break
                        accDone = 0
                        accTotal = 0
                        try {
                            val token = if (acc.authState != null) OAuth.accessToken(app, acc) else null
                            ImapClient.syncAccount(
                                acc, token, db,
                                alive = { me?.isActive != false },
                                onTotal = { accTotal = it; total = prevTotal + accTotal },
                                onProgress = { accDone += it; done = prevDone + accDone }
                            )
                            ok = true
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lastErr = e
                        }
                    }
                    prevDone += accDone
                    prevTotal += accTotal
                    val err = lastErr
                    if (!ok && err != null) errors.add(acc.email + ": " + (err.message ?: err.javaClass.simpleName))
                }
                if (errors.isNotEmpty()) error = errors.first().take(160)
            } catch (e: CancellationException) {
                // cancelled by the user: progress already saved, next run resumes
            } finally {
                running = false
                lastFinished = System.currentTimeMillis()
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
