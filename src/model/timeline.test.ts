import { describe, expect, it } from 'vitest';
import { compile, formatClock, segmentAt } from './timeline';
import { DEFAULT_CUES, type Workout, type WorkoutNode } from './types';
import { TEMPLATES } from './templates';
import { buildCues, durationClips } from '../audio/cues';
import { renderTrack, SAMPLE_RATE } from '../audio/render';
import { makePack } from '../audio/voice';

const w = (nodes: WorkoutNode[], getReady = 0): Workout => ({
  id: 'w', name: 'T', nodes, cues: { ...DEFAULT_CUES, getReady }, updatedAt: 0,
});

describe('compile', () => {
  it('flattens repeats and keeps segments back to back', () => {
    const tl = compile(w([
      { type: 'step', id: 'a', kind: 'warmup', seconds: 60 },
      { type: 'repeat', id: 'r', times: 3, children: [
        { type: 'step', id: 'b', kind: 'run', seconds: 30 },
        { type: 'step', id: 'c', kind: 'walk', seconds: 20 },
      ] },
    ], 10));
    expect(tl.segments.map((s) => s.kind)).toEqual(['ready', 'warmup', 'run', 'walk', 'run', 'walk', 'run', 'walk']);
    expect(tl.total).toBe(10 + 60 + 3 * 50);
    tl.segments.forEach((s, i) => {
      if (i > 0) expect(s.start).toBe(tl.segments[i - 1].start + tl.segments[i - 1].duration);
    });
    expect(tl.segments[4]).toMatchObject({ round: 2, rounds: 3, roundStart: true });
    expect(tl.segments[5].roundStart).toBe(false);
  });

  it('skips the trailing rest on the last round', () => {
    const tabata = TEMPLATES.find((t) => t.name === 'Tabata')!.make();
    const tl = compile(tabata);
    expect(tl.total).toBe(DEFAULT_CUES.getReady + 8 * 30 - 10);
    expect(tl.segments.at(-1)!.kind).toBe('work');
  });

  it('drops zero-length steps', () => {
    const tl = compile(w([{ type: 'step', id: 'a', kind: 'run', seconds: 0 }]));
    expect(tl.segments).toHaveLength(0);
  });

  it('finds the segment at a time', () => {
    const tl = compile(w([
      { type: 'step', id: 'a', kind: 'run', seconds: 10 },
      { type: 'step', id: 'b', kind: 'walk', seconds: 10 },
    ]));
    expect(segmentAt(tl, 0)!.kind).toBe('run');
    expect(segmentAt(tl, 9.99)!.kind).toBe('run');
    expect(segmentAt(tl, 10)!.kind).toBe('walk');
    expect(segmentAt(tl, 99)!.kind).toBe('walk');
  });
});

describe('cues', () => {
  it('speaks durations', () => {
    expect(durationClips(90)).toEqual(['n1', 'minute', 'n30', 'seconds']);
    expect(durationClips(120)).toEqual(['n2', 'minutes']);
    expect(durationClips(1)).toEqual(['n1', 'second']);
  });

  it('announces each step, counts down and finishes', () => {
    const tl = compile(w([{ type: 'repeat', id: 'r', times: 2, children: [
      { type: 'step', id: 'a', kind: 'run', seconds: 60 },
    ] }]));
    const cues = buildCues(tl, DEFAULT_CUES);
    const voices = cues.filter((c) => c.type === 'voice').map((c) => (c.type === 'voice' ? c.clips.join(' ') : ''));
    expect(voices).toEqual([
      'round n1 of n2 run n1 minute', 'halfway', 'ten_seconds',
      'last_round run n1 minute', 'halfway', 'ten_seconds', 'complete',
    ]);
    const ticks = cues.filter((c) => c.type === 'beep' && c.freq === 880).map((c) => c.at);
    expect(ticks).toEqual([57, 58, 59, 117, 118, 119]);
    // sorted
    expect(cues.map((c) => c.at)).toEqual([...cues.map((c) => c.at)].sort((a, b) => a - b));
  });
});

describe('render', () => {
  it('writes a valid 8-bit WAV the length of the workout plus tail', () => {
    const pack = makePack({ sampleRate: SAMPLE_RATE, clips: { run: [0, 4] } }, new Uint8Array([200, 200, 200, 200]));
    const tl = compile(w([{ type: 'step', id: 'a', kind: 'run', seconds: 20 }]));
    const wav = renderTrack(buildCues(tl, DEFAULT_CUES), tl.total, pack);
    const dv = new DataView(wav.buffer);
    expect(String.fromCharCode(...wav.subarray(0, 4))).toBe('RIFF');
    expect(dv.getUint32(24, true)).toBe(SAMPLE_RATE);
    expect(dv.getUint32(40, true)).toBe(wav.length - 44);
    expect(wav.length - 44).toBe(Math.ceil((20 + 3) * SAMPLE_RATE));
    // the "run" clip is mixed in just after the start beep
    const at = 44 + Math.round(0.55 * SAMPLE_RATE);
    expect(Array.from(wav.subarray(at, at + 4))).toEqual([200, 200, 200, 200]);
    // silence elsewhere
    expect(wav[44 + 10 * SAMPLE_RATE]).toBe(128);
  });
});

describe('formatClock', () => {
  it('formats', () => {
    expect(formatClock(0)).toBe('0:00');
    expect(formatClock(59.2)).toBe('1:00');
    expect(formatClock(605)).toBe('10:05');
    expect(formatClock(3725)).toBe('1:02:05');
  });
});
