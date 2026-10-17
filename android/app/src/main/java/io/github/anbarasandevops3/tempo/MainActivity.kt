package io.github.anbarasandevops3.tempo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import io.github.anbarasandevops3.tempo.model.Workout
import io.github.anbarasandevops3.tempo.service.WorkoutService
import io.github.anbarasandevops3.tempo.store.Store
import io.github.anbarasandevops3.tempo.ui.EditorScreen
import io.github.anbarasandevops3.tempo.ui.HistoryScreen
import io.github.anbarasandevops3.tempo.ui.HomeScreen
import io.github.anbarasandevops3.tempo.ui.Palette
import io.github.anbarasandevops3.tempo.ui.RunScreen
import io.github.anbarasandevops3.tempo.ui.TempoTheme

sealed interface Screen {
    data object Home : Screen
    data object History : Screen
    data class Edit(val workout: Workout, val isNew: Boolean) : Screen
    /** The run screen before Start (and for Resume). */
    data class Ready(val workoutId: String, val resumeAt: Double? = null, val entryId: String? = null) : Screen
}

class MainActivity : ComponentActivity() {

    private var pendingStart: (() -> Unit)? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Start either way: without the permission the workout still plays, just without the notification.
        pendingStart?.invoke()
        pendingStart = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        setContent { TempoTheme { App() } }
    }

    /** Asks for the notification permission once (Android 13+), then starts. */
    fun startWorkout(workoutId: String, resumeAt: Double?, entryId: String?) {
        val go = { WorkoutService.start(this, workoutId, resumeAt, entryId) }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingStart = go
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else go()
    }

    @Composable
    private fun App() {
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        val run by WorkoutService.state.collectAsState()
        val home = { screen = Screen.Home }

        Box(Modifier.fillMaxSize().background(Palette.bg)) {
            val r = run
            if (r != null) {
                // A workout is running (or just finished): the run screen owns the display.
                RunScreen(
                    workout = r.workout, timeline = r.timeline, run = r,
                    onStart = {}, onExit = { WorkoutService.dismiss(); home() },
                )
                return@Box
            }
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    onStart = { screen = Screen.Ready(it.id) },
                    onEdit = { screen = Screen.Edit(it, false) },
                    onCreate = { screen = Screen.Edit(it, true) },
                    onResume = { session -> screen = Screen.Ready(session.workoutId, session.position, session.entryId) },
                    onHistory = { screen = Screen.History },
                )
                Screen.History -> {
                    BackHandler(onBack = home)
                    HistoryScreen(liveEntryId = Store.loadSession()?.entryId, onBack = home)
                }
                is Screen.Edit -> {
                    BackHandler(onBack = home)
                    EditorScreen(
                        initial = s.workout, isNew = s.isNew,
                        onSave = { Store.saveWorkout(it); home() },
                        onDelete = { Store.deleteWorkout(s.workout.id); home() },
                        onCancel = home,
                    )
                }
                is Screen.Ready -> {
                    BackHandler(onBack = home)
                    val w = Store.workout(s.workoutId)
                    if (w == null) home() else RunScreen(
                        workout = w, timeline = null, run = null, resumeAt = s.resumeAt,
                        onStart = { startWorkout(w.id, s.resumeAt, s.entryId) },
                        onExit = home,
                    )
                }
            }
        }
    }
}
