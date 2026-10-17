package io.github.anbarasandevops3.tempo.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Mirrors src/audio/render.ts: the whole workout in ONE WAV, mono 16-bit PCM at SAMPLE_RATE.
// The player plays this file; its position is the only clock. Android plays the voice pack at its
// full 22050 Hz (the web uses 16000 Hz to save memory on the iPhone).

const val SAMPLE_RATE = 22050 // = the voice pack's rate (RATE in tools/make_voice_pack.py)
private const val TAIL = 3.0
private const val CLIP_GAP = 0.06
/** Voice clips are normalised near full scale; beeps sit a little below so the voice stands out. */
private const val BEEP_AMP = 0.55 * 32767
/** Peak level for device TTS clips, matching the pack (PEAK in make_voice_pack.py). */
private const val TTS_PEAK = 0.95f
const val WAV_HEADER = 44

fun interface ClipSource {
    /** 16-bit PCM at SAMPLE_RATE, or null if unknown. */
    fun clip(key: String): ShortArray?
}

fun renderTrack(cues: List<Cue>, totalSec: Int, clips: ClipSource): ByteArray {
    val len = ceil((totalSec + TAIL) * SAMPLE_RATE).toInt()
    val pcm = ShortArray(len) // zero = silence

    for (cue in cues) {
        var pos = sampleAt(cue.at, SAMPLE_RATE)
        when (cue) {
            is Cue.Beep -> mixBeep(pcm, pos, cue.freq, cue.dur)
            is Cue.Voice -> for (key in cue.clips) {
                val clip = clips.clip(key) ?: continue
                mix(pcm, pos, clip)
                pos += clip.size + (CLIP_GAP * SAMPLE_RATE).roundToInt()
            }
        }
    }
    val buf = ByteArray(WAV_HEADER + len * 2)
    writeHeader(buf, len)
    for (i in 0 until len) {
        val v = pcm[i].toInt()
        buf[WAV_HEADER + 2 * i] = v.toByte()
        buf[WAV_HEADER + 2 * i + 1] = (v shr 8).toByte()
    }
    return buf
}

private fun clamp(v: Int): Short = (if (v < -32768) -32768 else if (v > 32767) 32767 else v).toShort()

private fun mix(pcm: ShortArray, at: Int, clip: ShortArray) {
    for (i in clip.indices) {
        val j = at + i
        if (j < 0 || j >= pcm.size) continue
        pcm[j] = clamp(pcm[j] + clip[i])
    }
}

private fun mixBeep(pcm: ShortArray, at: Int, freq: Double, dur: Double) {
    val n = (dur * SAMPLE_RATE).roundToInt()
    val fade = (0.008 * SAMPLE_RATE).roundToInt()
    for (i in 0 until n) {
        val j = at + i
        if (j < 0 || j >= pcm.size) continue
        val env = min(1.0, min(i.toDouble() / fade, (n - i).toDouble() / fade))
        pcm[j] = clamp(pcm[j] + (sin(2 * PI * freq * i / SAMPLE_RATE) * BEEP_AMP * env).roundToInt())
    }
}

private fun writeHeader(buf: ByteArray, samples: Int) {
    fun ascii(o: Int, s: String) = s.forEachIndexed { i, c -> buf[o + i] = c.code.toByte() }
    fun u32(o: Int, v: Int) { for (i in 0..3) buf[o + i] = (v shr (8 * i)).toByte() }
    fun u16(o: Int, v: Int) { for (i in 0..1) buf[o + i] = (v shr (8 * i)).toByte() }
    val dataLen = samples * 2
    ascii(0, "RIFF"); u32(4, 36 + dataLen); ascii(8, "WAVE")
    ascii(12, "fmt "); u32(16, 16); u16(20, 1); u16(22, 1)
    u32(24, SAMPLE_RATE); u32(28, SAMPLE_RATE * 2); u16(32, 2); u16(34, 16)
    ascii(36, "data"); u32(40, dataLen)
}

/** Little-endian 16-bit bytes -> samples. */
fun bytesToPcm16(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): ShortArray =
    ShortArray(length / 2) { i -> ((bytes[offset + 2 * i].toInt() and 0xff) or (bytes[offset + 2 * i + 1].toInt() shl 8)).toShort() }

/**
 * Turn 16-bit mono PCM at [rate] (e.g. from TextToSpeech) into a clip at SAMPLE_RATE: trim
 * near-silence, resample (linear), and normalise to the same peak as the voice pack.
 */
fun pcm16ToClip(pcm: ByteArray, rate: Int): ShortArray {
    val n = pcm.size / 2
    if (n == 0) return ShortArray(0)
    val src = bytesToPcm16(pcm).let { s -> FloatArray(n) { s[it] / 32768f } }
    val thr = 0.01f
    val first = src.indexOfFirst { abs(it) > thr }.coerceAtLeast(0)
    val last = src.indexOfLast { abs(it) > thr }.let { if (it < first) n - 1 else it }
    val peak = (first..last).maxOf { abs(src[it]) }.coerceAtLeast(1e-4f)
    val gain = TTS_PEAK / peak
    val outLen = ((last - first + 1).toLong() * SAMPLE_RATE / rate).toInt()
    return ShortArray(outLen) { i ->
        val x = first + i.toDouble() * rate / SAMPLE_RATE
        val i0 = x.toInt().coerceIn(0, n - 1)
        val i1 = (i0 + 1).coerceAtMost(n - 1)
        val f = (x - i0).toFloat()
        clamp(((src[i0] * (1 - f) + src[i1] * f) * gain * 32767).roundToInt())
    }
}
