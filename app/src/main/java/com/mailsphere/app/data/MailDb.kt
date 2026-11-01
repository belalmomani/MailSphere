package com.mailsphere.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local index of message HEADERS only (sender, subject, date, unread flag) in app-private storage.
 * Message bodies are never stored. Rows are removed when the account is removed.
 */
class MailDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "mail_index.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE messages (account TEXT NOT NULL, uid INTEGER NOT NULL, sender TEXT, address TEXT, subject TEXT, " +
                "date INTEGER, unread INTEGER, skey TEXT, cat TEXT, y TEXT, ym TEXT, PRIMARY KEY(account, uid))"
        )
        db.execSQL("CREATE INDEX idx_skey ON messages(skey)")
        db.execSQL("CREATE INDEX idx_cat ON messages(cat)")
        db.execSQL("CREATE INDEX idx_ym ON messages(ym)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

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
