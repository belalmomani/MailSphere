package com.mailsphere.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** provider: "password" (IMAP app password), "google" or "microsoft" (OAuth; authState = AppAuth AuthState JSON). */
data class Account(
    val email: String,
    val host: String,
    val port: Int,
    val password: String,
    val provider: String = "password",
    val authState: String? = null
)

data class Preset(val host: String, val port: Int, val supported: Boolean = true)

object Providers {
    fun presetFor(email: String): Preset? {
        val d = email.substringAfter('@', "").lowercase()
        return when {
            d == "gmail.com" || d == "googlemail.com" -> Preset("imap.gmail.com", 993)
            d.startsWith("yahoo.") || d == "ymail.com" -> Preset("imap.mail.yahoo.com", 993)
            d == "icloud.com" || d == "me.com" || d == "mac.com" -> Preset("imap.mail.me.com", 993)
            // Microsoft no longer accepts password IMAP for personal accounts: needs OAuth (later phase).
            d in listOf("outlook.com", "hotmail.com", "live.com", "msn.com") -> Preset("outlook.office365.com", 993, supported = false)
            else -> null
        }
    }
}

/** Credentials are stored ONLY on this device, encrypted (Android Keystore AES-256). Backup is disabled in the manifest. */
object AccountStore {
    private fun prefs(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, "mailsphere_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun list(ctx: Context): List<Account> = try {
        val arr = JSONArray(prefs(ctx).getString("accounts", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Account(
                o.getString("email"), o.getString("host"), o.getInt("port"), o.getString("password"),
                o.optString("provider", "password"),
                if (o.has("authState")) o.getString("authState") else null
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(ctx: Context, accounts: List<Account>) {
        val arr = JSONArray()
        accounts.forEach {
            val o = JSONObject().put("email", it.email).put("host", it.host).put("port", it.port).put("password", it.password).put("provider", it.provider)
            if (it.authState != null) o.put("authState", it.authState)
            arr.put(o)
        }
        prefs(ctx).edit().putString("accounts", arr.toString()).apply()
    }

    fun add(ctx: Context, account: Account) = save(ctx, list(ctx).filter { it.email != account.email } + account)

    fun remove(ctx: Context, email: String) = save(ctx, list(ctx).filter { it.email != email })
}
