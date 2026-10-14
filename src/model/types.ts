// Workout = a tree of steps and repeats. Compiled into a flat timeline (timeline.ts) before playing.

export const STEP_KINDS = [
  'warmup', 'run', 'jog', 'walk', 'sprint', 'work', 'rest', 'recover', 'cooldown', 'stretch',
] as const;
export type StepKind = (typeof STEP_KINDS)[number];

export const KIND_LABEL: Record<StepKind, string> = {
  warmup: 'Warm up', run: 'Run', jog: 'Jog', walk: 'Walk', sprint: 'Sprint',
  work: 'Work', rest: 'Rest', recover: 'Recover', cooldown: 'Cool down', stretch: 'Stretch',
};

export interface Step {
  type: 'step';
  id: string;
  kind: StepKind;
  /** Shown on screen instead of the kind label, e.g. "Burpees". The voice still says the kind. */
  label?: string;
  seconds: number;
}

export interface Repeat {
  type: 'repeat';
  id: string;
  times: number;
  children: WorkoutNode[];
  /** Drop the final child on the last round when it is a rest/recover/walk (e.g. Tabata). */
  skipLastRest?: boolean;
}

export type WorkoutNode = Step | Repeat;

export interface CueSettings {
  /** Seconds of "Get ready" before the first step. 0 = none. */
  getReady: number;
  /** Beeps in the last N seconds of each step. */
  countdownBeeps: number;
  /** Voice "Halfway" on steps of 60 s or more. */
  halfway: boolean;
  /** Voice "Ten seconds" on steps of 30 s or more. */
  tenSeconds: boolean;
  /** Voice "Round 2 of 8" / "Last round" at the start of each round. */
  announceRounds: boolean;
}

export interface Workout {
  id: string;
  name: string;
  nodes: WorkoutNode[];
  cues: CueSettings;
  updatedAt: number;
}

export const DEFAULT_CUES: CueSettings = {
  getReady: 10,
  countdownBeeps: 3,
  halfway: true,
  tenSeconds: true,
  announceRounds: true,
};

export const RESTING_KINDS: ReadonlySet<StepKind> = new Set(['rest', 'recover', 'walk']);

export function newId(): string {
  return Math.random().toString(36).slice(2, 10);
}
