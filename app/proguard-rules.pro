# JavaMail (IMAP)
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.security.sasl.**
-dontwarn javax.naming.**
-dontwarn com.sun.mail.**
-dontwarn org.apache.harmony.**
# security-crypto / Tink
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
