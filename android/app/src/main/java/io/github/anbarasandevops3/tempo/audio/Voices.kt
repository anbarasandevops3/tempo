package io.github.anbarasandevops3.tempo.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The pre-recorded voice pack (public/voice, packaged as assets). */
class VoicePack(private val index: Map<String, Pair<Int, Int>>, private val data: ByteArray) : ClipSource {
    override fun clip(key: String): ByteArray? = index[key]?.let { (off, len) -> data.copyOfRange(off, off + len) }

    companion object {
        @Volatile private var cached: VoicePack? = null

        fun load(context: Context): VoicePack = cached ?: synchronized(this) {
            cached ?: run {
                val json = JSONObject(context.assets.open("pack.json").bufferedReader().use { it.readText() })
                val clips = json.getJSONObject("clips")
                val index = clips.keys().asSequence().associateWith { k ->
                    val a = clips.getJSONArray(k); a.getInt(0) to a.getInt(1)
                }
                VoicePack(index, context.assets.open("pack.bin").use { it.readBytes() }).also { cached = it }
            }
        }
    }
}

/**
 * Speaks custom step names ("Burpees") with the phone's text-to-speech, rendered to files and
 * converted to clips so they can be mixed into the track. Blocking: call off the main thread.
 * Names that fail are simply missing; the cue falls back to the step type.
 */
class NameVoices(private val context: Context) {
    private companion object { const val TAG = "TempoTTS" }
    private val clips = ConcurrentHashMap<String, ByteArray>()

    fun clip(key: String): ByteArray? = clips[key]

    fun prepare(labels: Collection<String>, timeoutSec: Long = 15): Map<String, ByteArray> {
        val wanted = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            .filter { !clips.containsKey(nameClipKey(it)) }
        if (wanted.isEmpty()) return clips
        val ready = CountDownLatch(1)
        var ok = false
        val tts = TextToSpeech(context.applicationContext) { status -> ok = status == TextToSpeech.SUCCESS; ready.countDown() }
        try {
            if (!ready.await(timeoutSec, TimeUnit.SECONDS) || !ok) return clips
            tts.language = Locale.getDefault().takeIf { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: Locale.US
            val dir = File(context.cacheDir, "tts").apply { mkdirs() }
            // One name at a time: some engines drop queued synthesizeToFile requests. Retry a failure once.
            var latch = CountDownLatch(1)
            var failed = false
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {}
                override fun onDone(id: String) = latch.countDown()
                @Deprecated("Deprecated in Java") override fun onError(id: String) { failed = true; latch.countDown() }
                override fun onError(id: String, errorCode: Int) { failed = true; latch.countDown() }
            })
            val perName = maxOf(3L, timeoutSec / maxOf(1, wanted.size) * 2)
            wanted.forEachIndexed { i, label ->
                val f = File(dir, "name$i.wav")
                for (attempt in 1..2) {
                    f.delete()
                    latch = CountDownLatch(1)
                    failed = false
                    if (tts.synthesizeToFile(label, null, f, "u$i-$attempt") != TextToSpeech.SUCCESS) continue
                    val finished = latch.await(perName, TimeUnit.SECONDS)
                    val clip = if (finished && !failed) runCatching { readWavClip(f.readBytes()) }.getOrNull() else null
                    if (clip != null && clip.isNotEmpty()) { clips[nameClipKey(label)] = clip; break }
                }
                Log.i(TAG, "name '$label': ${if (clips.containsKey(nameClipKey(label))) "ok" else "failed"}")
            }
        } finally {
            tts.shutdown()
        }
        return clips
    }
}

/** Parse a 16-bit mono PCM WAV (as written by TextToSpeech) into an 8-bit clip at SAMPLE_RATE. */
fun readWavClip(bytes: ByteArray): ByteArray? {
    fun u16(o: Int) = (bytes[o].toInt() and 0xff) or ((bytes[o + 1].toInt() and 0xff) shl 8)
    fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
    if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
    var o = 12
    var rate = 0
    var channels = 1
    var bits = 16
    while (o + 8 <= bytes.size) {
        val id = String(bytes, o, 4)
        val size = u32(o + 4)
        if (id == "fmt ") {
            channels = u16(o + 10); rate = u32(o + 12); bits = u16(o + 22)
        } else if (id == "data") {
            if (bits != 16 || rate <= 0) return null
            val end = minOf(bytes.size, o + 8 + size)
            var pcm = bytes.copyOfRange(o + 8, end)
            if (channels == 2) pcm = ByteArray(pcm.size / 2) { i -> pcm[(i / 2) * 4 + (i % 2)] }
            return pcm16ToClip(pcm, rate)
        }
        o += 8 + size + (size and 1)
    }
    return null
}
