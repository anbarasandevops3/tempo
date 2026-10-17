package io.github.anbarasandevops3.tempo.model

import kotlin.math.ceil
import kotlin.math.max

// Mirrors src/model/timeline.ts.

/** One continuous stretch of time. kind == null means the "Get ready" lead-in. */
data class Segment(
    val index: Int,
    val kind: StepKind?,
    val title: String,
    /** The custom name, if the step has one. */
    val label: String?,
    val start: Int,
    val duration: Int,
    val round: Int? = null,
    val rounds: Int? = null,
    val roundStart: Boolean = false,
) {
    val end: Int get() = start + duration
    val isReady: Boolean get() = kind == null
}

data class Timeline(val segments: List<Segment>, val total: Int) {
    /** Segment playing at time t (clamped to the last one). */
    fun segmentAt(t: Double): Segment? {
        if (segments.isEmpty()) return null
        var lo = 0
        var hi = segments.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (segments[mid].start <= t) lo = mid else hi = mid - 1
        }
        return segments[lo]
    }
}

fun compile(workout: Workout): Timeline {
    val segments = mutableListOf<Segment>()
    var t = 0

    fun push(kind: StepKind?, title: String, label: String?, duration: Int, round: Int?, rounds: Int?, roundStart: Boolean) {
        if (duration <= 0) return
        segments += Segment(segments.size, kind, title, label, t, duration, round, rounds, roundStart)
        t += duration
    }

    if (workout.cues.getReady > 0) push(null, "Get ready", null, workout.cues.getReady, null, null, false)

    fun walk(nodes: List<WorkoutNode>, round: Int?, rounds: Int?) {
        var first = true
        for (node in nodes) {
            when (node) {
                is Step -> {
                    val label = node.label?.trim()?.takeIf { it.isNotEmpty() }
                    push(node.kind, label ?: node.kind.label, label, max(0, node.seconds), round, rounds, round != null && first)
                    first = false
                }
                is Repeat -> {
                    val times = max(0, node.times)
                    for (n in 1..times) {
                        var children = node.children
                        val last = children.lastOrNull()
                        if (n == times && node.skipLastRest && last is Step && last.kind.resting) {
                            children = children.dropLast(1)
                        }
                        walk(children, n, times)
                    }
                    first = false
                }
            }
        }
    }
    walk(workout.nodes, null, null)
    return Timeline(segments, t)
}

fun formatClock(sec: Double): String {
    val s = max(0, ceil(sec - 1e-6).toInt())
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    val mm = if (h > 0) m.toString().padStart(2, '0') else m.toString()
    return (if (h > 0) "$h:" else "") + "$mm:" + r.toString().padStart(2, '0')
}

fun formatClock(sec: Int): String = formatClock(sec.toDouble())
