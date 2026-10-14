// Workouts and the in-progress session, saved in localStorage on the phone.

import type { Workout } from '../model/types';
import { TEMPLATES } from '../model/templates';

const WORKOUTS = 'tempo.workouts.v1';
const SESSION = 'tempo.session.v1';

export interface SavedSession {
  workoutId: string;
  position: number;
  savedAt: number;
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
