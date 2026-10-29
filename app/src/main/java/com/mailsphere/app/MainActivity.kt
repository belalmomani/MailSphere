package com.mailsphere.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mailsphere.app.ads.AdManager
import com.mailsphere.app.ui.MailSphereApp
import com.mailsphere.app.ui.theme.MailSphereTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdManager.attach(this)
        val prefs = getSharedPreferences("mailsphere_prefs", MODE_PRIVATE)
        setContent {
            var themeMode by remember { mutableIntStateOf(prefs.getInt("theme_mode", 0)) }
            val dark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            MailSphereTheme(dark) {
                MailSphereApp(themeMode) {
                    themeMode = it
                    prefs.edit().putInt("theme_mode", it).apply()
                }
            }
        }
    }

    override fun onDestroy() {
        AdManager.detach(this)
        super.onDestroy()
    }
}
