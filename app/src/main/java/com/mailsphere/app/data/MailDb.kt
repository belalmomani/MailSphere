package com.mailsphere.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local index of message HEADERS only (sender, subject, date, unread flag) in app-private storage.
 * Message bodies are never stored. Rows are removed when the account is removed.
 */
class MailDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "mail_index.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE messages (account TEXT NOT NULL, uid INTEGER NOT NULL, sender TEXT, address TEXT, subject TEXT, " +
                "date INTEGER, unread INTEGER, skey TEXT, cat TEXT, y TEXT, ym TEXT, att INTEGER DEFAULT -1, PRIMARY KEY(account, uid))"
        )
        db.execSQL("CREATE INDEX idx_skey ON messages(skey)")
        db.execSQL("CREATE INDEX idx_cat ON messages(cat)")
        db.execSQL("CREATE INDEX idx_ym ON messages(ym)")
        createAttachments(db)
    }

    private fun createAttachments(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE attachments (account TEXT NOT NULL, uid INTEGER NOT NULL, idx INTEGER NOT NULL, name TEXT, size INTEGER, " +
                "mime TEXT, kind TEXT, PRIMARY KEY(account, uid, idx))"
        )
        db.execSQL("CREATE INDEX idx_att_kind ON attachments(kind)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN att INTEGER DEFAULT -1")
            createAttachments(db)
        }
    }

    private fun col(type: String) = if (type == "cat") "cat" else "skey"

    fun maxUid(account: String): Long =
        readableDatabase.rawQuery("SELECT MAX(uid) FROM messages WHERE account=?", arrayOf(account)).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L
        }

    fun insert(account: String, items: List<MailItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (m in items) {
                val ym = Organizer.monthKey(m)
                val cv = ContentValues().apply {
                    put("account", account)
                    put("uid", m.uid)
                    put("sender", m.sender)
                    put("address", m.senderAddress)
                    put("subject", m.subject)
                    put("date", m.dateMillis)
                    put("unread", if (m.unread) 1 else 0)
                    put("skey", Organizer.senderKey(m))
                    put("cat", Organizer.categoryOf(m).key)
                    put("ym", ym)
                    put("y", ym.take(4))
                }
                db.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteAccount(account: String) {
        writableDatabase.delete("messages", "account=?", arrayOf(account))
        writableDatabase.delete("attachments", "account=?", arrayOf(account))
    }

    /** [total messages, distinct senders, distinct years] */
    fun stats(): List<Int> =
        readableDatabase.rawQuery("SELECT COUNT(*), COUNT(DISTINCT skey), COUNT(DISTINCT y) FROM messages", null).use {
            if (it.moveToFirst()) listOf(it.getInt(0), it.getInt(1), it.getInt(2)) else listOf(0, 0, 0)
        }

    fun count(): Int = stats()[0]

    /** Top-level groups by sender key ("sender") or category ("cat"), biggest first. */
    fun topGroups(type: String): List<Group> {
        val c = col(type)
        return readableDatabase.rawQuery(
            "SELECT $c, MAX(sender), COUNT(*), SUM(unread) FROM messages GROUP BY $c ORDER BY COUNT(*) DESC", null
        ).use {
            val out = mutableListOf<Group>()
            while (it.moveToNext()) out.add(Group(it.getString(0) ?: "", it.getString(1) ?: "", it.getInt(2), it.getInt(3)))
            out
        }
    }

    fun senderLabel(key: String): String =
        readableDatabase.rawQuery("SELECT MAX(sender) FROM messages WHERE skey=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) ?: key else key
        }

    /** Counts per year-month, newest first. key "_" = all messages. */
    fun monthCounts(type: String, key: String): List<Group> {
        val all = key == "_"
        val sql = "SELECT ym, COUNT(*), SUM(unread) FROM messages " + (if (all) "" else "WHERE ${col(type)}=? ") + "GROUP BY ym ORDER BY ym DESC"
        return readableDatabase.rawQuery(sql, if (all) null else arrayOf(key)).use {
            val out = mutableListOf<Group>()
            while (it.moveToNext()) out.add(Group(it.getString(0) ?: "0000-00", it.getString(0) ?: "", it.getInt(1), it.getInt(2)))
            out
        }
    }

    /** (account, uid) of every indexed message in a folder. period: null = whole folder, "yyyy" = a year, "yyyy-MM" = a month. key "_" = all. */
    fun targets(type: String, key: String, period: String?): List<Pair<String, Long>> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (key != "_") {
            where.add("${col(type)}=?")
            args.add(key)
        }
        if (period != null) {
            where.add(if (period.length == 4) "y=?" else "ym=?")
            args.add(period)
        }
        val sql = "SELECT account, uid FROM messages" + (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND "))
        return readableDatabase.rawQuery(sql, if (args.isEmpty()) null else args.toTypedArray()).use {
            val out = mutableListOf<Pair<String, Long>>()
            while (it.moveToNext()) out.add(it.getString(0) to it.getLong(1))
            out
        }
    }

    fun deleteRows(account: String, uids: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (u in uids) {
                db.delete("messages", "account=? AND uid=?", arrayOf(account, u.toString()))
                db.delete("attachments", "account=? AND uid=?", arrayOf(account, u.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- attachments ----------

    fun unscannedCount(account: String? = null): Int {
        val sql = "SELECT COUNT(*) FROM messages WHERE att=-1" + (if (account != null) " AND account=?" else "")
        return readableDatabase.rawQuery(sql, if (account != null) arrayOf(account) else null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun unscanned(account: String, limit: Int): List<Long> =
        readableDatabase.rawQuery("SELECT uid FROM messages WHERE account=? AND att=-1 ORDER BY uid DESC LIMIT $limit", arrayOf(account)).use {
            val out = mutableListOf<Long>()
            while (it.moveToNext()) out.add(it.getLong(0))
            out
        }

    fun saveAttachments(account: String, uid: Long, list: List<AttInfo>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("attachments", "account=? AND uid=?", arrayOf(account, uid.toString()))
            list.forEachIndexed { i, a ->
                val cv = ContentValues().apply {
                    put("account", account); put("uid", uid); put("idx", i)
                    put("name", a.name); put("size", a.size); put("mime", a.mime); put("kind", a.kind)
                }
                db.insertWithOnConflict("attachments", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val cv2 = ContentValues().apply { put("att", list.size) }
            db.update("messages", cv2, "account=? AND uid=?", arrayOf(account, uid.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun attachmentTotal(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM attachments", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun likeEscape(t: String) = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    fun attachments(kind: String?, q: String, limit: Int = 500): List<AttRow> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (kind != null) {
            where.add("a.kind=?")
            args.add(kind)
        }
        for (t in q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            where.add("a.name LIKE ? ESCAPE '\\'")
            args.add(likeEscape(t))
        }
        val sql = "SELECT a.account, a.uid, a.idx, a.name, a.size, a.kind, m.sender, m.date FROM attachments a " +
            "JOIN messages m ON a.account=m.account AND a.uid=m.uid " +
            (if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ") + " ") +
            "ORDER BY m.date DESC LIMIT $limit"
        return readableDatabase.rawQuery(sql, if (args.isEmpty()) null else args.toTypedArray()).use {
            val out = mutableListOf<AttRow>()
            while (it.moveToNext()) {
                out.add(AttRow(it.getString(0) ?: "", it.getLong(1), it.getInt(2), it.getString(3) ?: "", it.getLong(4), it.getString(5) ?: "other", it.getString(6) ?: "", it.getLong(7)))
            }
            out
        }
    }

    // ---------- search (headers only: sender, address, subject, date) ----------

    fun search(q: String, cat: String?, unreadOnly: Boolean, since: Long, hasAtt: Boolean, limit: Int = 500): List<MailItem> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        for (t in q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val like = likeEscape(t)
            where.add("(sender LIKE ? ESCAPE '\\' OR address LIKE ? ESCAPE '\\' OR subject LIKE ? ESCAPE '\\')")
            args.add(like); args.add(like); args.add(like)
        }
        if (cat != null) {
            where.add("cat=?")
            args.add(cat)
        }
        if (unreadOnly) where.add("unread=1")
        if (since > 0) {
            where.add("date>=?")
            args.add(since.toString())
        }
        if (hasAtt) where.add("att>0")
        val sql = "SELECT account, uid, sender, address, subject, date, unread FROM messages" +
            (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) + " ORDER BY date DESC LIMIT $limit"
        return readableDatabase.rawQuery(sql, if (args.isEmpty()) null else args.toTypedArray()).use {
            val out = mutableListOf<MailItem>()
            while (it.moveToNext()) {
                val millis = it.getLong(5)
                out.add(
                    MailItem(
                        sender = it.getString(2) ?: "", subject = it.getString(4) ?: "", dateMillis = millis,
                        unread = it.getInt(6) == 1, time = formatShortDate(millis),
                        uid = it.getLong(1), account = it.getString(0) ?: "", senderAddress = it.getString(3) ?: ""
                    )
                )
            }
            out
        }
    }

    fun messages(type: String, key: String, ym: String, limit: Int = 1500): List<MailItem> {
        val all = key == "_"
        val sql = "SELECT account, uid, sender, address, subject, date, unread FROM messages WHERE " +
            (if (all) "" else "${col(type)}=? AND ") + "ym=? ORDER BY date DESC LIMIT $limit"
        val args = if (all) arrayOf(ym) else arrayOf(key, ym)
        return readableDatabase.rawQuery(sql, args).use {
            val out = mutableListOf<MailItem>()
            while (it.moveToNext()) {
                val millis = it.getLong(5)
                out.add(
                    MailItem(
                        sender = it.getString(2) ?: "", subject = it.getString(4) ?: "", dateMillis = millis,
                        unread = it.getInt(6) == 1, time = formatShortDate(millis),
                        uid = it.getLong(1), account = it.getString(0) ?: "", senderAddress = it.getString(3) ?: ""
                    )
                )
            }
            out
        }
    }

    companion object {
        @Volatile private var inst: MailDb? = null
        fun get(ctx: Context): MailDb =
            inst ?: synchronized(this) { inst ?: MailDb(ctx.applicationContext).also { inst = it } }
    }
}
