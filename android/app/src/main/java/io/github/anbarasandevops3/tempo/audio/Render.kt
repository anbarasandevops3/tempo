package io.github.anbarasandevops3.tempo.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Mirrors src/audio/render.ts: the whole workout in ONE WAV, mono 8-bit unsigned PCM at
// SAMPLE_RATE (silence = 128). The player plays this file; its position is the only clock.

const val SAMPLE_RATE = 11025 // must match RATE in tools/make_voice_pack.py
private const val TAIL = 3.0
private const val CLIP_GAP = 0.06
private const val BEEP_AMP = 90.0
const val WAV_HEADER = 44

fun interface ClipSource {
    /** 8-bit unsigned PCM at SAMPLE_RATE, or null if unknown. */
    fun clip(key: String): ByteArray?
}

fun renderTrack(cues: List<Cue>, totalSec: Int, clips: ClipSource): ByteArray {
    val len = ceil((totalSec + TAIL) * SAMPLE_RATE).toInt()
    val buf = ByteArray(WAV_HEADER + len)
    writeHeader(buf, len)
    buf.fill(128.toByte(), WAV_HEADER, buf.size)

    for (cue in cues) {
        var pos = sampleAt(cue.at, SAMPLE_RATE)
        when (cue) {
            is Cue.Beep -> mixBeep(buf, pos, cue.freq, cue.dur)
            is Cue.Voice -> for (key in cue.clips) {
                val clip = clips.clip(key) ?: continue
                mix(buf, pos, clip)
                pos += clip.size + (CLIP_GAP * SAMPLE_RATE).roundToInt()
            }
        }
    }
    return buf
}

private fun u8(b: Byte) = b.toInt() and 0xff

private fun clamp(v: Int) = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()

private fun mix(buf: ByteArray, at: Int, clip: ByteArray) {
    for (i in clip.indices) {
        val j = WAV_HEADER + at + i
        if (j < WAV_HEADER || j >= buf.size) continue
        buf[j] = clamp(u8(buf[j]) + u8(clip[i]) - 128)
    }
}

private fun mixBeep(buf: ByteArray, at: Int, freq: Double, dur: Double) {
    val n = (dur * SAMPLE_RATE).roundToInt()
    val fade = (0.008 * SAMPLE_RATE).roundToInt()
    for (i in 0 until n) {
        val j = WAV_HEADER + at + i
        if (j < WAV_HEADER || j >= buf.size) continue
        val env = min(1.0, min(i.toDouble() / fade, (n - i).toDouble() / fade))
        val v = sin(2 * PI * freq * i / SAMPLE_RATE) * BEEP_AMP * env
        buf[j] = clamp(u8(buf[j]) + v.roundToInt())
    }
}

private fun writeHeader(buf: ByteArray, dataLen: Int) {
    fun ascii(o: Int, s: String) = s.forEachIndexed { i, c -> buf[o + i] = c.code.toByte() }
    fun u32(o: Int, v: Int) { for (i in 0..3) buf[o + i] = (v shr (8 * i)).toByte() }
    fun u16(o: Int, v: Int) { for (i in 0..1) buf[o + i] = (v shr (8 * i)).toByte() }
    ascii(0, "RIFF"); u32(4, 36 + dataLen); ascii(8, "WAVE")
    ascii(12, "fmt "); u32(16, 16); u16(20, 1); u16(22, 1)
    u32(24, SAMPLE_RATE); u32(28, SAMPLE_RATE); u16(32, 1); u16(34, 8)
    ascii(36, "data"); u32(40, dataLen)
}

/**
 * Convert 16-bit little-endian mono PCM at [rate] to 8-bit unsigned at SAMPLE_RATE (linear
 * interpolation), normalised to a similar loudness as the voice pack. Used for TTS clips.
 */
fun pcm16ToClip(pcm: ByteArray, rate: Int): ByteArray {
    val n = pcm.size / 2
    if (n == 0) return ByteArray(0)
    val src = FloatArray(n) { i -> ((pcm[2 * i].toInt() and 0xff) or (pcm[2 * i + 1].toInt() shl 8)).toShort() / 32768f }
    // trim leading/trailing near-silence
    val thr = 0.01f
    val first = src.indexOfFirst { it > thr || it < -thr }.coerceAtLeast(0)
    val last = src.indexOfLast { it > thr || it < -thr }.let { if (it < first) n - 1 else it }
    val peak = (first..last).maxOf { kotlin.math.abs(src[it]) }.coerceAtLeast(1e-4f)
    val gain = 0.85f / peak
    val outLen = ((last - first + 1).toLong() * SAMPLE_RATE / rate).toInt()
    return ByteArray(outLen) { i ->
        val x = first + i.toDouble() * rate / SAMPLE_RATE
        val i0 = x.toInt().coerceIn(0, n - 1)
        val i1 = (i0 + 1).coerceAtMost(n - 1)
        val f = (x - i0).toFloat()
        val v = (src[i0] * (1 - f) + src[i1] * f) * gain
        clamp(128 + (v * 127).roundToInt())
    }
}
