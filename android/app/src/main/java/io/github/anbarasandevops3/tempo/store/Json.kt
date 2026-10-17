package io.github.anbarasandevops3.tempo.store

import io.github.anbarasandevops3.tempo.model.CueSettings
import io.github.anbarasandevops3.tempo.model.EntryStatus
import io.github.anbarasandevops3.tempo.model.HistoryEntry
import io.github.anbarasandevops3.tempo.model.Repeat
import io.github.anbarasandevops3.tempo.model.Step
import io.github.anbarasandevops3.tempo.model.StepKind
import io.github.anbarasandevops3.tempo.model.Workout
import io.github.anbarasandevops3.tempo.model.WorkoutNode
import org.json.JSONArray
import org.json.JSONObject

// JSON in the same shape as the web app (docs/backup-format.md).

const val BACKUP_FORMAT = 1

fun WorkoutNode.toJson(): JSONObject = when (this) {
    is Step -> JSONObject().put("type", "step").put("id", id).put("kind", kind.key).put("seconds", seconds)
        .also { o -> label?.takeIf { it.isNotBlank() }?.let { o.put("label", it) } }
    is Repeat -> JSONObject().put("type", "repeat").put("id", id).put("times", times).put("skipLastRest", skipLastRest)
        .put("children", JSONArray(children.map { it.toJson() }))
}

fun nodeFromJson(o: JSONObject): WorkoutNode =
    if (o.optString("type") == "repeat") Repeat(
        id = o.getString("id"),
        times = o.optInt("times", 1),
        children = o.optJSONArray("children").objects().map(::nodeFromJson),
        skipLastRest = o.optBoolean("skipLastRest", false),
    ) else Step(
        id = o.getString("id"),
        kind = StepKind.fromKey(o.optString("kind")),
        seconds = o.optInt("seconds", 60),
        label = o.optString("label").takeIf { o.has("label") && it.isNotBlank() },
    )

fun CueSettings.toJson(): JSONObject = JSONObject()
    .put("getReady", getReady).put("countdownBeeps", countdownBeeps).put("halfway", halfway)
    .put("tenSeconds", tenSeconds).put("announceRounds", announceRounds)
    .put("vibrate", vibrate).put("speakNames", speakNames)

fun cuesFromJson(o: JSONObject?): CueSettings {
    val d = CueSettings()
    if (o == null) return d
    return CueSettings(
        getReady = o.optInt("getReady", d.getReady),
        countdownBeeps = o.optInt("countdownBeeps", d.countdownBeeps),
        halfway = o.optBoolean("halfway", d.halfway),
        tenSeconds = o.optBoolean("tenSeconds", d.tenSeconds),
        announceRounds = o.optBoolean("announceRounds", d.announceRounds),
        vibrate = o.optBoolean("vibrate", d.vibrate),
        speakNames = o.optBoolean("speakNames", d.speakNames),
    )
}

fun Workout.toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("updatedAt", updatedAt)
    .put("cues", cues.toJson()).put("nodes", JSONArray(nodes.map { it.toJson() }))

fun workoutFromJson(o: JSONObject) = Workout(
    id = o.getString("id"),
    name = o.optString("name", "Workout"),
    nodes = o.optJSONArray("nodes").objects().map(::nodeFromJson),
    cues = cuesFromJson(o.optJSONObject("cues")),
    updatedAt = o.optLong("updatedAt", 0L),
)

fun HistoryEntry.toJson(): JSONObject = JSONObject().put("id", id).put("workoutId", workoutId).put("name", name)
    .put("startedAt", startedAt).put("plannedSec", plannedSec).put("doneSec", doneSec).put("status", status.key)

fun entryFromJson(o: JSONObject) = HistoryEntry(
    id = o.getString("id"),
    workoutId = o.optString("workoutId"),
    name = o.optString("name", "Workout"),
    startedAt = o.optLong("startedAt"),
    plannedSec = o.optInt("plannedSec"),
    doneSec = o.optInt("doneSec"),
    status = EntryStatus.fromKey(o.optString("status")),
)

fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

data class Backup(val workouts: List<Workout>, val history: List<HistoryEntry>)

fun backupToJson(b: Backup, now: Long = System.currentTimeMillis()): String = JSONObject()
    .put("app", "tempo").put("format", BACKUP_FORMAT).put("exportedAt", now)
    .put("workouts", JSONArray(b.workouts.map { it.toJson() }))
    .put("history", JSONArray(b.history.map { it.toJson() }))
    .toString(2)

class BackupException(message: String) : Exception(message)

fun backupFromJson(text: String): Backup {
    val o = runCatching { JSONObject(text) }.getOrElse { throw BackupException("That file isn't a Tempo backup.") }
    if (o.optString("app") != "tempo") throw BackupException("That file isn't a Tempo backup.")
    if (o.optInt("format", 0) > BACKUP_FORMAT) throw BackupException("This backup is from a newer version of Tempo. Update the app first.")
    return Backup(
        o.optJSONArray("workouts").objects().mapNotNull { runCatching { workoutFromJson(it) }.getOrNull() },
        o.optJSONArray("history").objects().mapNotNull { runCatching { entryFromJson(it) }.getOrNull() },
    )
}

/** Merge rules from docs/backup-format.md: never wipe, newer workout wins, longer history entry wins. */
fun merge(current: Backup, incoming: Backup): Backup {
    val workouts = LinkedHashMap<String, Workout>()
    current.workouts.forEach { workouts[it.id] = it }
    incoming.workouts.forEach { w -> val old = workouts[w.id]; if (old == null || w.updatedAt > old.updatedAt) workouts[w.id] = w }
    val history = LinkedHashMap<String, HistoryEntry>()
    current.history.forEach { history[it.id] = it }
    incoming.history.forEach { e ->
        val old = history[e.id]
        val better = old == null || e.doneSec > old.doneSec ||
            (e.doneSec == old.doneSec && e.status == EntryStatus.COMPLETED && old.status != EntryStatus.COMPLETED)
        if (better) history[e.id] = e
    }
    return Backup(workouts.values.toList(), history.values.sortedBy { it.startedAt })
}
