package com.cutcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.ui.exercise.CueCardScreen
import com.cutcoach.ui.session.SessionPlayerScreen
import com.cutcoach.ui.session.SessionPlayerViewModel
import com.cutcoach.ui.theme.CutCoachTheme
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.today.TodayScreen
import java.time.LocalDate
import java.time.LocalTime

private object Routes {
    const val TODAY = "today"
    const val SESSION = "session/{sessionId}"
    const val CUE_CARD = "cue/{exerciseId}"

    fun session(id: String) = "session/$id"
    fun cueCard(id: String) = "cue/$id"
}

/**
 * The root of the app. It reads the protocol once, then routes between screens.
 *
 * `safeDrawingPadding` keeps content clear of the status bar and the gesture
 * area, so every screen inherits the fix rather than repeating it.
 */
@Composable
fun CutCoachApp() {
    val context = LocalContext.current
    val load = remember { ProtocolRepository.load(context) }

    CutCoachTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.safeDrawingPadding()) {
                when (load) {
                    is ProtocolLoad.Success -> MainNavigation(load.repository)
                    is ProtocolLoad.Failure -> ProtocolError(load)
                }
            }
        }
    }
}

@Composable
private fun MainNavigation(repo: ProtocolRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.TODAY) {

        composable(Routes.TODAY) {
            val today = LocalDate.now()
            TodayScreen(
                repo = repo,
                today = today,
                now = LocalTime.now(),
                onStartSession = {
                    repo.scheduleFor(today.dayOfWeek)?.let {
                        navController.navigate(Routes.session(it.sessionId))
                    }
                },
            )
        }

        composable(
            route = Routes.SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val programWeek = repo.programWeek(LocalDate.now())

            // Scoped to this back-stack entry, so opening a cue card mid-set and
            // coming back keeps the timer running and the log intact.
            val vm: SessionPlayerViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = viewModelFactory {
                    initializer { SessionPlayerViewModel(repo, sessionId, programWeek) }
                },
            )

            SessionPlayerScreen(
                vm = vm,
                onExit = { navController.popBackStack(Routes.TODAY, inclusive = false) },
                onOpenCueCard = { navController.navigate(Routes.cueCard(it)) },
            )
        }

        composable(
            route = Routes.CUE_CARD,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) { entry ->
            val exerciseId = entry.arguments?.getString("exerciseId").orEmpty()
            val exercise = repo.exercise(exerciseId)

            if (exercise == null) {
                MissingExercise(exerciseId)
            } else {
                CueCardScreen(
                    exercise = exercise,
                    // Until progression is stored, everyone starts on rung one.
                    // Phase 3 replaces this with the exercise's real position.
                    currentLadderStep = 0,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun MissingExercise(id: String) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Unknown exercise", style = MaterialTheme.typography.headlineSmall, color = SunriseAmber)
        Text(
            "\"$id\" is scheduled in protocol.json but has no entry in exercises.json.",
            style = MaterialTheme.typography.bodyMedium,
            color = Mist,
        )
    }
}

/**
 * Shown when protocol.json or exercises.json cannot be read. The app is useless
 * without them, so this says which file and why rather than crashing.
 */
@Composable
private fun ProtocolError(failure: ProtocolLoad.Failure) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "The protocol did not load",
            style = MaterialTheme.typography.headlineMedium,
            color = SunriseAmber,
        )
        Text(failure.message, style = MaterialTheme.typography.bodyMedium, color = Mist)
        Text(
            "Fix the file in app/src/main/assets and run the app again.",
            style = MaterialTheme.typography.bodySmall,
            color = MistDim,
        )
    }
}
