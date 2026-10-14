import { describe, expect, it } from 'vitest';
import {
  addDays, effectiveStatus, groupByDay, lastDone, relativeDay, streakDays, totals, weekStart, weeklyMinutes,
  type HistoryEntry,
} from './history';

const at = (y: number, m: number, d: number, h = 7) => new Date(y, m - 1, d, h).getTime();
let n = 0;
const entry = (startedAt: number, doneSec = 600, extra: Partial<HistoryEntry> = {}): HistoryEntry => ({
  id: `e${n++}`, workoutId: 'w1', name: 'Intervals', startedAt, plannedSec: 600, doneSec, status: 'completed', ...extra,
});

// Wednesday 2026-10-14
const NOW = at(2026, 10, 14, 18);

describe('history', () => {
  it('weeks start on Monday', () => {
    expect(new Date(weekStart(NOW)).getDate()).toBe(12);
    expect(new Date(weekStart(at(2026, 10, 12, 0))).getDate()).toBe(12);
    expect(new Date(weekStart(at(2026, 10, 11, 23))).getDate()).toBe(5);
  });

  it('sums the week', () => {
    const list = [entry(at(2026, 10, 12), 1200), entry(at(2026, 10, 14), 600), entry(at(2026, 10, 11), 900)];
    expect(totals(list, weekStart(NOW), addDays(weekStart(NOW), 7))).toEqual({ workouts: 2, seconds: 1800 });
  });

  it('weekly minutes, oldest first', () => {
    const list = [entry(at(2026, 10, 13), 1200), entry(at(2026, 10, 6), 600)];
    expect(weeklyMinutes(list, NOW, 3).map((w) => w.minutes)).toEqual([0, 10, 20]);
  });

  it('counts the streak from today or yesterday', () => {
    expect(streakDays([entry(at(2026, 10, 14)), entry(at(2026, 10, 13)), entry(at(2026, 10, 11))], NOW)).toBe(2);
    expect(streakDays([entry(at(2026, 10, 13)), entry(at(2026, 10, 12))], NOW)).toBe(2);
    expect(streakDays([entry(at(2026, 10, 12))], NOW)).toBe(0);
    expect(streakDays([], NOW)).toBe(0);
  });

  it('groups by day, newest first', () => {
    const g = groupByDay([entry(at(2026, 10, 13, 7)), entry(at(2026, 10, 14, 7)), entry(at(2026, 10, 14, 19))]);
    expect(g.map((x) => [x.day, x.entries.length])).toEqual([['2026-10-14', 2], ['2026-10-13', 1]]);
    expect(g[0].entries[0].startedAt).toBe(at(2026, 10, 14, 19));
  });

  it('treats an abandoned active entry as stopped', () => {
    const e = entry(NOW, 100, { status: 'active' });
    expect(effectiveStatus(e, e.id)).toBe('active');
    expect(effectiveStatus(e, undefined)).toBe('stopped');
  });

  it('last done and relative days', () => {
    expect(lastDone([entry(at(2026, 10, 1)), entry(at(2026, 10, 9))], 'w1')).toBe(at(2026, 10, 9));
    expect(lastDone([], 'w1')).toBeUndefined();
    expect(relativeDay(at(2026, 10, 14, 6), NOW)).toBe('today');
    expect(relativeDay(at(2026, 10, 13, 23), NOW)).toBe('yesterday');
    expect(relativeDay(at(2026, 10, 9), NOW)).toBe('5 days ago');
    expect(relativeDay(at(2026, 9, 20), NOW)).toBe('3 weeks ago');
  });
});
