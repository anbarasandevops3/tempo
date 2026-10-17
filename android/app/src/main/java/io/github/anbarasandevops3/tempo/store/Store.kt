package io.github.anbarasandevops3.tempo.store

import android.content.Context
import io.github.anbarasandevops3.tempo.model.EntryStatus
import io.github.anbarasandevops3.tempo.model.HistoryEntry
import io.github.anbarasandevops3.tempo.model.MIN_LOG_SEC
import io.github.anbarasandevops3.tempo.model.TEMPLATES
import io.github.anbarasandevops3.tempo.model.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A run that can be resumed after the app was closed (mirrors SavedSession in src/store/store.ts). */
data class SavedSession(val workoutId: String, val position: Double, val savedAt: Long, val entryId: String?)

/**
 * Workouts, history and the resumable session, on the phone. Files are written atomically
 * (temp file + rename) so a kill mid-write can't corrupt them. Shared by the UI and the service.
 */
object Store {
    private lateinit var dir: File
    private val _workouts = MutableStateFlow<List<Workout>>(emptyList())
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val workouts: StateFlow<List<Workout>> = _workouts.asStateFlow()
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private const val HISTORY_MAX = 5000
    private const val SESSION_MAX_AGE_MS = 3 * 3600_000L

    @Synchronized
    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = context.applicationContext.filesDir
        val saved = readArray("workouts.json")
        _workouts.value = if (saved != null) saved.objects().mapNotNull { runCatching { workoutFromJson(it) }.getOrNull() }
        else TEMPLATES.take(3).map { it.make() }.also { writeWorkouts(it) }
        _history.value = readArray("history.json")?.objects()?.mapNotNull { runCatching { entryFromJson(it) }.getOrNull() } ?: emptyList()
    }

    // --- workouts

    @Synchronized
    fun saveWorkout(w: Workout) {
        val list = _workouts.value
        val next = if (list.any { it.id == w.id }) list.map { if (it.id == w.id) w else it } else listOf(w) + list
        writeWorkouts(next)
    }

    @Synchronized
    fun deleteWorkout(id: String) = writeWorkouts(_workouts.value.filter { it.id != id })

    fun workout(id: String): Workout? = _workouts.value.firstOrNull { it.id == id }

    private fun writeWorkouts(list: List<Workout>) {
        _workouts.value = list
        writeText("workouts.json", JSONArray(list.map { it.toJson() }).toString())
    }

    // --- history

    @Synchronized
    fun addHistory(e: HistoryEntry) = writeHistory(_history.value + e)

    /** doneSec is time actually played, capped at the plan. */
    @Synchronized
    fun updateHistory(id: String, doneSec: Int? = null, status: EntryStatus? = null) {
        writeHistory(_history.value.map { e ->
            if (e.id != id) e
            else e.copy(
                doneSec = doneSec?.coerceAtMost(e.plannedSec) ?: e.doneSec,
                status = status ?: e.status,
            )
        })
    }

    /** Close a stopped run: keep it if it lasted long enough, else drop it as an accidental start. */
    @Synchronized
    fun finishStopped(id: String, doneSec: Int) {
        val e = _history.value.firstOrNull { it.id == id } ?: return
        val done = doneSec.coerceAtMost(e.plannedSec)
        if (done < MIN_LOG_SEC) writeHistory(_history.value.filter { it.id != id })
        else updateHistory(id, done, EntryStatus.STOPPED)
    }

    @Synchronized
    fun deleteHistory(id: String) = writeHistory(_history.value.filter { it.id != id })

    private fun writeHistory(list: List<HistoryEntry>) {
        val capped = if (list.size > HISTORY_MAX) list.takeLast(HISTORY_MAX) else list
        _history.value = capped
        writeText("history.json", JSONArray(capped.map { it.toJson() }).toString())
    }

    // --- session

    fun loadSession(): SavedSession? {
        val o = runCatching { JSONObject(File(dir, "session.json").readText()) }.getOrNull() ?: return null
        val s = SavedSession(o.getString("workoutId"), o.getDouble("position"), o.getLong("savedAt"),
            o.optString("entryId").takeIf { o.has("entryId") && it.isNotEmpty() })
        if (System.currentTimeMillis() - s.savedAt > SESSION_MAX_AGE_MS) { saveSession(null); return null }
        return s
    }

    fun saveSession(s: SavedSession?) {
        if (s == null) { File(dir, "session.json").delete(); return }
        writeText("session.json", JSONObject().put("workoutId", s.workoutId).put("position", s.position)
            .put("savedAt", s.savedAt).apply { s.entryId?.let { put("entryId", it) } }.toString())
    }

    // --- backup

    fun exportBackup(): String = backupToJson(Backup(_workouts.value, _history.value))

    /** @return counts of (workouts, history entries) after the merge. */
    @Synchronized
    fun importBackup(text: String): Pair<Int, Int> {
        val merged = merge(Backup(_workouts.value, _history.value), backupFromJson(text))
        writeWorkouts(merged.workouts)
        writeHistory(merged.history)
        return merged.workouts.size to merged.history.size
    }

    // --- files

    private fun readArray(name: String): JSONArray? =
        runCatching { JSONArray(File(dir, name).readText()) }.getOrNull()

    private fun writeText(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }
}
