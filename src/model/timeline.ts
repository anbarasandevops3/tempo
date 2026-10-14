import { KIND_LABEL, RESTING_KINDS, type StepKind, type Workout, type WorkoutNode } from './types';

/** One continuous stretch of time on the timeline. */
export interface Segment {
  index: number;
  kind: StepKind | 'ready';
  title: string;
  start: number; // seconds from track start
  duration: number;
  /** Innermost repeat this segment belongs to, if any. */
  round?: number;
  rounds?: number;
  /** True on the first segment of a round. */
  roundStart?: boolean;
}

export interface Timeline {
  segments: Segment[];
  total: number;
}

export function compile(workout: Workout): Timeline {
  const segments: Segment[] = [];
  let t = 0;
  const push = (s: Omit<Segment, 'index' | 'start'>) => {
    if (s.duration <= 0) return;
    segments.push({ ...s, index: segments.length, start: t });
    t += s.duration;
  };

  if (workout.cues.getReady > 0) {
    push({ kind: 'ready', title: 'Get ready', duration: workout.cues.getReady });
  }

  const walk = (nodes: WorkoutNode[], round?: { n: number; of: number }) => {
    let first = true;
    for (const node of nodes) {
      if (node.type === 'step') {
        push({
          kind: node.kind,
          title: node.label?.trim() || KIND_LABEL[node.kind],
          duration: Math.max(0, Math.round(node.seconds)),
          round: round?.n,
          rounds: round?.of,
          roundStart: round ? first : undefined,
        });
        first = false;
      } else {
        const times = Math.max(0, Math.floor(node.times));
        for (let n = 1; n <= times; n++) {
          let children = node.children;
          const last = children[children.length - 1];
          if (n === times && node.skipLastRest && last?.type === 'step' && RESTING_KINDS.has(last.kind)) {
            children = children.slice(0, -1);
          }
          walk(children, { n, of: times });
        }
        first = false;
      }
    }
  };
  walk(workout.nodes);

  return { segments, total: t };
}

/** Segment playing at time t (clamped to the last one). */
export function segmentAt(tl: Timeline, t: number): Segment | undefined {
  const segs = tl.segments;
  if (segs.length === 0) return undefined;
  let lo = 0;
  let hi = segs.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (segs[mid].start <= t) lo = mid;
    else hi = mid - 1;
  }
  return segs[lo];
}

export function workoutSeconds(workout: Workout): number {
  return compile(workout).total;
}

export function formatClock(sec: number): string {
  const s = Math.max(0, Math.ceil(sec - 1e-6));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m);
  return `${h > 0 ? h + ':' : ''}${mm}:${String(r).padStart(2, '0')}`;
}
