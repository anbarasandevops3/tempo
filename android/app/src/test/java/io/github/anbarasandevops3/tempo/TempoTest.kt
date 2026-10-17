package io.github.anbarasandevops3.tempo

import io.github.anbarasandevops3.tempo.audio.BeepType
import io.github.anbarasandevops3.tempo.audio.ClipSource
import io.github.anbarasandevops3.tempo.audio.Cue
import io.github.anbarasandevops3.tempo.audio.SAMPLE_RATE
import io.github.anbarasandevops3.tempo.audio.WAV_HEADER
import io.github.anbarasandevops3.tempo.audio.buildCues
import io.github.anbarasandevops3.tempo.audio.bytesToPcm16
import io.github.anbarasandevops3.tempo.audio.durationClips
import io.github.anbarasandevops3.tempo.audio.nameClipKey
import io.github.anbarasandevops3.tempo.audio.pcm16ToClip
import io.github.anbarasandevops3.tempo.audio.renderTrack
import io.github.anbarasandevops3.tempo.model.CueSettings
import io.github.anbarasandevops3.tempo.model.EntryStatus
import io.github.anbarasandevops3.tempo.model.HistoryEntry
import io.github.anbarasandevops3.tempo.model.HistoryStats
import io.github.anbarasandevops3.tempo.model.Repeat
import io.github.anbarasandevops3.tempo.model.Step
import io.github.anbarasandevops3.tempo.model.StepKind
import io.github.anbarasandevops3.tempo.model.TEMPLATES
import io.github.anbarasandevops3.tempo.model.Workout
import io.github.anbarasandevops3.tempo.model.WorkoutNode
import io.github.anbarasandevops3.tempo.model.append
import io.github.anbarasandevops3.tempo.model.compile
import io.github.anbarasandevops3.tempo.model.effectiveStatus
import io.github.anbarasandevops3.tempo.model.formatClock
import io.github.anbarasandevops3.tempo.model.move
import io.github.anbarasandevops3.tempo.model.parseDuration
import io.github.anbarasandevops3.tempo.model.remove
import io.github.anbarasandevops3.tempo.store.Backup
import io.github.anbarasandevops3.tempo.store.BackupException
import io.github.anbarasandevops3.tempo.store.backupFromJson
import io.github.anbarasandevops3.tempo.store.backupToJson
import io.github.anbarasandevops3.tempo.store.merge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TempoTest {
    private fun w(nodes: List<WorkoutNode>, getReady: Int = 0) = Workout("w", "T", nodes, CueSettings(getReady = getReady), 0)
    private fun step(kind: StepKind, s: Int, label: String? = null) = Step(kind.key + s, kind, s, label)

    // --- timeline (same cases as src/model/timeline.test.ts)

    @Test fun flattensRepeatsBackToBack() {
        val tl = compile(w(listOf(step(StepKind.WARMUP, 60), Repeat("r", 3, listOf(step(StepKind.RUN, 30), step(StepKind.WALK, 20)))), 10))
        assertEquals(listOf(null, "warmup", "run", "walk", "run", "walk", "run", "walk"), tl.segments.map { it.kind?.key })
        assertEquals(10 + 60 + 3 * 50, tl.total)
        tl.segments.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        assertEquals(2, tl.segments[4].round); assertEquals(3, tl.segments[4].rounds); assertTrue(tl.segments[4].roundStart)
        assertTrue(!tl.segments[5].roundStart)
    }

    @Test fun skipsTrailingRestOnLastRound() {
        val tl = compile(TEMPLATES.first { it.name == "Tabata" }.make())
        assertEquals(10 + 8 * 30 - 10, tl.total)
        assertEquals(StepKind.WORK, tl.segments.last().kind)
    }

    @Test fun segmentAt() {
        val tl = compile(w(listOf(step(StepKind.RUN, 10), step(StepKind.WALK, 10))))
        assertEquals(StepKind.RUN, tl.segmentAt(9.99)?.kind)
        assertEquals(StepKind.WALK, tl.segmentAt(10.0)?.kind)
        assertEquals(StepKind.WALK, tl.segmentAt(99.0)?.kind)
    }

    @Test fun formatsClock() {
        assertEquals("0:00", formatClock(0)); assertEquals("1:00", formatClock(59.2))
        assertEquals("10:05", formatClock(605)); assertEquals("1:02:05", formatClock(3725))
    }

    // --- cues

    @Test fun speaksDurations() {
        assertEquals(listOf("n1", "minute", "n30", "seconds"), durationClips(90))
        assertEquals(listOf("n2", "minutes"), durationClips(120))
    }

    @Test fun announcesCountsDownAndFinishes() {
        val tl = compile(w(listOf(Repeat("r", 2, listOf(step(StepKind.RUN, 60))))))
        val cues = buildCues(tl, CueSettings(getReady = 0))
        val voices = cues.filterIsInstance<Cue.Voice>().map { it.clips.joinToString(" ") }
        assertEquals(listOf("round n1 of n2 run n1 minute", "halfway", "ten_seconds",
            "last_round run n1 minute", "halfway", "ten_seconds", "complete"), voices)
        assertEquals(listOf(57.0, 58.0, 59.0, 117.0, 118.0, 119.0), cues.filterIsInstance<Cue.Beep>().filter { it.type == BeepType.TICK }.map { it.at })
        assertEquals(cues.map { it.at }, cues.map { it.at }.sorted())
    }

    @Test fun speaksCustomNamesWhenAvailable() {
        val tl = compile(w(listOf(step(StepKind.WORK, 20, "Burpees"))))
        val with = buildCues(tl, CueSettings(getReady = 0)) { true }.filterIsInstance<Cue.Voice>().first()
        assertEquals(listOf(nameClipKey("Burpees"), "n20", "seconds"), with.clips)
        val without = buildCues(tl, CueSettings(getReady = 0)) { it != nameClipKey("Burpees") }.filterIsInstance<Cue.Voice>().first()
        assertEquals("work", without.clips.first())
        val off = buildCues(tl, CueSettings(getReady = 0, speakNames = false)).filterIsInstance<Cue.Voice>().first()
        assertEquals("work", off.clips.first())
    }

    // --- render

    @Test fun rendersValidWav() {
        val clips = ClipSource { if (it == "run") shortArrayOf(1000, 2000, 3000, 4000) else null }
        val tl = compile(w(listOf(step(StepKind.RUN, 20))))
        val wav = renderTrack(buildCues(tl, CueSettings(getReady = 0)), tl.total, clips)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(16, wav[34].toInt())
        assertEquals(Math.ceil(23.0 * SAMPLE_RATE).toInt() * 2, wav.size - WAV_HEADER)
        val pcm = bytesToPcm16(wav, WAV_HEADER)
        val at = Math.round(0.55 * SAMPLE_RATE).toInt()
        assertArrayEquals(shortArrayOf(1000, 2000, 3000, 4000), pcm.copyOfRange(at, at + 4))
        assertEquals(0, pcm[10 * SAMPLE_RATE].toInt())
    }

    @Test fun convertsTtsAudio() {
        // 0.1 s of a 16-bit sine at 22050 Hz with silence around it -> trimmed, resampled to ~0.1 s
        val rate = 22050
        val pcm = ByteArray(rate / 10 * 2 * 3)
        for (i in rate / 10 until 2 * rate / 10) {
            val v = (Math.sin(i * 0.2) * 12000).toInt()
            pcm[2 * i] = v.toByte(); pcm[2 * i + 1] = (v shr 8).toByte()
        }
        val clip = pcm16ToClip(pcm, rate)
        assertTrue(clip.size in (SAMPLE_RATE / 10 - 20)..(SAMPLE_RATE / 10 + 20))
        // normalised to ~95% of full scale
        assertTrue(clip.maxOf { it.toInt() } in 30000..32767)
    }

    // --- edits

    @Test fun editsTree() {
        val nodes = listOf<WorkoutNode>(step(StepKind.RUN, 10), Repeat("r", 2, listOf(step(StepKind.WALK, 5))))
        assertEquals(listOf("r", "run10"), nodes.move("run10", 1).map { it.id })
        assertEquals(2, (nodes.append("r", step(StepKind.REST, 3))[1] as Repeat).children.size)
        assertEquals(0, (nodes.remove("walk5")[1] as Repeat).children.size)
        assertEquals(90, parseDuration("1:30")); assertEquals(90, parseDuration("90")); assertEquals(90, parseDuration("1m30"))
        assertNull(parseDuration("abc"))
    }

    // --- history (same cases as src/model/history.test.ts)

    private val zone = ZoneId.of("UTC")
    private fun at(d: LocalDate, h: Int = 7) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private var n = 0
    private fun entry(day: LocalDate, done: Int = 600, status: EntryStatus = EntryStatus.COMPLETED) =
        HistoryEntry("e${n++}", "w1", "Intervals", at(day), 600, done, status)
    private val today = LocalDate.of(2026, 10, 14) // a Wednesday

    @Test fun historyStats() {
        val s = HistoryStats(listOf(entry(today.minusDays(2), 1200), entry(today, 600), entry(today.minusDays(3), 900)), zone)
        assertEquals(LocalDate.of(2026, 10, 12), s.weekStart(today))
        assertEquals(HistoryStats.Totals(2, 1800), s.totals(s.weekStart(today), s.weekStart(today).plusWeeks(1)))
        assertEquals(listOf(0, 15, 30), s.weeklyMinutes(today, 3).map { it.second })
    }

    @Test fun streak() {
        assertEquals(2, HistoryStats(listOf(entry(today), entry(today.minusDays(1)), entry(today.minusDays(3))), zone).streakDays(today))
        assertEquals(2, HistoryStats(listOf(entry(today.minusDays(1)), entry(today.minusDays(2))), zone).streakDays(today))
        assertEquals(0, HistoryStats(listOf(entry(today.minusDays(2))), zone).streakDays(today))
    }

    @Test fun abandonedActiveEntryIsStopped() {
        val e = entry(today, 100, EntryStatus.ACTIVE)
        assertEquals(EntryStatus.ACTIVE, e.effectiveStatus(e.id))
        assertEquals(EntryStatus.STOPPED, e.effectiveStatus(null))
    }

    // --- backup (docs/backup-format.md)

    @Test fun backupRoundTripAndMerge() {
        val wk = TEMPLATES.first { it.name == "HIIT circuit" }.make().copy(updatedAt = 100)
        val h = entry(today, 300, EntryStatus.STOPPED)
        val text = backupToJson(Backup(listOf(wk), listOf(h)))
        val back = backupFromJson(text)
        assertEquals(wk, back.workouts.single())
        assertEquals(h, back.history.single())

        val newer = wk.copy(name = "Renamed", updatedAt = 200)
        val longer = h.copy(doneSec = 600, status = EntryStatus.COMPLETED)
        val other = entry(today.minusDays(1))
        val merged = merge(Backup(listOf(wk), listOf(h)), Backup(listOf(newer), listOf(longer, other)))
        assertEquals("Renamed", merged.workouts.single().name)
        assertEquals(listOf(other, longer), merged.history)
        // older data never overwrites newer
        assertEquals("Renamed", merge(merged, Backup(listOf(wk), listOf(h))).workouts.single().name)
    }

    @Test fun readsWebAppBackup() {
        // Written by the web app: no vibrate/speakNames, no skipLastRest on plain repeats.
        val web = """{"app":"tempo","format":1,"exportedAt":1,"workouts":[{"id":"a","name":"Web","updatedAt":5,
            "cues":{"getReady":10,"countdownBeeps":3,"halfway":true,"tenSeconds":true,"announceRounds":true},
            "nodes":[{"type":"repeat","id":"r","times":2,"children":[{"type":"step","id":"s","kind":"run","seconds":60}]}]}],
            "history":[{"id":"h","workoutId":"a","name":"Web","startedAt":1,"plannedSec":130,"doneSec":130,"status":"completed"}]}"""
        val b = backupFromJson(web)
        assertEquals(130, compile(b.workouts.single()).total)
        assertTrue(b.workouts.single().cues.vibrate)
        assertEquals(EntryStatus.COMPLETED, b.history.single().status)
    }

    @Test(expected = BackupException::class)
    fun rejectsOtherFiles() { backupFromJson("""{"hello":"world"}""") }
}
