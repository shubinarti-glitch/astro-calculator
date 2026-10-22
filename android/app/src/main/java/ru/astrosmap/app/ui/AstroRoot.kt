package ru.astrosmap.app.ui

import ru.astrosmap.app.ui.assistant.AssistantUiState
import androidx.compose.runtime.rememberUpdatedState

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    Today("today", R.string.section_today, R.drawable.ic_today),
    Chart("chart", R.string.section_chart, R.drawable.ic_chart),
    Saved("saved", R.string.nav_cards_short, R.drawable.ic_saved),
    Tools("tools", R.string.section_tools, R.drawable.ic_tools),
    Account("account", R.string.section_account, R.drawable.ic_account),
}

@Composable
fun AstroRoot(notificationRoute: String? = null, onNotificationRouteHandled: () -> Unit = {}) {
    val session = androidx.hilt.navigation.compose.hiltViewModel<ru.astrosmap.app.ui.journal.AdviceNavigationViewModel>()
    val owner by session.owners.collectAsState()
    // A fresh graph discards old account ViewModels and saved report back stacks.
    key(owner) { AstroSessionRoot(notificationRoute, onNotificationRouteHandled) }
}

@Composable
private fun AstroSessionRoot(notificationRoute: String?, onNotificationRouteHandled: () -> Unit) {
    val navController = rememberNavController()
    val adviceNavigation = androidx.hilt.navigation.compose.hiltViewModel<ru.astrosmap.app.ui.journal.AdviceNavigationViewModel>()
    val adviceDraft by adviceNavigation.draft.collectAsState()
    val sessionOwner by adviceNavigation.owners.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val assistantState = remember(backStack?.id) { mutableStateOf<AssistantUiState?>(null) }
    val activeAssistantEntry = rememberUpdatedState(backStack?.id)
    var hintedIndex by remember { mutableIntStateOf(-1) }
    val compactLabels = LocalDensity.current.fontScale >= 1.3f

    NotificationOptInPrompt(eligible = currentRoute == Section.Today.route)

    LaunchedEffect(notificationRoute) {
        val route = notificationRoute ?: return@LaunchedEffect
        if (route !in setOf("today", "tarot", "luncal")) {
            onNotificationRouteHandled()
            return@LaunchedEffect
        }
        navController.currentBackStackEntryFlow.first()
        navController.navigate(route) { launchSingleTop = true }
        onNotificationRouteHandled()
    }

    // Повторяющаяся подсказка показывает, что нижняя панель интерактивна.
    LaunchedEffect(Unit) {
        delay(3_000)
        var index = 0
        while (true) {
            hintedIndex = index
            delay(850)
            hintedIndex = -1
            index = (index + 1) % Section.entries.size
            delay(9_150)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                Section.entries.forEach { section ->
                    val index = Section.entries.indexOf(section)
                    val pulse by animateFloatAsState(
                        targetValue = if (hintedIndex == index) 1f else 0f,
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        label = "bottom_nav_hint_${section.route}",
                    )
                    NavigationBarItem(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        selected = currentRoute == section.route,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        alwaysShowLabel = !compactLabels,
                        onClick = {
                            navController.navigate(section.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val defaultTint = LocalContentColor.current
                            Box(
                                Modifier
                                    .size(width = 48.dp, height = 36.dp)
                                    .graphicsLayer {
                                        scaleX = 1f + pulse * 0.16f
                                        scaleY = scaleX
                                    }
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = if (currentRoute == section.route) 0.13f else pulse * 0.12f,
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(section.iconRes),
                                    contentDescription = stringResource(section.titleRes),
                                    tint = if (pulse > 0.01f) MaterialTheme.colorScheme.primary
                                    else defaultTint,
                                )
                            }
                        },
                        label = {
                            // Двухстрочные подписи допускаем осознанно — иначе длинные обрезаются.
                            Text(
                                stringResource(section.titleRes),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            ru.astrosmap.app.ui.theme.StarryBackground()
            NavHost(
                navController = navController,
                startDestination = Section.Today.route,
                modifier = Modifier.fillMaxSize(),
            ) {
            composable(Section.Today.route) {
                ru.astrosmap.app.ui.today.TodayScreen(
                    onCreateChart = {
                        navController.navigate(Section.Chart.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Section.Chart.route) { entry ->
                val publishAssistant = remember(assistantState, entry.id) {
                    { state: AssistantUiState? -> if (activeAssistantEntry.value == entry.id) assistantState.value = state }
                }
                ChartFormScreen(onCalculated = { navController.navigate("view/0") },
                    onAssistantState = publishAssistant)
            }
            composable(Section.Saved.route) {
                SavedScreen(onOpen = { id -> navController.navigate("view/$id") })
            }
            composable(Section.Account.route) {
                ru.astrosmap.app.ui.account.AccountScreen(onMaterials = { navController.navigate("materials") })
            }
            composable("materials") { ru.astrosmap.app.ui.saved.SavedMaterialsScreen() }
            composable("tarot") { ru.astrosmap.app.ui.tarot.TarotScreen() }
            composable(Section.Tools.route) {
                ru.astrosmap.app.ui.tools.ToolsScreen(
                    onTransits = { id -> navController.navigate("transit/$id") },
                    onProgression = { id -> navController.navigate("progression/$id") },
                    onForecast = { id -> navController.navigate("forecast/$id") { launchSingleTop = true } },
                    onSolar = { id -> navController.navigate("return/solar/$id") },
                    onLunar = { id -> navController.navigate("return/lunar/$id") },
                    onSynastry = { a, b -> navController.navigate("synastry/$a/$b") },
                    onLunarCalendar = { navController.navigate("luncal") },
                    onTarot = { navController.navigate("tarot") },
                    onJournal = { navController.navigate("journal") },
                )
            }
            composable("luncal") { ru.astrosmap.app.ui.tools.LunarCalendarScreen() }
            composable("journal") { entry ->
                val publishAssistant = remember(assistantState, entry.id) {
                    { state: AssistantUiState? -> if (activeAssistantEntry.value == entry.id) assistantState.value = state }
                }
                ru.astrosmap.app.ui.journal.JournalScreen(adviceDraft = adviceDraft, onAdviceConsumed = adviceNavigation::clear,
                    onAssistantState = publishAssistant)
            }
            composable("transit/{id}") { ru.astrosmap.app.ui.tools.TransitScreen() }
            composable("progression/{id}") { ru.astrosmap.app.ui.tools.ProgressionScreen() }
            composable("forecast/{id}") { entry ->
                val publishAssistant = remember(assistantState, entry.id) {
                    { state: AssistantUiState? -> if (activeAssistantEntry.value == entry.id) assistantState.value = state }
                }
                ru.astrosmap.app.ui.tools.ForecastScreen(
                    onAssistantState = publishAssistant,
                    onOpenJournal = { navController.navigate("journal") { launchSingleTop = true } },
                    onOpenAccount = { navController.navigate(Section.Account.route) { launchSingleTop = true } },
                    onJournalAdvice = { date, advice, source ->
                    adviceNavigation.prepare(date, advice, sessionOwner, source) { navController.navigate("journal") { launchSingleTop = true } }
                }, onSelectChart = {
                    navController.navigate(Section.Saved.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable("return/{type}/{id}") { ru.astrosmap.app.ui.tools.ReturnScreen() }
            composable("synastry/{a}/{b}") { ru.astrosmap.app.ui.tools.SynastryScreen() }
            composable("view/{id}") { entry ->
                val publishAssistant = remember(assistantState, entry.id) {
                    { state: AssistantUiState? -> if (activeAssistantEntry.value == entry.id) assistantState.value = state }
                }
                ChartViewScreen(
                    onAssistantState = publishAssistant,
                    onEdit = {
                        navController.navigate(Section.Chart.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    onClosed = { navController.popBackStack() },
                    onForecast = { id -> navController.navigate("forecast/$id") { launchSingleTop = true } },
                )
            }
            }
            ru.astrosmap.app.ui.assistant.FloatingAssistant(currentRoute, uiState = assistantState.value, onNavigate = { destination ->
                navController.navigate(destination) { launchSingleTop = true }
            })
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
