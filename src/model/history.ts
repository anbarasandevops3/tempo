// Workout history: one entry per started workout, kept up to date while it plays.

export interface HistoryEntry {
  id: string;
  workoutId: string;
  name: string;
  startedAt: number; // epoch ms
  plannedSec: number;
  /** Furthest point reached in the track, in seconds (from audio.currentTime). */
  doneSec: number;
  /** 'active' = still playing, or the app was closed mid-workout. */
  status: 'active' | 'completed' | 'stopped';
}

/** Entries shorter than this when stopped are treated as accidental starts and dropped. */
export const MIN_LOG_SEC = 30;

/** An 'active' entry that isn't the live/resumable session counts as stopped. */
export function effectiveStatus(e: HistoryEntry, liveEntryId?: string): 'active' | 'completed' | 'stopped' {
  if (e.status === 'active' && e.id !== liveEntryId) return 'stopped';
  return e.status;
}

export function dayKey(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Monday 00:00 local time of the week containing ms. */
export function weekStart(ms: number): number {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return d.getTime();
}

export interface Totals {
  workouts: number;
  seconds: number;
}

export function totals(entries: HistoryEntry[], from: number, to: number): Totals {
  let workouts = 0;
  let seconds = 0;
  for (const e of entries) {
    if (e.startedAt >= from && e.startedAt < to) {
      workouts++;
      seconds += e.doneSec;
    }
  }
  return { workouts, seconds };
}

/** Minutes trained per week for the last `weeks` weeks, oldest first. */
export function weeklyMinutes(entries: HistoryEntry[], now: number, weeks: number): { start: number; minutes: number }[] {
  const out: { start: number; minutes: number }[] = [];
  let start = weekStart(now);
  for (let i = 0; i < weeks; i++) {
    const next = addDays(start, 7);
    out.unshift({ start, minutes: Math.round(totals(entries, start, next).seconds / 60) });
    start = addDays(start, -7);
  }
  return out;
}

/** Consecutive days with a workout, ending today (or yesterday, if nothing yet today). */
export function streakDays(entries: HistoryEntry[], now: number): number {
  const days = new Set(entries.map((e) => dayKey(e.startedAt)));
  let day = startOfDay(now);
  if (!days.has(dayKey(day))) day = addDays(day, -1);
  let n = 0;
  while (days.has(dayKey(day))) {
    n++;
    day = addDays(day, -1);
  }
  return n;
}

/** Newest first, grouped by local day. */
export function groupByDay(entries: HistoryEntry[]): { day: string; entries: HistoryEntry[] }[] {
  const sorted = [...entries].sort((a, b) => b.startedAt - a.startedAt);
  const groups: { day: string; entries: HistoryEntry[] }[] = [];
  for (const e of sorted) {
    const day = dayKey(e.startedAt);
    if (groups.at(-1)?.day !== day) groups.push({ day, entries: [] });
    groups.at(-1)!.entries.push(e);
  }
  return groups;
}

export function lastDone(entries: HistoryEntry[], workoutId: string): number | undefined {
  let best: number | undefined;
  for (const e of entries) if (e.workoutId === workoutId && (best === undefined || e.startedAt > best)) best = e.startedAt;
  return best;
}

/** "today", "yesterday", "3 days ago", "2 weeks ago" */
export function relativeDay(ms: number, now: number): string {
  const days = Math.round((startOfDay(now) - startOfDay(ms)) / 86_400_000);
  if (days <= 0) return 'today';
  if (days === 1) return 'yesterday';
  if (days < 14) return `${days} days ago`;
  if (days < 60) return `${Math.floor(days / 7)} weeks ago`;
  return `${Math.floor(days / 30)} months ago`;
}

export function startOfDay(ms: number): number {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Calendar-safe (DST) day arithmetic. */
export function addDays(ms: number, n: number): number {
  const d = new Date(ms);
  d.setDate(d.getDate() + n);
  return d.getTime();
}
