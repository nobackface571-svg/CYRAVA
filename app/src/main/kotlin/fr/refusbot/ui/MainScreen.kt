package fr.refusbot.ui

import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.refusbot.MainActivity
import fr.refusbot.R

enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    ACCUEIL(R.string.tab_home, R.drawable.ic_home),
    APPLICATIONS(R.string.tab_apps, R.drawable.ic_apps),
    HISTORIQUE(R.string.tab_history, R.drawable.ic_history),
    COMPATIBILITE(R.string.tab_compat, R.drawable.ic_phone),
    REGLAGES(R.string.tab_settings, R.drawable.ic_settings),
}

@Composable
fun MainScreen(requestedRoute: MutableState<String?>) {
    var tab by rememberSaveable { mutableStateOf(Tab.ACCUEIL) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.MENU) }
    LaunchedEffect(requestedRoute.value) {
        when (requestedRoute.value) {
            null -> return@LaunchedEffect
            MainActivity.TAB_HISTORY -> tab = Tab.HISTORIQUE
            MainActivity.TAB_DIAGNOSTIC -> { tab = Tab.REGLAGES; settingsPage = SettingsPage.DIAGNOSTIC }
            else -> tab = Tab.ACCUEIL
        }
        requestedRoute.value = null
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp).clip(RoundedCornerShape(24.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
            ) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            if (t == Tab.REGLAGES && tab == Tab.REGLAGES) settingsPage = SettingsPage.MENU
                            tab = t
                        },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(stringResource(t.label), style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding: PaddingValues ->
        when (tab) {
            Tab.ACCUEIL -> HomeScreen(padding,
                onOpenApps = { tab = Tab.APPLICATIONS },
                onOpenHistory = { tab = Tab.HISTORIQUE })
            Tab.APPLICATIONS -> AppsScreen(padding)
            Tab.HISTORIQUE -> HistoryScreen(padding)
            Tab.COMPATIBILITE -> CompatibilityScreen(padding, onOpenDiagnostic = {
                tab = Tab.REGLAGES; settingsPage = SettingsPage.DIAGNOSTIC
            })
            Tab.REGLAGES -> SettingsScreen(padding, settingsPage, onNavigate = { settingsPage = it })
        }
    }
}
