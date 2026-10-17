package io.github.anbarasandevops3.tempo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.anbarasandevops3.tempo.model.EntryStatus
import io.github.anbarasandevops3.tempo.model.HistoryEntry
import io.github.anbarasandevops3.tempo.model.HistoryStats
import io.github.anbarasandevops3.tempo.model.effectiveStatus
import io.github.anbarasandevops3.tempo.model.formatClock
import io.github.anbarasandevops3.tempo.store.Store
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(liveEntryId: String?, onBack: () -> Unit) {
    val entries by Store.history.collectAsState()
    val stats = HistoryStats(entries)
    val today = LocalDate.now()
    val week = stats.weekStart(today)
    val thisWeek = stats.totals(week, week.plusWeeks(1))
    val lastWeek = stats.totals(week.minusWeeks(1), week)
    val streak = stats.streakDays(today)
    val weeks = stats.weeklyMinutes(today, 8)
    var month by remember { mutableStateOf(YearMonth.now()) }
    var deleting by remember { mutableStateOf<HistoryEntry?>(null) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Palette.text) }
                Text("History", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${thisWeek.workouts}", "workouts this week", Modifier.weight(1f))
                Stat(minutes(thisWeek.seconds), "this week · ${minutes(lastWeek.seconds)} last week", Modifier.weight(1f))
                Stat("$streak", if (streak == 1) "day streak" else "days streak", Modifier.weight(1f))
            }
        }
        item { SectionTitle("Minutes per week") }
        item { WeekChart(weeks) }
        item { Calendar(month, stats.daysWithWorkouts(), today, onMonth = { month = it }) }
        item { SectionTitle("Workouts") }
        if (entries.isEmpty()) item {
            Text("Nothing yet. Finished and stopped workouts show up here.", color = Palette.muted, modifier = Modifier.padding(16.dp))
        }
        for ((day, list) in stats.groupByDay()) {
            item(key = "d$day") {
                Text(dayTitle(day, today), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
            }
            items(list, key = { it.id }) { e -> EntryRow(e, e.effectiveStatus(liveEntryId)) { deleting = e } }
        }
    }

    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${e.name}”?") },
            text = { Text(Instant.ofEpochMilli(e.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))) },
            confirmButton = { TextButton(onClick = { Store.deleteHistory(e.id); deleting = null }) { Text("Delete", color = Palette.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(Palette.card, RoundedCornerShape(14.dp)).border(1.dp, Palette.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Palette.muted, fontSize = 12.sp)
    }
}

@Composable
private fun WeekChart(weeks: List<Pair<LocalDate, Int>>) {
    val max = (weeks.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    Row(
        Modifier.fillMaxWidth().height(160.dp).background(Palette.card, RoundedCornerShape(14.dp)).padding(10.dp)
            .semantics { contentDescription = "Minutes per week, last 8 weeks: " + weeks.joinToString { it.second.toString() } },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        weeks.forEachIndexed { i, (start, min) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(if (min > 0) "$min" else "", color = Palette.muted, fontSize = 10.sp)
                Box(Modifier.width(22.dp).fillMaxHeight(fraction = (min.toFloat() / max * 0.75f).coerceAtLeast(0.02f))
                    .background(if (i == weeks.lastIndex) Palette.accent else Palette.line, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                Text(start.format(fmt), color = Palette.muted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Calendar(month: YearMonth, days: Set<LocalDate>, today: LocalDate, onMonth: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text("‹", fontSize = 20.sp, color = Palette.text) }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.from(today)) {
            Text("›", fontSize = 20.sp, color = if (month < YearMonth.from(today)) Palette.text else Palette.line)
        }
    }
    val first = month.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val weeksInGrid = ((first.dayOfWeek.value - 1 + month.lengthOfMonth()) + 6) / 7
    Column(Modifier.fillMaxWidth().background(Palette.card, RoundedCornerShape(14.dp)).padding(10.dp)) {
        Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(it, color = Palette.muted, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } }
        for (w in 0 until weeksInGrid) Row {
            for (d in 0..6) {
                val date = gridStart.plusDays((w * 7 + d).toLong())
                val inMonth = date.month == month.month
                val done = date in days
                Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(if (done) Palette.accent.copy(alpha = if (inMonth) 1f else .3f) else Color.Transparent, CircleShape)
                            .then(if (date == today) Modifier.border(2.dp, Palette.text, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${date.dayOfMonth}", fontSize = 14.sp,
                            color = when { done -> Color(0xFF111111); inMonth -> Palette.text; else -> Palette.muted.copy(alpha = .4f) },
                            fontWeight = if (done) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: HistoryEntry, status: EntryStatus, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Palette.card),
        border = BorderStroke(1.dp, Palette.line),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val time = Instant.ofEpochMilli(e.startedAt).atZone(ZoneId.systemDefault()).toLocalTime()
                    .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                Text("$time · ${formatClock(e.doneSec)}" + if (status != EntryStatus.COMPLETED) " of ${formatClock(e.plannedSec)}" else "",
                    color = Palette.muted, fontSize = 14.sp)
            }
            val (label, fg, bg) = when (status) {
                EntryStatus.COMPLETED -> Triple("✓ Done", Color(0xFF4AD685), Color(0x2E23A55A))
                EntryStatus.ACTIVE -> Triple("Unfinished", Palette.accent, Color(0x2EFF6B2C))
                EntryStatus.STOPPED -> Triple("Stopped ${(e.doneSec * 100.0 / e.plannedSec.coerceAtLeast(1)).roundToInt()}%", Palette.muted, Palette.line)
            }
            Text(label, color = fg, fontSize = 12.sp, modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDelete) { Text("✕", color = Palette.muted) }
        }
    }
}

private fun minutes(sec: Int) = "${(sec / 60.0).roundToInt()} min"

private fun dayTitle(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMM"))
}
