package io.github.anbarasandevops3.tempo.model

// Mirrors src/model/types.ts. A workout is a tree of steps and repeats, compiled into a flat
// timeline (Timeline.kt) before playing.

enum class StepKind(val key: String, val label: String, val resting: Boolean = false) {
    WARMUP("warmup", "Warm up"),
    RUN("run", "Run"),
    JOG("jog", "Jog"),
    WALK("walk", "Walk", resting = true),
    SPRINT("sprint", "Sprint"),
    WORK("work", "Work"),
    REST("rest", "Rest", resting = true),
    RECOVER("recover", "Recover", resting = true),
    COOLDOWN("cooldown", "Cool down"),
    STRETCH("stretch", "Stretch");

    companion object {
        fun fromKey(key: String): StepKind = entries.firstOrNull { it.key == key } ?: WORK
    }
}

sealed interface WorkoutNode {
    val id: String
}

data class Step(
    override val id: String,
    val kind: StepKind,
    val seconds: Int,
    /** Shown on screen instead of the kind label, e.g. "Burpees"; spoken on Android when speakNames is on. */
    val label: String? = null,
) : WorkoutNode

data class Repeat(
    override val id: String,
    val times: Int,
    val children: List<WorkoutNode>,
    /** Drop the final child on the last round when it is a rest/recover/walk (e.g. Tabata). */
    val skipLastRest: Boolean = false,
) : WorkoutNode

data class CueSettings(
    val getReady: Int = 10,
    val countdownBeeps: Int = 3,
    val halfway: Boolean = true,
    val tenSeconds: Boolean = true,
    val announceRounds: Boolean = true,
    /** Android only. */
    val vibrate: Boolean = true,
    /** Android only: say custom step names with text-to-speech. */
    val speakNames: Boolean = true,
)

data class Workout(
    val id: String,
    val name: String,
    val nodes: List<WorkoutNode>,
    val cues: CueSettings = CueSettings(),
    val updatedAt: Long = System.currentTimeMillis(),
)

private val ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

fun newId(): String = (1..8).map { ID_CHARS.random() }.joinToString("")
