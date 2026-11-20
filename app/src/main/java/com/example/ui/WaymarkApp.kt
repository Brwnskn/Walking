package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.TripDetailScreen
import kotlinx.serialization.Serializable

@Serializable
object TodayRoute

@Serializable
object HistoryRoute

@Serializable
object SettingsRoute

@Serializable
data class TripDetailRoute(val tripId: Long)

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val topDestinations = listOf(
    TopLevelDestination(
        route = TodayRoute,
        labelRes = R.string.nav_today,
        selectedIcon = Icons.Filled.Route,
        unselectedIcon = Icons.Outlined.Route,
        testTag = "nav_today",
    ),
    TopLevelDestination(
        route = HistoryRoute,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history",
    ),
    TopLevelDestination(
        route = SettingsRoute,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings",
    ),
)

@Composable
fun WaymarkApp(
    viewModel: WaymarkViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val context = LocalContext.current

    val showTopLevelChrome = currentDestination?.hasRoute<TripDetailRoute>() != true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showTopLevelChrome && !useNavigationRail) {
                    NavigationBar {
                        topDestinations.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showTopLevelChrome && useNavigationRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        topDestinations.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = TodayRoute,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 780.dp),
                    ) {
                        composable<TodayRoute> {
                            TodayScreen(
                                uiState = uiState,
                                onStartTracking = { viewModel.startTracking(context) },
                                onStopTracking = { viewModel.stopTracking(context) },
                                onFinishLegNow = { viewModel.finishCurrentLegNow() },
                                onToggleUnit = { viewModel.setDistanceUnit(it) },
                                onTripClick = { tripId ->
                                    navController.navigate(TripDetailRoute(tripId))
                                },
                            )
                        }

                        composable<HistoryRoute> {
                            HistoryScreen(
                                uiState = uiState,
                                onSelectDate = { viewModel.selectHistoryDate(it) },
                                onTripClick = { tripId ->
                                    navController.navigate(TripDetailRoute(tripId))
                                },
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                preferences = uiState.preferences,
                                onSelectUnit = { viewModel.setDistanceUnit(it) },
                                onSelectDwellMinutes = { viewModel.setDwellThresholdMinutes(it) },
                                onClearHistory = { viewModel.clearAllHistory() },
                            )
                        }

                        composable<TripDetailRoute> { entry ->
                            val args = entry.toRoute<TripDetailRoute>()
                            val trip by viewModel.observeTrip(args.tripId)
                                .collectAsStateWithLifecycle(initialValue = null)
                            TripDetailScreen(
                                trip = trip,
                                unit = uiState.preferences.distanceUnit,
                                onBack = { navController.popBackStack() },
                                onRenameLabels = { origin, dest ->
                                    viewModel.updateTripLabels(args.tripId, origin, dest)
                                },
                                onDeleteTrip = {
                                    viewModel.deleteTrip(args.tripId)
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
