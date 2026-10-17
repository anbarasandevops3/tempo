import { describe, expect, it } from 'vitest';
import { backupFromJson, backupToJson, BackupError, merge } from './backup';
import { TEMPLATES } from './templates';
import type { HistoryEntry } from './history';

const entry = (id: string, doneSec: number, status: HistoryEntry['status'] = 'completed'): HistoryEntry => ({
  id, workoutId: 'w', name: 'X', startedAt: Number(id.replace(/\D/g, '')) || 1, plannedSec: 600, doneSec, status,
});

describe('backup', () => {
  it('round-trips', () => {
    const w = { ...TEMPLATES[1].make(), updatedAt: 100 };
    const h = entry('h1', 300, 'stopped');
    const back = backupFromJson(backupToJson({ workouts: [w], history: [h] }));
    expect(back.workouts).toEqual([w]);
    expect(back.history).toEqual([h]);
  });

  it('merges without losing anything', () => {
    const w = { ...TEMPLATES[1].make(), updatedAt: 100 };
    const renamed = { ...w, name: 'Renamed', updatedAt: 200 };
    const merged = merge(
      { workouts: [w], history: [entry('h2', 300, 'stopped')] },
      { workouts: [renamed], history: [entry('h2', 600), entry('h1', 100)] },
    );
    expect(merged.workouts.map((x) => x.name)).toEqual(['Renamed']);
    expect(merged.history.map((e) => [e.id, e.doneSec])).toEqual([['h1', 100], ['h2', 600]]);
    // older data never overwrites newer
    expect(merge(merged, { workouts: [w], history: [entry('h2', 300, 'stopped')] }).workouts[0].name).toBe('Renamed');
  });

  it('reads an Android backup (extra cue fields kept)', () => {
    const android = `{"app":"tempo","format":1,"exportedAt":1,"workouts":[{"id":"a","name":"Droid","updatedAt":5,
      "cues":{"getReady":10,"countdownBeeps":3,"halfway":true,"tenSeconds":true,"announceRounds":true,"vibrate":false,"speakNames":true},
      "nodes":[{"type":"step","id":"s","kind":"work","seconds":40,"label":"Burpees"}]}],"history":[]}`;
    const b = backupFromJson(android);
    expect(b.workouts[0].nodes[0]).toMatchObject({ label: 'Burpees' });
    expect((b.workouts[0].cues as unknown as { vibrate: boolean }).vibrate).toBe(false);
  });

  it('rejects other files', () => {
    expect(() => backupFromJson('{"hello":1}')).toThrow(BackupError);
    expect(() => backupFromJson('not json')).toThrow(BackupError);
    expect(() => backupFromJson('{"app":"tempo","format":99}')).toThrow(/newer version/);
  });
});
