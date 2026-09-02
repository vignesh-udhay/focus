package com.vignesh.focuslist.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vignesh.focuslist.FocuslistApplication
import com.vignesh.focuslist.core.design.LocalContentWidth
import com.vignesh.focuslist.core.design.focuslistUsesNavigationRail
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.ui.focus.FocusScreen
import com.vignesh.focuslist.ui.inbox.InboxScreen
import com.vignesh.focuslist.ui.logbook.LogbookScreen
import com.vignesh.focuslist.ui.placement.PlacementScreen
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.today.TodayScreen
import com.vignesh.focuslist.ui.upcoming.UpcomingScreen

/**
 * The navigation graph.
 *
 * Every screen sits directly in it and every screen carries the bar, so any
 * destination is one or two taps from any other. Nothing has to be reached by
 * going back to Today first.
 *
 * Back is the navigation back stack's own, not a hand-written one: back from a
 * secondary destination returns to whichever list it was opened from, back
 * from Inbox returns to Today, and back from Today leaves the app.
 */
@Composable
fun FocuslistNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    // Obtained here, above the graph, and handed to every screen. Inside a
    // destination the view model store owner is that destination's back stack
    // entry, which would give each list its own view model and split the undo
    // offer five ways.
    val viewModel = taskListViewModel()

    // A running Focus session takes the navigation away in both presentations.
    // The bar is handed to the screens and could have been withheld inside
    // one, but the rail is a sibling of the whole graph, so the decision has
    // to be made out here for the two to behave alike.
    val isFocusSession by viewModel.isFocusSessionActive.collectAsStateWithLifecycle()

    // One navigation model, two presentations. Which one is on screen is the
    // only thing this decides; the graph, the destinations and the back stack
    // are identical either way.
    val usesRail = focuslistUsesNavigationRail()

    // Screens take their navigation as a bottom bar. With a rail beside them
    // there is no bottom bar to give, so they are handed nothing and the rail
    // sits outside them, full height, as Material specifies.
    val navigationBar: @Composable () -> Unit = if (usesRail || isFocusSession) {
        {}
    } else {
        {
            FocuslistNavigationBar(
                currentRoute = currentRoute,
                onOpenTopLevel = navController::openTopLevel,
                onOpenSecondary = navController::openSecondary
            )
        }
    }

    val graph: @Composable (Modifier) -> Unit = { hostModifier ->
        NavHost(
            navController = navController,
            startDestination = FocuslistRoutes.TODAY,
            modifier = hostModifier
        ) {
            composable(FocuslistRoutes.TODAY) {
                TodayScreen(
                    viewModel = viewModel,
                    bottomBar = navigationBar,
                    // Only the move between destinations belongs here. Which task
                    // Focus lands on is the screen's own call, through the view
                    // model both of them already share.
                    onOpenFocus = { navController.openTopLevel(FocuslistRoutes.FOCUS) }
                )
            }

            composable(FocuslistRoutes.INBOX) {
                InboxScreen(viewModel = viewModel, bottomBar = navigationBar)
            }

            composable(FocuslistRoutes.FOCUS) {
                FocusScreen(viewModel = viewModel, bottomBar = navigationBar)
            }

            composable(FocuslistRoutes.UPCOMING) {
                UpcomingScreen(viewModel = viewModel, bottomBar = navigationBar)
            }

            // Two routes over one screen: Anytime and Someday are the same list
            // with one constant changed, and each opens on its own tab.
            composable(FocuslistRoutes.ANYTIME) {
                PlacementScreen(
                    viewModel = viewModel,
                    initialPlacement = TaskPlacement.ANYTIME,
                    bottomBar = navigationBar
                )
            }

            composable(FocuslistRoutes.SOMEDAY) {
                PlacementScreen(
                    viewModel = viewModel,
                    initialPlacement = TaskPlacement.SOMEDAY,
                    bottomBar = navigationBar
                )
            }

            composable(FocuslistRoutes.LOGBOOK) {
                LogbookScreen(viewModel = viewModel, bottomBar = navigationBar)
            }
        }
    }

    if (usesRail) {
        Row(modifier = modifier.fillMaxSize()) {
            if (!isFocusSession) {
                FocuslistNavigationRail(
                    currentRoute = currentRoute,
                    onOpenTopLevel = navController::openTopLevel,
                    onOpenSecondary = navController::openSecondary,
                    modifier = Modifier.fillMaxHeight()
                )
            }

            // The rail's width is chrome, not content. Measuring what is left
            // and publishing it means the content column centres inside the
            // area it actually has rather than inside the window, which would
            // push it off centre by half the rail.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalContentWidth provides maxWidth) {
                    graph(Modifier)
                }
            }
        }
    } else {
        graph(modifier)
    }
}

/**
 * Switches to one of the destinations the bar holds.
 *
 * Everything above Today is cleared first, so tapping around the bar never
 * grows the stack and back from a bar destination always means Today, then
 * out. Tapping the current destination again does nothing.
 *
 * Deliberately without `saveState`/`restoreState`. Those save the stack that
 * was popped and put it back on return, which is right for a bar whose items
 * each own a nested graph. This graph is flat and the secondary destinations
 * sit on top of it, so restoring would return the user to a secondary list
 * they had already left.
 */
private fun NavHostController.openTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

/**
 * Opens a destination from the More menu.
 *
 * An ordinary forward move, so back returns to the list it was opened from
 * rather than jumping to Today.
 */
private fun NavHostController.openSecondary(route: String) {
    navigate(route) { launchSingleTop = true }
}

/**
 * Builds the one [TaskListViewModel] the whole app reads.
 *
 * Private, and called only from [FocuslistNavHost], because where it is called
 * decides how many view models exist. `viewModel()` resolves against the
 * current `LocalViewModelStoreOwner`; here that is the Activity, but inside a
 * destination it is that destination's back stack entry, which would hand
 * every list its own view model and split the undo offer between them.
 *
 * Screens receive the result as a parameter and know nothing about this.
 */
@Composable
private fun taskListViewModel(): TaskListViewModel {
    val application = LocalContext.current.applicationContext as FocuslistApplication

    return viewModel(
        factory = TaskListViewModel.Factory(
            repository = application.taskRepository,
            currentDay = application.currentDay,
            alarms = application.focusAlarms
        )
    )
}
