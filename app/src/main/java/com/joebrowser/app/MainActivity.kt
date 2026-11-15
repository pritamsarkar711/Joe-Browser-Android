package com.joebrowser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joebrowser.app.ui.AppViewModel
import com.joebrowser.app.ui.extensions.ExtensionsScreen
import com.joebrowser.app.ui.engines.EnginesScreen
import com.joebrowser.app.ui.history.HistoryScreen
import com.joebrowser.app.ui.profiles.ProfileEditorScreen
import com.joebrowser.app.ui.profiles.ProfilesScreen
import com.joebrowser.app.ui.proxy.ProxiesScreen
import com.joebrowser.app.ui.settings.SettingsScreen
import com.joebrowser.app.ui.theme.JoePalette
import com.joebrowser.app.ui.theme.JoeTheme
import com.joebrowser.app.ui.theme.palette

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: AppViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val dark = when (state.settings.themeMode) {
                1 -> true
                2 -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            JoeTheme(themeMode = state.settings.themeMode) {
                val p = palette(dark)
                JoeApp(model, state, p)
            }
        }
    }
}

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val DESTINATIONS = listOf(
    Destination("profiles", "Profiles", Icons.Outlined.Layers),
    Destination("proxies", "Proxies", Icons.Outlined.Public),
    Destination("extensions", "Extensions", Icons.Outlined.Extension),
    Destination("engines", "Engines", Icons.Outlined.TravelExplore),
    Destination("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
private fun JoeApp(model: AppViewModel, state: com.joebrowser.app.ui.AppState, p: JoePalette) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            snackbar.showSnackbar(state.message)
            model.clearMessage()
        }
    }

    val width = LocalConfiguration.current.screenWidthDp
    val wide = width >= 720
    // The editor and the history are full-screen destinations: the bar at the bottom
    // would only take space away from the thing the user came to look at.
    val fullScreen = current?.route?.startsWith("editor/") == true || current?.route == "history"

    Scaffold(
        containerColor = p.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!wide && !fullScreen) {
                NavigationBar(containerColor = p.bgSoft) {
                    DESTINATIONS.forEach { d ->
                        val selected = current?.hierarchy?.any { it.route == d.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(d.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(d.icon, contentDescription = d.label) },
                            label = { Text(d.label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = p.accent,
                                selectedTextColor = p.accentText,
                                indicatorColor = p.accentOnBg,
                                unselectedIconColor = p.textFaint,
                                unselectedTextColor = p.textFaint,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxSize()) {
                if (wide && !fullScreen) {
                    NavigationRail(containerColor = p.sidebar) {
                        DESTINATIONS.forEach { d ->
                            val selected = current?.hierarchy?.any { it.route == d.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(d.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(d.icon, contentDescription = d.label) },
                                label = { Text(d.label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = p.accent,
                                    selectedTextColor = p.accentText,
                                    indicatorColor = p.accentOnBg,
                                    unselectedIconColor = p.textFaint,
                                    unselectedTextColor = p.textFaint,
                                ),
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    NavHost(navController = navController, startDestination = "profiles") {
                        composable("profiles") {
                            ProfilesScreen(
                                state = state,
                                palette = p,
                                onOpen = { profileId ->
                                    navController.navigate("editor/$profileId")
                                },
                                onNew = { navController.navigate("editor/new") },
                                onHistory = { navController.navigate("history") },
                                onSearch = model::setSearch,
                                onSort = model::setSort,
                                onDelete = model::deleteProfile,
                                onDuplicate = model::duplicateProfile,
                                onLaunch = { profile ->
                                    model.noteLaunched(profile.id)
                                    BrowserLauncher.launch(context, profile)
                                },
                            )
                        }
                        composable("editor/{profileId}") { entry ->
                            val id = entry.arguments?.getString("profileId") ?: "new"
                            ProfileEditorScreen(
                                profileId = id,
                                state = state,
                                palette = p,
                                onBack = { navController.popBackStack() },
                                onSave = model::saveProfile,
                                onDelete = { profile ->
                                    model.deleteProfile(profile)
                                    navController.popBackStack()
                                },
                                onLaunch = { profile ->
                                    model.noteLaunched(profile.id)
                                    BrowserLauncher.launch(context, profile)
                                },
                                onCheckProxy = model::checkProxy,
                                onMessage = model::setMessage,
                            )
                        }
                        composable("proxies") {
                            ProxiesScreen(
                                state = state,
                                palette = p,
                                onSave = model::saveProxy,
                                onDelete = model::deleteProxy,
                                onCheck = model::checkProxy,
                                onCheckAll = model::checkAllProxies,
                            )
                        }
                        composable("extensions") {
                            ExtensionsScreen(
                                state = state,
                                palette = p,
                                onSave = model::saveExtension,
                                onDelete = model::deleteExtension,
                                onMessage = model::setMessage,
                            )
                        }
                        composable("engines") {
                            EnginesScreen(state = state, palette = p, onMessage = model::setMessage)
                        }
                        composable("settings") {
                            SettingsScreen(
                                state = state,
                                palette = p,
                                onSave = model::saveSettings,
                                onMessage = model::setMessage,
                                onClearHistory = { model.clearHistory() },
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                palette = p,
                                entries = model.history(),
                                onBack = { navController.popBackStack() },
                                onClear = { model.clearHistory() },
                            )
                        }
                    }
                }
            }
        }
    }
}
