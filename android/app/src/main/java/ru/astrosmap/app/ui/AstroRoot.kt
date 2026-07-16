package ru.astrosmap.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.form.ChartFormScreen
import ru.astrosmap.app.ui.saved.SavedScreen
import ru.astrosmap.app.ui.view.ChartViewScreen

/** Корневые разделы приложения (нижняя навигация). */
enum class Section(val route: String, val titleRes: Int, val iconRes: Int) {
    Chart("chart", R.string.section_chart, R.drawable.ic_chart),
    Saved("saved", R.string.section_saved, R.drawable.ic_saved),
    Tools("tools", R.string.section_tools, R.drawable.ic_tools),
    Account("account", R.string.section_account, R.drawable.ic_account),
}

@Composable
fun AstroRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Section.entries.forEach { section ->
                    NavigationBarItem(
                        selected = currentRoute == section.route,
                        onClick = {
                            navController.navigate(section.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(section.iconRes), contentDescription = null) },
                        label = { Text(stringResource(section.titleRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Section.Chart.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Section.Chart.route) {
                ChartFormScreen(onCalculated = { navController.navigate("view/0") })
            }
            composable(Section.Saved.route) {
                SavedScreen(onOpen = { id -> navController.navigate("view/$id") })
            }
            composable(Section.Account.route) { ru.astrosmap.app.ui.account.AccountScreen() }
            composable(Section.Tools.route) {
                ru.astrosmap.app.ui.tools.ToolsScreen(
                    onTransits = { id -> navController.navigate("transit/$id") },
                    onSolar = { id -> navController.navigate("solar/$id") },
                )
            }
            composable("transit/{id}") { ru.astrosmap.app.ui.tools.TransitScreen() }
            composable("solar/{id}") { ru.astrosmap.app.ui.tools.SolarScreen() }
            composable("view/{id}") {
                ChartViewScreen(
                    onEdit = {
                        navController.navigate(Section.Chart.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    onClosed = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Заглушка раздела — заменяется реальным экраном на своём этапе. */
@Composable
private fun PlaceholderScreen(titleRes: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
