// Renders the whole workout into ONE WAV file. iOS keeps a playing <audio> element alive with the
// screen locked, while it freezes the page's JavaScript, so every cue must already be in the audio.
// Format: mono, 8-bit unsigned PCM at SAMPLE_RATE (silence = 128). ~0.66 MB per minute.

import type { Cue } from './cues';
import type { VoicePack } from './voice';

export const SAMPLE_RATE = 11025; // must match RATE in tools/make_voice_pack.py
const TAIL = 3; // seconds after the last cue ("Workout complete")
const CLIP_GAP = 0.06;
const BEEP_AMP = 90;
const HEADER = 44;

export function renderTrack(cues: Cue[], totalSec: number, pack: VoicePack): Uint8Array {
  const len = Math.ceil((totalSec + TAIL) * SAMPLE_RATE);
  const buf = new Uint8Array(HEADER + len);
  writeHeader(buf, len);
  const pcm = buf.subarray(HEADER);
  pcm.fill(128);

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

function mix(pcm: Uint8Array, at: number, clip: Uint8Array) {
  for (let i = 0; i < clip.length; i++) {
    const j = at + i;
    if (j < 0 || j >= pcm.length) continue;
    pcm[j] = clamp(pcm[j] + clip[i] - 128);
  }
}

function mixBeep(pcm: Uint8Array, at: number, freq: number, dur: number) {
  const n = Math.round(dur * SAMPLE_RATE);
  const fade = Math.round(0.008 * SAMPLE_RATE);
  for (let i = 0; i < n; i++) {
    const j = at + i;
    if (j < 0 || j >= pcm.length) continue;
    const env = Math.min(1, i / fade, (n - i) / fade);
    const v = Math.sin((2 * Math.PI * freq * i) / SAMPLE_RATE) * BEEP_AMP * env;
    pcm[j] = clamp(pcm[j] + Math.round(v));
  }
}

const clamp = (v: number) => (v < 0 ? 0 : v > 255 ? 255 : v);

function writeHeader(buf: Uint8Array, dataLen: number) {
  const dv = new DataView(buf.buffer, buf.byteOffset, HEADER);
  const ascii = (o: number, s: string) => { for (let i = 0; i < s.length; i++) dv.setUint8(o + i, s.charCodeAt(i)); };
  ascii(0, 'RIFF');
  dv.setUint32(4, 36 + dataLen, true);
  ascii(8, 'WAVE');
  ascii(12, 'fmt ');
  dv.setUint32(16, 16, true);
  dv.setUint16(20, 1, true); // PCM
  dv.setUint16(22, 1, true); // mono
  dv.setUint32(24, SAMPLE_RATE, true);
  dv.setUint32(28, SAMPLE_RATE, true); // byte rate
  dv.setUint16(32, 1, true); // block align
  dv.setUint16(34, 8, true); // bits per sample
  ascii(36, 'data');
  dv.setUint32(40, dataLen, true);
}
