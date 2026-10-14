import { DEFAULT_CUES, newId, type Repeat, type Step, type StepKind, type Workout, type WorkoutNode } from './types';

const step = (kind: StepKind, seconds: number, label?: string): Step => ({ type: 'step', id: newId(), kind, seconds, label });
const repeat = (times: number, children: WorkoutNode[], skipLastRest = false): Repeat => ({
  type: 'repeat', id: newId(), times, children, skipLastRest,
});
const workout = (name: string, nodes: WorkoutNode[]): Workout => ({
  id: newId(), name, nodes, cues: { ...DEFAULT_CUES }, updatedAt: Date.now(),
});

export interface Template {
  name: string;
  blurb: string;
  make(): Workout;
}

export const TEMPLATES: Template[] = [
  {
    name: 'Run / walk intervals',
    blurb: '5 min warm-up, 6 × (1 min run, 1:30 walk), 5 min cool-down',
    make: () => workout('Run / walk intervals', [
      step('warmup', 300), repeat(6, [step('run', 60), step('walk', 90)]), step('cooldown', 300),
    ]),
  },
  {
    name: 'Tabata',
    blurb: '8 × (20 s work, 10 s rest)',
    make: () => workout('Tabata', [repeat(8, [step('work', 20), step('rest', 10)], true)]),
  },
  {
    name: 'Dynamic warm-up',
    blurb: '6 moves, 40 s each, 5 min',
    make: () => workout('Dynamic warm-up', [
      step('jog', 60, 'Easy jog'),
      step('work', 40, 'Leg swings'),
      step('work', 40, 'Walking lunges'),
      step('work', 40, 'High knees'),
      step('work', 40, 'Butt kicks'),
      step('stretch', 40, 'Hip openers'),
      step('sprint', 40, 'Strides'),
    ]),
  },
  {
    name: 'Sprint intervals',
    blurb: '10 min jog, 8 × (30 s sprint, 90 s recover), 10 min cool-down',
    make: () => workout('Sprint intervals', [
      step('jog', 600, 'Warm-up jog'), repeat(8, [step('sprint', 30), step('recover', 90)], true), step('cooldown', 600),
    ]),
  },
  {
    name: 'HIIT 30/30',
    blurb: '10 × (30 s work, 30 s rest)',
    make: () => workout('HIIT 30/30', [repeat(10, [step('work', 30), step('rest', 30)], true)]),
  },
  {
    name: 'HIIT circuit',
    blurb: '3 rounds × 4 moves (40 s on, 20 s off), 1 min between rounds',
    make: () => workout('HIIT circuit', [
      repeat(3, [
        repeat(4, [step('work', 40), step('rest', 20)], true),
        step('recover', 60),
      ], true),
    ]),
  },
];
