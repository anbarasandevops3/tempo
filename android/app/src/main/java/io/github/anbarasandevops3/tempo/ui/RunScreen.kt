package io.github.anbarasandevops3.tempo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.anbarasandevops3.tempo.R
import io.github.anbarasandevops3.tempo.model.Timeline
import io.github.anbarasandevops3.tempo.model.Workout
import io.github.anbarasandevops3.tempo.model.compile
import io.github.anbarasandevops3.tempo.model.formatClock
import io.github.anbarasandevops3.tempo.service.Phase
import io.github.anbarasandevops3.tempo.service.RunUi
import io.github.anbarasandevops3.tempo.service.WorkoutService
import kotlin.math.floor
import kotlin.math.min

/**
 * The run screen. With [run] == null it is the "ready" view (Start / Resume button); otherwise it
 * mirrors the service's state. All controls go to WorkoutService.
 */
@Composable
fun RunScreen(
    workout: Workout,
    timeline: Timeline?,
    run: RunUi?,
    resumeAt: Double? = null,
    onStart: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val tl = timeline ?: remember(workout) { compile(workout) }
    val pos = run?.position ?: resumeAt ?: 0.0
    val phase = run?.phase
    var keepAwake by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(keepAwake, phase) {
        view.keepScreenOn = keepAwake && (phase == Phase.PLAYING || phase == Phase.PAUSED)
        onDispose { view.keepScreenOn = false }
    }

    if (phase == Phase.DONE) {
        Column(
            Modifier.fillMaxSize().background(Palette.done).statusBarsPadding().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Done!", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(formatClock(tl.total), fontSize = 96.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(workout.name, fontSize = 20.sp, color = Color.White)
            }
            BigButton("Finish", onClick = onExit)
        }
        return
    }

    val seg = tl.segmentAt(pos)
    val next = seg?.let { tl.segments.getOrNull(it.index + 1) }
    val segLeft = seg?.let { it.end - pos } ?: 0.0
    val progress = seg?.let { if (it.duration > 0) ((pos - it.start) / it.duration).toFloat().coerceIn(0f, 1f) else 0f } ?: 0f
    val bg by animateColorAsState(kindColor(seg?.kind), tween(350), label = "bg")

    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (run != null && phase != Phase.ERROR) confirmStop = true else onExit() }) {
                Text("✕ Stop", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Text(workout.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Checkbox(checked = keepAwake, onCheckedChange = { keepAwake = it },
                colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black, uncheckedColor = Color.White))
            Text("Screen on", color = Color.White, fontSize = 14.sp)
        }

        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (seg?.rounds != null && seg.rounds > 1) {
                Text("Round ${seg.round} / ${seg.rounds}", color = Color.White.copy(alpha = .85f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(seg?.title ?: workout.name, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, lineHeight = 52.sp)
            Text(formatClock(segLeft), color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth(.8f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = .25f))) {
                Box(Modifier.fillMaxWidth(progress).height(8.dp).background(Color.White))
            }
            Text(next?.let { "Next: ${it.title} · ${formatClock(it.duration)}" } ?: "Last one!",
                color = Color.White.copy(alpha = .9f), fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${formatClock(floor(min(pos, tl.total.toDouble())))} elapsed", color = Color.White, fontSize = 17.sp)
                Text("${formatClock(tl.total - pos)} left", color = Color.White, fontSize = 17.sp)
            }
            when (phase) {
                null -> BigButton(if (resumeAt != null) "Resume" else "Start", onClick = onStart)
                Phase.PREPARING -> BigButton("Preparing audio…", enabled = false) {}
                Phase.ERROR -> {
                    Text(run.error ?: "Something went wrong", color = Color.White,
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .35f), RoundedCornerShape(10.dp)).padding(12.dp))
                    BigButton("Close", onClick = onExit)
                }
                Phase.PLAYING, Phase.PAUSED -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(R.drawable.ic_prev, "Previous step", 72) { WorkoutService.previous(context) }
                    RoundButton(if (phase == Phase.PLAYING) R.drawable.ic_pause else R.drawable.ic_play,
                        if (phase == Phase.PLAYING) "Pause" else "Play", 96, main = true) { WorkoutService.toggle(context) }
                    RoundButton(R.drawable.ic_next, "Next step", 72) { WorkoutService.next(context) }
                }
                Phase.DONE -> {}
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop this workout?") },
            text = { Text("It's saved in History with the time you've done.") },
            confirmButton = { TextButton(onClick = { confirmStop = false; WorkoutService.stop(context); onExit() }) { Text("Stop") } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .95f), contentColor = Color(0xFF111111),
            disabledContainerColor = Color.White.copy(alpha = .6f), disabledContentColor = Color(0xFF111111)),
    ) { Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun RoundButton(icon: Int, label: String, size: Int, main: Boolean = false, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size.dp).clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (main) Color.White else Color.Black.copy(alpha = .2f),
            contentColor = if (main) Color(0xFF111111) else Color.White,
        ),
    ) { Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size((size * 0.42).dp)) }
}
