// Backup file shared with the Android app (docs/backup-format.md). Pure, tested.

import type { HistoryEntry } from './history';
import type { Workout } from './types';

export const BACKUP_FORMAT = 1;

export interface Backup {
  workouts: Workout[];
  history: HistoryEntry[];
}

export function backupToJson(b: Backup, now = Date.now()): string {
  return JSON.stringify({ app: 'tempo', format: BACKUP_FORMAT, exportedAt: now, workouts: b.workouts, history: b.history }, null, 2);
}

export class BackupError extends Error {}

export function backupFromJson(text: string): Backup {
  let o: unknown;
  try {
    o = JSON.parse(text);
  } catch {
    throw new BackupError("That file isn't a Tempo backup.");
  }
  const obj = o as { app?: unknown; format?: unknown; workouts?: unknown; history?: unknown };
  if (!obj || obj.app !== 'tempo') throw new BackupError("That file isn't a Tempo backup.");
  if (typeof obj.format === 'number' && obj.format > BACKUP_FORMAT) {
    throw new BackupError('This backup is from a newer version of Tempo. Update the app first.');
  }
  const arr = (v: unknown) => (Array.isArray(v) ? v : []);
  return {
    workouts: arr(obj.workouts).filter((w): w is Workout => !!w && typeof w.id === 'string' && Array.isArray(w.nodes)),
    history: arr(obj.history).filter((e): e is HistoryEntry => !!e && typeof e.id === 'string' && typeof e.startedAt === 'number'),
  };
}

/** Never wipes: newer workout wins (updatedAt), longer history entry wins (doneSec, then completed). */
export function merge(current: Backup, incoming: Backup): Backup {
  const workouts = new Map(current.workouts.map((w) => [w.id, w]));
  for (const w of incoming.workouts) {
    const old = workouts.get(w.id);
    if (!old || (w.updatedAt ?? 0) > (old.updatedAt ?? 0)) workouts.set(w.id, w);
  }
  const history = new Map(current.history.map((e) => [e.id, e]));
  for (const e of incoming.history) {
    const old = history.get(e.id);
    const better = !old || e.doneSec > old.doneSec ||
      (e.doneSec === old.doneSec && e.status === 'completed' && old.status !== 'completed');
    if (better) history.set(e.id, e);
  }
  return {
    workouts: [...workouts.values()],
    history: [...history.values()].sort((a, b) => a.startedAt - b.startedAt),
  };
}
