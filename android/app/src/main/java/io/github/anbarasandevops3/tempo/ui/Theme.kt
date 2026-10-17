package io.github.anbarasandevops3.tempo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.anbarasandevops3.tempo.model.StepKind

// Same tokens as src/styles.css.
object Palette {
    val bg = Color(0xFF111318)
    val card = Color(0xFF1C1F26)
    val line = Color(0xFF2C303A)
    val text = Color(0xFFF2F3F5)
    val muted = Color(0xFF9AA0AC)
    val accent = Color(0xFFFF6B2C)
    val danger = Color(0xFFFF4D4F)
    val ready = Color(0xFF4B5160)
    val done = Color(0xFF23A55A)
}

fun kindColor(kind: StepKind?): Color = when (kind) {
    null -> Palette.ready
    StepKind.WARMUP -> Color(0xFFD98A00)
    StepKind.RUN -> Color(0xFFFF6B2C)
    StepKind.JOG -> Color(0xFFE0A100)
    StepKind.WALK -> Color(0xFF23A55A)
    StepKind.SPRINT, StepKind.WORK -> Color(0xFFE5243B)
    StepKind.REST -> Color(0xFF2F6FED)
    StepKind.RECOVER -> Color(0xFF13A3A3)
    StepKind.COOLDOWN -> Color(0xFF5B5BD6)
    StepKind.STRETCH -> Color(0xFF9B51E0)
}

@Composable
fun TempoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            onPrimary = Color(0xFF111111),
            background = Palette.bg,
            onBackground = Palette.text,
            surface = Palette.card,
            onSurface = Palette.text,
            surfaceVariant = Palette.card,
            onSurfaceVariant = Palette.muted,
            surfaceContainerHigh = Palette.card,
            outline = Palette.line,
            error = Palette.danger,
        ),
        content = content,
    )
}
