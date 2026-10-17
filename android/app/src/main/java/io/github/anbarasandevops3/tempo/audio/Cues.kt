package io.github.anbarasandevops3.tempo.audio

import io.github.anbarasandevops3.tempo.model.CueSettings
import io.github.anbarasandevops3.tempo.model.Timeline
import kotlin.math.min
import kotlin.math.roundToInt

// Mirrors src/audio/cues.ts. Beeps double as vibration cues on Android.

sealed interface Cue {
    val at: Double

    data class Voice(override val at: Double, val clips: List<String>) : Cue

    data class Beep(override val at: Double, val freq: Double, val dur: Double, val type: BeepType) : Cue
}

enum class BeepType { START, TICK, END }

private const val TICK_FREQ = 880.0
private const val TICK_DUR = 0.15
private const val GO_FREQ = 1320.0
private const val GO_DUR = 0.45

/** Clip key for a custom step name (synthesised on the device). */
fun nameClipKey(label: String) = "name:${label.trim().lowercase()}"

/** Clip keys for a spoken duration, e.g. 90 -> one minute thirty seconds. */
fun durationClips(sec: Int): List<String> {
    val m = sec / 60
    val s = sec % 60
    val out = mutableListOf<String>()
    if (m > 0) out += listOf("n${min(m, 59)}", if (m == 1) "minute" else "minutes")
    if (s > 0) out += listOf("n$s", if (s == 1) "second" else "seconds")
    return out
}

/**
 * @param hasClip whether a clip exists; used to fall back from a custom name to the step type
 *   when text-to-speech couldn't produce it.
 */
fun buildCues(tl: Timeline, settings: CueSettings, hasClip: (String) -> Boolean = { true }): List<Cue> {
    val cues = mutableListOf<Cue>()
    for (seg in tl.segments) {
        val end = seg.end.toDouble()
        if (seg.isReady) {
            cues += Cue.Voice(seg.start.toDouble(), listOf("get_ready"))
        } else {
            cues += Cue.Beep(seg.start.toDouble(), GO_FREQ, GO_DUR, BeepType.START)
            val clips = mutableListOf<String>()
            val round = seg.round
            val rounds = seg.rounds
            if (settings.announceRounds && seg.roundStart && round != null && rounds != null && rounds > 1) {
                clips += if (round == rounds) listOf("last_round") else listOf("round", "n${min(round, 59)}", "of", "n${min(rounds, 59)}")
            }
            val name = seg.label?.let(::nameClipKey)
            clips += if (settings.speakNames && name != null && hasClip(name)) name else seg.kind!!.key
            clips += durationClips(seg.duration)
            cues += Cue.Voice(seg.start + GO_DUR + 0.1, clips)

            if (settings.halfway && seg.duration >= 60) cues += Cue.Voice(seg.start + seg.duration / 2.0, listOf("halfway"))
            if (settings.tenSeconds && seg.duration >= 30) cues += Cue.Voice(end - 10, listOf("ten_seconds"))
        }
        val n = settings.countdownBeeps
        if (n > 0 && seg.duration >= n + 3) {
            for (i in n downTo 1) cues += Cue.Beep(end - i, TICK_FREQ, TICK_DUR, BeepType.TICK)
        }
    }
    cues += Cue.Beep(tl.total.toDouble(), GO_FREQ, GO_DUR, BeepType.END)
    cues += Cue.Voice(tl.total + GO_DUR + 0.1, listOf("complete"))
    return cues.sortedBy { it.at }
}

/** Seconds -> sample index at the given rate. */
fun sampleAt(sec: Double, rate: Int): Int = (sec * rate).roundToInt()
