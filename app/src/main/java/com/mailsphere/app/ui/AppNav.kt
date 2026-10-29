package com.mailsphere.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import com.mailsphere.app.ads.AdManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mailsphere.app.data.Bi

private data class Tab(val route: String, val icon: ImageVector, val label: Bi)

@Composable
fun MailSphereApp(themeMode: Int, onThemeMode: (Int) -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val tabs = listOf(
        Tab("inbox", Icons.Filled.Email, Bi("البريد", "Mail")),
        Tab("folders", Icons.Filled.Folder, Bi("المجلدات", "Folders")),
        Tab("vault", Icons.Filled.AttachFile, Bi("المرفقات", "Attachments")),
        Tab("settings", Icons.Filled.MoreHoriz, Bi("المزيد", "More"))
    )

    // Status/navigation bar icon colors: light icons over the dark gradient screens and in dark theme.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val onGradient = route == "splash" || route == "welcome"
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !dark && !onGradient
        c.isAppearanceLightNavigationBars = !dark && !onGradient
    }

    // Leaving a task screen = natural pause where an ad is allowed (limits are enforced in AdManager).
    val leaveTask = { nav.popBackStack(); AdManager.maybeShow() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo("inbox") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label.t()) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "splash", modifier = Modifier.padding(padding)) {
            composable("splash") {
                SplashScreen { nav.navigate("welcome") { popUpTo("splash") { inclusive = true } } }
            }
            composable("welcome") {
                WelcomeScreen(
                    canGoBack = nav.previousBackStackEntry != null,
                    onBack = { nav.popBackStack() },
                    onExplore = { nav.navigate("inbox") { popUpTo(nav.graph.id) { inclusive = true } } },
                    onAddAccount = { nav.navigate("addaccount") }
                )
            }
            composable("inbox") { InboxScreen(onOpenSearch = { nav.navigate("search") }) }
            composable("addaccount") {
                AddAccountScreen(
                    onBack = { nav.popBackStack() },
                    onDone = { nav.navigate("inbox") { popUpTo(nav.graph.id) { inclusive = true } } }
                )
            }
            composable("folders") { FoldersScreen() }
            composable("vault") { VaultScreen() }
            composable("search") {
                BackHandler(onBack = leaveTask)
                SearchScreen(onBack = leaveTask)
            }
            composable("organize") {
                BackHandler(onBack = leaveTask)
                OrganizeScreen(onBack = leaveTask)
            }
            composable("settings") {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    onAddAccount = { nav.navigate("addaccount") },
                    onOpenSearch = { nav.navigate("search") },
                    onOpenOrganize = { nav.navigate("organize") }
                )
            }
        }
    }
}
