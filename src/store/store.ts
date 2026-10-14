// Workouts and the in-progress session, saved in localStorage on the phone.

import type { Workout } from '../model/types';
import { TEMPLATES } from '../model/templates';
import { MIN_LOG_SEC, type HistoryEntry } from '../model/history';

const WORKOUTS = 'tempo.workouts.v1';
const SESSION = 'tempo.session.v1';
const HISTORY = 'tempo.history.v1';
const HISTORY_MAX = 5000;

export interface SavedSession {
  workoutId: string;
  position: number;
  savedAt: number;
  /** History entry this run is logged under; resuming keeps updating it. */
  entryId?: string;
}

function read<T>(key: string): T | undefined {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : undefined;
  } catch {
    return undefined;
  }
}

function write(key: string, value: unknown) {
  try {
    if (value === undefined) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify(value));
  } catch { /* private mode or storage full */ }
}

export function loadWorkouts(): Workout[] {
  const saved = read<Workout[]>(WORKOUTS);
  if (saved) return saved;
  const seeded = TEMPLATES.slice(0, 3).map((t) => t.make());
  write(WORKOUTS, seeded);
  return seeded;
}

export function saveWorkouts(list: Workout[]) {
  write(WORKOUTS, list);
}

export function loadSession(): SavedSession | undefined {
  const s = read<SavedSession>(SESSION);
  // Older than 3 hours: not worth resuming.
  if (s && Date.now() - s.savedAt < 3 * 3600_000) return s;
  write(SESSION, undefined);
  return undefined;
}

export function saveSession(s: SavedSession | undefined) {
  write(SESSION, s);
}

export function loadHistory(): HistoryEntry[] {
  return read<HistoryEntry[]>(HISTORY) ?? [];
}

function saveHistory(list: HistoryEntry[]) {
  write(HISTORY, list.length > HISTORY_MAX ? list.slice(-HISTORY_MAX) : list);
}

export function addHistory(e: HistoryEntry) {
  saveHistory([...loadHistory(), e]);
}

/** Update an entry's progress/status. doneSec is time actually played, capped at the plan. */
export function updateHistory(id: string, patch: Partial<Pick<HistoryEntry, 'doneSec' | 'status'>>) {
  const list = loadHistory();
  const e = list.find((x) => x.id === id);
  if (!e) return;
  if (patch.doneSec !== undefined) e.doneSec = Math.min(Math.round(patch.doneSec), e.plannedSec);
  if (patch.status) e.status = patch.status;
  saveHistory(list);
}

/** Close a run that was stopped: keep it if it lasted long enough, else drop it as an accidental start. */
export function finishStopped(id: string, doneSec: number) {
  updateHistory(id, { doneSec });
  const list = loadHistory();
  const e = list.find((x) => x.id === id);
  if (!e) return;
  if (e.doneSec < MIN_LOG_SEC) saveHistory(list.filter((x) => x.id !== id));
  else updateHistory(id, { status: 'stopped' });
}

export function deleteHistory(id: string) {
  saveHistory(loadHistory().filter((x) => x.id !== id));
}
