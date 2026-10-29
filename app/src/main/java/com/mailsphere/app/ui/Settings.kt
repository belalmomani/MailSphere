package com.mailsphere.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.mailsphere.app.ads.AdsConfig

@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    onAddAccount: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenOrganize: () -> Unit
) {
    val isAr = LocalConfiguration.current.locales[0].language == "ar"
    val ctx = LocalContext.current
    val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    val adsText = when {
        !AdsConfig.ADS_ENABLED -> tr("غير مفعّلة", "Disabled")
        AdsConfig.TEST_MODE -> tr("مفعّلة — إعلانات تجريبية (حد ${AdsConfig.MAX_IMPRESSIONS_PER_DAY} يوميًا)", "Enabled — TEST ads (max ${AdsConfig.MAX_IMPRESSIONS_PER_DAY}/day)")
        else -> tr("مفعّلة — حد ${AdsConfig.MAX_IMPRESSIONS_PER_DAY} يوميًا", "Enabled — max ${AdsConfig.MAX_IMPRESSIONS_PER_DAY}/day")
    }

    Column(Modifier.fillMaxSize().screenInsets().verticalScroll(rememberScrollState())) {
        ScreenHeader(tr("الإعدادات", "Settings"))

        SectionLabel(tr("الحسابات والأدوات", "Accounts & tools"))
        SettingRow(Icons.Filled.Add, tr("إضافة حساب بريد", "Add email account"), tr("غير متاح بعد (واجهة تجريبية)", "Not available yet (demo UI)"), onAddAccount)
        SettingRow(Icons.Filled.Search, tr("البحث المتقدم", "Advanced search"), null, onOpenSearch)
        SettingRow(Icons.Filled.AutoAwesome, tr("رتّب بريدي بالكامل", "Organize my whole mail"), tr("محاكاة تجريبية", "Simulation"), onOpenOrganize)

        SectionLabel(tr("اللغة", "Language"))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = isAr, onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar")) }, label = { Text("العربية") })
            FilterChip(selected = !isAr, onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }, label = { Text("English") })
        }

        SectionLabel(tr("المظهر", "Appearance"))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tr("النظام", "System"), tr("فاتح", "Light"), tr("داكن", "Dark")).forEachIndexed { i, label ->
                FilterChip(selected = themeMode == i, onClick = { onThemeMode(i) }, label = { Text(label) })
            }
        }

        SectionLabel(tr("الخصوصية والإعلانات", "Privacy & ads"))
        SettingRow(Icons.Filled.Lock, tr("الخصوصية", "Privacy"),
            tr("لا يقرأ التطبيق بريدك بعد. حزمة الإعلانات (Start.io) قد تستخدم معرّف الإعلانات.", "The app does not read your mail yet. The ads SDK (Start.io) may use the advertising ID."), null)
        SettingRow(Icons.Filled.Campaign, tr("الإعلانات", "Ads"), adsText, null)

        SectionLabel(tr("حول", "About"))
        SettingRow(Icons.Filled.Info, "MailSphere / ميل سفير", tr("الإصدار $version", "Version $version"), null)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
