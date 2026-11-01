import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: read from keystore.properties (git-ignored; created by CI from GitHub Secrets).
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = ksProps.containsKey("storeFile")

android {
    namespace = "com.mailsphere.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mailsphere.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.0"
        resourceConfigurations += listOf("ar", "en")
        // OAuth redirect scheme used by AppAuth (Google + Microsoft sign-in)
        manifestPlaceholders["appAuthRedirectScheme"] = "com.mailsphere.app"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(ksProps.getProperty("storeFile"))
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No release key available -> fall back to the debug key so the APK is still installable for testing.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/NOTICE", "META-INF/LICENSE", "META-INF/DEPENDENCIES")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    // Start.io ads. Pinned: 5.4.0 requires a newer AGP/compileSdk than this project uses.
    implementation("com.startapp:inapp-sdk:5.3.1")
    // Phase 3: IMAP mail access + encrypted credential storage
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Phase 4: OAuth sign-in (Google / Microsoft)
    implementation("net.openid:appauth:0.11.1")
}
