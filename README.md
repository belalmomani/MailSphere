# MailSphere (ميل سفير)

تطبيق Android لإدارة البريد الإلكتروني — **المرحلة 1: الواجهات والبنية الأساسية فقط** (بيانات تجريبية، لا يوجد اتصال فعلي بالبريد).

- Package: `com.mailsphere.app` — Version: `1.0.0` (versionCode `1`)
- التقنية: Kotlin + Jetpack Compose (Material 3) + Navigation Compose، minSdk 24، targetSdk 35
- اللغات: العربية (RTL) والإنجليزية (LTR)، ويمكن تبديلها من الإعدادات

## بناء APK عبر GitHub Actions
1. ارفع محتويات هذا المجلد إلى جذر مستودعك (الفرع `main`).
2. افتح تبويب **Actions** ← **Build APK** (يعمل تلقائيًا عند كل push، أو اضغط Run workflow).
3. بعد النجاح نزّل الملف من **Artifacts ← MailSphere-APK** (فيه `app-release.apk` و`PROJECT_INFO.txt`).

## التوقيع (مهم للتحديثات دون حذف التطبيق)
- بدون أسرار: يُوقَّع الـAPK بمفتاح debug ويصلح للتجربة فقط، وقد يتغير مفتاح التوقيع بين البناءات فيمنع التحديث فوق النسخة المثبتة.
- لتوقيع ثابت: أنشئ مفتاحًا **مرة واحدة** واحتفظ به:
  `keytool -genkeypair -v -keystore mailsphere.jks -alias mailsphere -keyalg RSA -keysize 2048 -validity 10000`
  ثم حوّله: `base64 -w0 mailsphere.jks` وأضف في GitHub ← Settings ← Secrets and variables ← Actions:
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- لا ترفع ملف `.jks` ولا `keystore.properties` إلى المستودع (مُستبعدان في `.gitignore`).

## البناء محليًا
Android Studio (Koala أو أحدث) ← Open ← Sync ← Build APK. أو: `gradle assembleRelease` (Gradle 8.9 وJDK 17).

## الإعلانات (Start.io)
مدمجة (المرحلة 2). المعرّف في `res/values/ads.xml`. المفاتيح في `ads/AdFrequencyLimiter.kt` ← `AdsConfig`:
`ADS_ENABLED` (تفعيل/تعطيل)، `TEST_MODE` (إعلانات تجريبية؛ اجعلها `false` للإعلانات الحقيقية)، `MAX_IMPRESSIONS_PER_DAY` (3)، `MIN_INTERVAL_MS`.
لا تضغط على إعلاناتك الحقيقية أثناء الاختبار.
