package com.cutcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cutcoach.data.db.TrainingRepository
import com.cutcoach.data.protocol.ProtocolLoad
import com.cutcoach.data.protocol.ProtocolRepository
import com.cutcoach.data.settings.SettingsRepository
import com.cutcoach.ui.body.BodyScreen
import com.cutcoach.ui.exercise.CueCardScreen

import com.cutcoach.ui.exercise.LadderScreen
import com.cutcoach.health.DataCheckScreen
import com.cutcoach.ui.figure.FormCheckScreen
import com.cutcoach.ui.history.ExerciseHistoryScreen
import com.cutcoach.ui.history.HistoryScreen
import com.cutcoach.ui.session.SessionPlayerScreen
import com.cutcoach.ui.session.SessionPlayerViewModel
import com.cutcoach.ui.theme.CutCoachTheme
import com.cutcoach.ui.theme.Mist
import com.cutcoach.ui.theme.MistDim
import com.cutcoach.ui.theme.SlateEdge
import com.cutcoach.ui.settings.SettingsScreen
import com.cutcoach.ui.theme.SunriseAmber
import com.cutcoach.ui.today.TodayScreen
import com.cutcoach.ui.week.WeekScreen
import java.time.LocalDate
import java.time.LocalTime

object Routes {
    const val TODAY = "today"
    const val WEEK = "week"
    const val HISTORY = "history"
    const val BODY = "body"
    const val SETTINGS = "settings"
    const val SESSION = "session/{sessionId}"
    const val CUE_CARD = "cue/{exerciseId}"
    const val EXERCISE_HISTORY = "exerciseHistory/{exerciseId}"
    const val FIGURES = "figures"
    const val DATA_CHECK = "dataCheck"
    const val LADDERS = "ladders"

    fun session(id: String) = "session/$id"
    fun cueCard(id: String) = "cue/$id"
    fun exerciseHistory(id: String) = "exerciseHistory/$id"
}

/** The tabs in the bottom bar, in order. */
private val TABS = listOf(
    Routes.TODAY to "Today",
    Routes.WEEK to "Week",
    Routes.HISTORY to "History",
    Routes.BODY to "Body",
    Routes.SETTINGS to "Settings",
)

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
    val context = LocalContext.current

    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // The player and the cue card take the whole screen: mid-set is no time to
    // be offered a tab bar.
    val showTabs = route in TABS.map { it.first }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            NavGraph(navController = navController, repo = repo, context = context)
        }
        if (showTabs) {
            BottomTabs(current = route, onSelect = { navController.switchTab(it) })
        }
    }
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    repo: ProtocolRepository,
    context: android.content.Context,
) {
    NavHost(navController = navController, startDestination = Routes.TODAY) {

        composable(Routes.TODAY) {
            val today = LocalDate.now()
            val settings = remember { SettingsRepository.get(context) }
            val rehab by settings.rehabExercises.collectAsStateWithLifecycle(emptyList())

            // Read on opening rather than held in a cache, because the watch
            // syncs on its own schedule and a stale number here is worse than
            // a moment's wait.
            val health = produceState<com.cutcoach.health.DailyHealth?>(null, today) {
                value = com.cutcoach.health.readTodayAndLastNight(context, repo, today).first
            }
            val lastNight = produceState<com.cutcoach.health.DailyHealth?>(null, today) {
                value = com.cutcoach.health.readTodayAndLastNight(context, repo, today).second
            }

            // Computed after the watch read, so the advice sees the same data
            // the cards above it show rather than a moment-older copy.
            val advice = produceState(
                initialValue = emptyList<com.cutcoach.rules.Recommendation>(),
                today,
                health.value,
                lastNight.value,
            ) {
                value = com.cutcoach.rules.adviceForToday(
                    context = context,
                    repo = repo,
                    today = today,
                    todayHealth = health.value,
                    lastNight = lastNight.value,
                )
            }

            TodayScreen(
                repo = repo,
                today = today,
                now = LocalTime.now(),
                rehabExercises = rehab,
                health = health.value,
                lastNight = lastNight.value,
                advice = advice.value,
                onStartSession = {
                    repo.scheduleFor(today.dayOfWeek)?.let {
                        navController.navigate(Routes.session(it.sessionId))
                    }
                },
            )
        }

        composable(Routes.WEEK) {
            WeekScreen(repo = repo, today = LocalDate.now())
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                repo = repo,
                onOpenFigures = { navController.navigate(Routes.FIGURES) },
                onOpenDataCheck = { navController.navigate(Routes.DATA_CHECK) },
                onOpenLadders = { navController.navigate(Routes.LADDERS) },
            )
        }

        composable(Routes.FIGURES) {
            FormCheckScreen(repo = repo, onBack = { navController.popBackStack() })
        }

        composable(Routes.DATA_CHECK) {
            DataCheckScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.LADDERS) {
            LadderScreen(repo = repo, onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                repo = repo,
                onOpenExercise = { navController.navigate(Routes.exerciseHistory(it)) },
            )
        }

        composable(Routes.BODY) {
            BodyScreen(repo = repo, today = LocalDate.now())
        }

        composable(
            route = Routes.SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val programWeek = repo.programWeek(LocalDate.now())
            val training = remember { TrainingRepository.get(context) }
            val settings = remember { SettingsRepository.get(context) }

            // Scoped to this back-stack entry, so opening a cue card mid-set and
            // coming back keeps the timer running and the log intact.
            val vm: SessionPlayerViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = viewModelFactory {
                    initializer {
                        SessionPlayerViewModel(repo, training, settings, sessionId, programWeek)
                    }
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
                val settings = remember { SettingsRepository.get(context) }
                val override by settings.videoUrl(exerciseId).collectAsStateWithLifecycle("")
                val segment by settings.videoSegment(exerciseId)
                    .collectAsStateWithLifecycle(null)

                val training = remember { TrainingRepository.get(context) }
                val scope = rememberCoroutineScope()
                val step by training.ladderStepFlow(exerciseId)
                    .collectAsStateWithLifecycle(0)

                CueCardScreen(
                    exercise = exercise,
                    currentLadderStep = step,
                    onLadderStepChange = { index ->
                        scope.launch {
                            training.setLadderStep(exerciseId, index, exercise.ladder.size)
                        }
                    },
                    videoUrl = override.ifBlank { exercise.videoUrl },
                    videoStartS = segment?.first ?: exercise.videoStartS,
                    videoEndS = segment?.second ?: exercise.videoEndS,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Routes.EXERCISE_HISTORY,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) { entry ->
            ExerciseHistoryScreen(
                repo = repo,
                exerciseId = entry.arguments?.getString("exerciseId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Switching tabs should never build a back stack of tabs. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.TODAY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Words, not icons. At 05:35 a labelled target is faster to hit than a glyph
 * you have to decode, and it keeps the app free of an icon dependency.
 */
@Composable
private fun BottomTabs(current: String?, onSelect: (String) -> Unit) {
    Column {
        HorizontalDivider(color = SlateEdge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { (route, label) ->
                val selected = route == current
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) SunriseAmber else MistDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { if (!selected) onSelect(route) }
                        .padding(vertical = 20.dp),
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
