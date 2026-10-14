import { useState } from 'preact/hooks';
import { DEFAULT_CUES, newId, type Workout } from '../model/types';
import { compile, formatClock } from '../model/timeline';
import { TEMPLATES } from '../model/templates';
import { lastDone, relativeDay } from '../model/history';
import { loadHistory } from '../store/store';

interface Props {
  workouts: Workout[];
  resume?: { workout: Workout; position: number };
  onResume(): void;
  onDiscardResume(): void;
  onHistory(): void;
  onStart(w: Workout): void;
  onEdit(w: Workout): void;
  onCreate(w: Workout): void;
}

export function Home(p: Props) {
  const [picking, setPicking] = useState(false);
  const history = loadHistory();
  const now = Date.now();

  return (
    <div class="screen">
      <header class="bar">
        <h1>Tempo</h1>
        <div class="row">
          <button class="btn" onClick={p.onHistory}>History</button>
          <button class="btn primary" onClick={() => setPicking(true)}>+ New</button>
        </div>
      </header>

      {p.resume && (
        <div class="card resume">
          <div>
            <strong>Unfinished: {p.resume.workout.name}</strong>
            <div class="muted">Stopped at {formatClock(p.resume.position)}</div>
          </div>
          <div class="row">
            <button class="btn" onClick={p.onDiscardResume}>Discard</button>
            <button class="btn primary" onClick={p.onResume}>Resume</button>
          </div>
        </div>
      )}

      {p.workouts.length === 0 && <p class="muted center">No workouts yet. Tap “+ New”.</p>}

      <ul class="list">
        {p.workouts.map((w) => {
          const tl = compile(w);
          const last = lastDone(history, w.id);
          const count = tl.segments.filter((s) => s.kind !== 'ready').length;
          return (
            <li class="card workout" key={w.id}>
              <button class="workout-main" onClick={() => p.onStart(w)}>
                <span class="workout-name">{w.name}</span>
                <span class="muted">{formatClock(tl.total)} · {count} {count === 1 ? 'interval' : 'intervals'}</span>
                {last !== undefined && <span class="muted small">Last done {relativeDay(last, now)}</span>}
              </button>
              <button class="btn ghost" onClick={() => p.onEdit(w)} aria-label={`Edit ${w.name}`}>Edit</button>
            </li>
          );
        })}
      </ul>

      {picking && (
        <div class="sheet-backdrop" onClick={() => setPicking(false)}>
          <div class="sheet" onClick={(e) => e.stopPropagation()}>
            <h2>New workout</h2>
            <button
              class="card pick"
              onClick={() => p.onCreate({ id: newId(), name: 'My workout', nodes: [], cues: { ...DEFAULT_CUES }, updatedAt: Date.now() })}
            >
              <strong>Blank</strong>
              <span class="muted">Build it step by step</span>
            </button>
            {TEMPLATES.map((t) => (
              <button class="card pick" key={t.name} onClick={() => p.onCreate(t.make())}>
                <strong>{t.name}</strong>
                <span class="muted">{t.blurb}</span>
              </button>
            ))}
            <button class="btn wide" onClick={() => setPicking(false)}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  );
}
