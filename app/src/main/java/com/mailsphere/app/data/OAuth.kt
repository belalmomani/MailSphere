package com.mailsphere.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.mailsphere.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** OAuth 2.0 + PKCE sign-in (no client secret, no passwords). Providers: "google", "microsoft". */
object OAuth {
    fun clientId(ctx: Context, provider: String): String =
        ctx.getString(if (provider == "google") R.string.google_client_id else R.string.microsoft_client_id)

    fun imapHost(provider: String): Pair<String, Int> =
        if (provider == "google") "imap.gmail.com" to 993 else "outlook.office365.com" to 993

    fun request(ctx: Context, provider: String): AuthorizationRequest {
        val google = provider == "google"
        val cfg = AuthorizationServiceConfiguration(
            Uri.parse(if (google) "https://accounts.google.com/o/oauth2/v2/auth" else "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"),
            Uri.parse(if (google) "https://oauth2.googleapis.com/token" else "https://login.microsoftonline.com/common/oauth2/v2.0/token")
        )
        val scope = if (google) "openid email https://mail.google.com/"
        else "openid email offline_access https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send"
        val redirect = if (google) "com.mailsphere.app:/oauth2redirect" else "com.mailsphere.app://oauth2redirect"
        return AuthorizationRequest.Builder(cfg, clientId(ctx, provider), ResponseTypeValues.CODE, Uri.parse(redirect))
            .setScope(scope)
            .build()
    }

    fun emailFromIdToken(idToken: String?): String? = try {
        val payload = idToken!!.split(".")[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        val o = JSONObject(json)
        o.optString("email").ifBlank { o.optString("preferred_username") }.ifBlank { null }
    } catch (e: Exception) {
        null
    }

    /** Returns a valid access token, refreshing it if needed, and saves the updated state. Never logged. */
    suspend fun accessToken(ctx: Context, acc: Account): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val state = AuthState.jsonDeserialize(acc.authState ?: "{}")
            val svc = AuthorizationService(ctx)
            state.performActionWithFreshTokens(svc) { token, _, ex ->
                svc.dispose()
                if (token != null) {
                    AccountStore.add(ctx, acc.copy(authState = state.jsonSerializeString()))
                    cont.resume(token)
                } else {
                    cont.resumeWithException(ex ?: IllegalStateException("No access token"))
                }
            }
        }
    }
}
