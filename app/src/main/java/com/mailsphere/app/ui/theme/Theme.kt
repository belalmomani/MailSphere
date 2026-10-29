package com.mailsphere.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Blue = Color(0xFF1F6FEB)
val BrandGradient = Brush.verticalGradient(listOf(Color(0xFF0E1B4D), Color(0xFF2E2A8F), Color(0xFF4B3BB5)))

private val LightScheme = lightColorScheme(
    primary = Blue, onPrimary = Color.White,
    background = Color(0xFFF6F8FC), surface = Color.White, onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF2F9), onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFDDE3EE), error = Color(0xFFE5393B)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5B9BFF), onPrimary = Color(0xFF00133A),
    background = Color(0xFF0B1020), surface = Color(0xFF131A2E), onSurface = Color(0xFFE8ECF6),
    surfaceVariant = Color(0xFF1B2440), onSurfaceVariant = Color(0xFF9AA5BF),
    outline = Color(0xFF2A3558), error = Color(0xFFFF6B6B)
)

@Composable
fun MailSphereTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}
