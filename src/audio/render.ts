// Renders the whole workout into ONE WAV file. iOS keeps a playing <audio> element alive with the
// screen locked, while it freezes the page's JavaScript, so every cue must already be in the audio.
// Format: mono, 16-bit PCM at SAMPLE_RATE. ~1.9 MB per minute (a 25-minute workout is ~48 MB).

import type { Cue } from './cues';
import type { VoicePack } from './voice';

/** Track rate on the web. Lower than the pack's 22050 Hz to keep long workouts small in memory. */
export const SAMPLE_RATE = 16000;
const TAIL = 3; // seconds after the last cue ("Workout complete")
const CLIP_GAP = 0.06;
/** Voice clips are normalised near full scale; beeps sit a little below so the voice stands out. */
const BEEP_AMP = 0.55 * 32767;
const HEADER = 44;

export function renderTrack(cues: Cue[], totalSec: number, pack: VoicePack): Uint8Array {
  const len = Math.ceil((totalSec + TAIL) * SAMPLE_RATE);
  const buf = new Uint8Array(HEADER + len * 2);
  writeHeader(buf, len);
  const pcm = new Int16Array(buf.buffer, HEADER, len); // zero-filled = silence

  for (const cue of cues) {
    let pos = Math.round(cue.at * SAMPLE_RATE);
    if (cue.type === 'beep') {
      mixBeep(pcm, pos, cue.freq, cue.dur);
    } else {
      for (const key of cue.clips) {
        const clip = pack.clip(key);
        if (!clip) continue;
        mix(pcm, pos, clip);
        pos += clip.length + Math.round(CLIP_GAP * SAMPLE_RATE);
      }
    }
  }
  return buf;
}

function mix(pcm: Int16Array, at: number, clip: Int16Array) {
  for (let i = 0; i < clip.length; i++) {
    const j = at + i;
    if (j < 0 || j >= pcm.length) continue;
    pcm[j] = clamp(pcm[j] + clip[i]);
  }
}

function mixBeep(pcm: Int16Array, at: number, freq: number, dur: number) {
  const n = Math.round(dur * SAMPLE_RATE);
  const fade = Math.round(0.008 * SAMPLE_RATE);
  for (let i = 0; i < n; i++) {
    const j = at + i;
    if (j < 0 || j >= pcm.length) continue;
    const env = Math.min(1, i / fade, (n - i) / fade);
    pcm[j] = clamp(pcm[j] + Math.round(Math.sin((2 * Math.PI * freq * i) / SAMPLE_RATE) * BEEP_AMP * env));
  }
}

const clamp = (v: number) => (v < -32768 ? -32768 : v > 32767 ? 32767 : v);

/** Linear-interpolation resample of 16-bit PCM (used once per clip when the pack loads). */
export function resample(src: Int16Array, from: number, to: number): Int16Array {
  if (from === to) return src;
  const out = new Int16Array(Math.floor((src.length * to) / from));
  for (let i = 0; i < out.length; i++) {
    const x = (i * from) / to;
    const i0 = Math.floor(x);
    const i1 = Math.min(i0 + 1, src.length - 1);
    const f = x - i0;
    out[i] = Math.round(src[i0] * (1 - f) + src[i1] * f);
  }
  return out;
}

function writeHeader(buf: Uint8Array, samples: number) {
  const dv = new DataView(buf.buffer, buf.byteOffset, HEADER);
  const ascii = (o: number, s: string) => { for (let i = 0; i < s.length; i++) dv.setUint8(o + i, s.charCodeAt(i)); };
  const dataLen = samples * 2;
  ascii(0, 'RIFF');
  dv.setUint32(4, 36 + dataLen, true);
  ascii(8, 'WAVE');
  ascii(12, 'fmt ');
  dv.setUint32(16, 16, true);
  dv.setUint16(20, 1, true); // PCM
  dv.setUint16(22, 1, true); // mono
  dv.setUint32(24, SAMPLE_RATE, true);
  dv.setUint32(28, SAMPLE_RATE * 2, true); // byte rate
  dv.setUint16(32, 2, true); // block align
  dv.setUint16(34, 16, true); // bits per sample
  ascii(36, 'data');
  dv.setUint32(40, dataLen, true);
}
