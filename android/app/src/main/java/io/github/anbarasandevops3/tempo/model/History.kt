package io.github.anbarasandevops3.tempo.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// Mirrors src/model/history.ts.

enum class EntryStatus(val key: String) {
    ACTIVE("active"), COMPLETED("completed"), STOPPED("stopped");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: STOPPED
    }
}

data class HistoryEntry(
    val id: String,
    val workoutId: String,
    val name: String,
    val startedAt: Long,
    val plannedSec: Int,
    /** Seconds actually played (skipped parts excluded). */
    val doneSec: Int,
    val status: EntryStatus,
)

/** Stopped runs shorter than this are treated as accidental starts and dropped. */
const val MIN_LOG_SEC = 30

/** An ACTIVE entry that isn't the live/resumable run counts as stopped. */
fun HistoryEntry.effectiveStatus(liveEntryId: String?): EntryStatus =
    if (status == EntryStatus.ACTIVE && id != liveEntryId) EntryStatus.STOPPED else status

class HistoryStats(private val entries: List<HistoryEntry>, private val zone: ZoneId = ZoneId.systemDefault()) {

    fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    data class Totals(val workouts: Int, val seconds: Int)

    /** Workouts started on days in [from, to). */
    fun totals(from: LocalDate, to: LocalDate): Totals {
        val inRange = entries.filter { val d = day(it.startedAt); !d.isBefore(from) && d.isBefore(to) }
        return Totals(inRange.size, inRange.sumOf { it.doneSec })
    }

    /** Minutes per week for the last [weeks] weeks, oldest first. */
    fun weeklyMinutes(today: LocalDate, weeks: Int): List<Pair<LocalDate, Int>> {
        val current = weekStart(today)
        return (weeks - 1 downTo 0).map { i ->
            val start = current.minusWeeks(i.toLong())
            start to Math.round(totals(start, start.plusWeeks(1)).seconds / 60.0).toInt()
        }
    }

    /** Consecutive days with a workout, ending today (or yesterday if nothing yet today). */
    fun streakDays(today: LocalDate): Int {
        val days = entries.map { day(it.startedAt) }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    fun daysWithWorkouts(): Set<LocalDate> = entries.map { day(it.startedAt) }.toSet()

    /** Newest first, grouped by day. */
    fun groupByDay(): List<Pair<LocalDate, List<HistoryEntry>>> =
        entries.sortedByDescending { it.startedAt }.groupBy { day(it.startedAt) }.toList()

    fun lastDone(workoutId: String): Long? = entries.filter { it.workoutId == workoutId }.maxOfOrNull { it.startedAt }

    fun relativeDay(ms: Long, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(day(ms), today)
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days < 14 -> "$days days ago"
            days < 60 -> "${days / 7} weeks ago"
            else -> "${days / 30} months ago"
        }
    }
}
