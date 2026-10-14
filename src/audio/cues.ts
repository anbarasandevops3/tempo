import type { Timeline } from '../model/timeline';
import type { CueSettings } from '../model/types';

/** Something audible at a fixed time on the track. */
export type Cue =
  | { at: number; type: 'voice'; clips: string[] }
  | { at: number; type: 'beep'; freq: number; dur: number };

const TICK = { freq: 880, dur: 0.15 };
const GO = { freq: 1320, dur: 0.45 };

/** Clip keys for a spoken duration, e.g. 90 -> one minute thirty seconds. */
export function durationClips(sec: number): string[] {
  const m = Math.floor(sec / 60);
  const s = Math.round(sec % 60);
  const out: string[] = [];
  if (m > 0) out.push(m < 60 ? `n${m}` : 'n59', m === 1 ? 'minute' : 'minutes');
  if (s > 0) out.push(`n${s}`, s === 1 ? 'second' : 'seconds');
  return out;
}

export function buildCues(tl: Timeline, settings: CueSettings): Cue[] {
  const cues: Cue[] = [];
  for (const seg of tl.segments) {
    const end = seg.start + seg.duration;

    // Start of segment: beep, then announce (round) + kind + duration.
    if (seg.kind === 'ready') {
      cues.push({ at: seg.start, type: 'voice', clips: ['get_ready'] });
    } else {
      cues.push({ at: seg.start, type: 'beep', ...GO });
      const clips: string[] = [];
      if (settings.announceRounds && seg.roundStart && seg.round && seg.rounds && seg.rounds > 1) {
        clips.push(...(seg.round === seg.rounds ? ['last_round'] : ['round', `n${Math.min(seg.round, 59)}`, 'of', `n${Math.min(seg.rounds, 59)}`]));
      }
      clips.push(seg.kind, ...durationClips(seg.duration));
      cues.push({ at: seg.start + GO.dur + 0.1, type: 'voice', clips });
    }

    if (settings.halfway && seg.kind !== 'ready' && seg.duration >= 60) {
      cues.push({ at: seg.start + seg.duration / 2, type: 'voice', clips: ['halfway'] });
    }
    if (settings.tenSeconds && seg.kind !== 'ready' && seg.duration >= 30) {
      cues.push({ at: end - 10, type: 'voice', clips: ['ten_seconds'] });
    }
    // Countdown ticks only when they don't collide with the start announcement.
    const n = settings.countdownBeeps;
    if (n > 0 && seg.duration >= n + 3) {
      for (let i = n; i >= 1; i--) cues.push({ at: end - i, type: 'beep', ...TICK });
    }
  }
  cues.push({ at: tl.total, type: 'beep', ...GO });
  cues.push({ at: tl.total + GO.dur + 0.1, type: 'voice', clips: ['complete'] });
  return cues.sort((a, b) => a.at - b.at);
}
