# إعداد تسجيل الدخول بـ Google وMicrosoft (مجاني)

معرّفات العميل (Client IDs) **ليست أسرارًا**، وتُوضع في `app/src/main/res/values/oauth.xml`.

## Google (Gmail)
1. https://console.cloud.google.com ← مشروع جديد باسم MailSphere (لا تستخدم مشروع Firebase المشترك).
2. APIs & Services ← Library ← فعّل **Gmail API**.
3. OAuth consent screen (أو Google Auth Platform ← Branding/Audience): اسم التطبيق MailSphere، بريد الدعم، النوع **External**، الحالة **Testing**، وأضف **Test users** (حتى 100 بريد).
4. Data Access ← Add scopes: `https://mail.google.com/` و`openid` و`email`.
5. Clients ← Create client ← **Android**: Package `com.mailsphere.app`، وSHA-1 = `48:42:71:A7:0C:43:63:B3:8D:F2:85:7C:FC:A8:49:3D:D4:23:B0:F3`.
6. في إعدادات العميل المتقدمة فعّل **Enable custom URI scheme**.
7. انسخ Client ID وضعه في `google_client_id`.

## Microsoft (Outlook / Hotmail / Live)
1. https://entra.microsoft.com ← App registrations ← New registration: الاسم MailSphere، النوع: *Accounts in any organizational directory and personal Microsoft accounts*.
2. Authentication ← Add platform ← **Mobile and desktop applications** ← Custom redirect URI: `com.mailsphere.app://oauth2redirect`.
3. Authentication ← **Allow public client flows = Yes**.
4. API permissions ← Add ← APIs my organization uses ← **Office 365 Exchange Online** ← Delegated ← `IMAP.AccessAsUser.All`، مع Microsoft Graph: `openid` و`email` و`offline_access` و`profile`.
5. انسخ Application (client) ID وضعه في `microsoft_client_id`.
