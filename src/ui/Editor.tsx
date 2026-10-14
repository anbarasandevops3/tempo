import { useState } from 'preact/hooks';
import { KIND_LABEL, newId, STEP_KINDS, type Step, type StepKind, type Workout, type WorkoutNode } from '../model/types';
import { appendNode, cloneNode, insertAfter, moveNode, parseDuration, removeNode, updateNode } from '../model/edit';
import { compile, formatClock } from '../model/timeline';

interface Props {
  initial: Workout;
  isNew: boolean;
  onSave(w: Workout): void;
  onDelete(): void;
  onCancel(): void;
}

const newStep = (kind: StepKind = 'run', seconds = 60): Step => ({ type: 'step', id: newId(), kind, seconds });

export function Editor(p: Props) {
  const [w, setW] = useState<Workout>(p.initial);
  const setNodes = (fn: (n: WorkoutNode[]) => WorkoutNode[]) => setW((x) => ({ ...x, nodes: fn(x.nodes) }));
  const ops: NodeOps = {
    update: (id, fn) => setNodes((n) => updateNode(n, id, fn)),
    remove: (id) => setNodes((n) => removeNode(n, id)),
    move: (id, dir) => setNodes((n) => moveNode(n, id, dir)),
    duplicate: (node) => setNodes((n) => insertAfter(n, node.id, cloneNode(node, newId))),
    addStep: (parent) => setNodes((n) => appendNode(n, parent, newStep())),
    addRepeat: (parent) => setNodes((n) => appendNode(n, parent, {
      type: 'repeat', id: newId(), times: 4, children: [newStep('work', 30), newStep('rest', 30)],
    })),
  };
  const total = compile(w).total;
  const cues = w.cues;
  const setCue = <K extends keyof Workout['cues']>(k: K, v: Workout['cues'][K]) => setW((x) => ({ ...x, cues: { ...x.cues, [k]: v } }));

  return (
    <div class="screen">
      <header class="bar">
        <button class="btn ghost" onClick={p.onCancel}>Cancel</button>
        <span class="muted">{formatClock(total)}</span>
        <button class="btn primary" onClick={() => p.onSave({ ...w, name: w.name.trim() || 'Workout', updatedAt: Date.now() })}>Save</button>
      </header>

      <input
        class="title-input"
        value={w.name}
        onInput={(e) => setW({ ...w, name: e.currentTarget.value })}
        aria-label="Workout name"
      />

      <NodeList nodes={w.nodes} parent={null} ops={ops} />

      <h3>Cues</h3>
      <div class="card settings">
        <label class="setting">
          <span>Get ready</span>
          <select value={cues.getReady} onChange={(e) => setCue('getReady', Number(e.currentTarget.value))}>
            {[0, 5, 10, 15, 30].map((s) => <option value={s}>{s === 0 ? 'Off' : `${s} s`}</option>)}
          </select>
        </label>
        <label class="setting">
          <span>Countdown beeps</span>
          <select value={cues.countdownBeeps} onChange={(e) => setCue('countdownBeeps', Number(e.currentTarget.value))}>
            {[0, 3, 5].map((s) => <option value={s}>{s === 0 ? 'Off' : `Last ${s} s`}</option>)}
          </select>
        </label>
        <Toggle label="Say “Round 2 of 8”" value={cues.announceRounds} onChange={(v) => setCue('announceRounds', v)} />
        <Toggle label="Say “Halfway” (steps ≥ 1 min)" value={cues.halfway} onChange={(v) => setCue('halfway', v)} />
        <Toggle label="Say “Ten seconds” (steps ≥ 30 s)" value={cues.tenSeconds} onChange={(v) => setCue('tenSeconds', v)} />
      </div>

      {!p.isNew && (
        <button class="btn danger wide" onClick={() => { if (confirm(`Delete “${w.name}”?`)) p.onDelete(); }}>
          Delete workout
        </button>
      )}
    </div>
  );
}

interface NodeOps {
  update(id: string, fn: (n: WorkoutNode) => WorkoutNode): void;
  remove(id: string): void;
  move(id: string, dir: -1 | 1): void;
  duplicate(node: WorkoutNode): void;
  addStep(parent: string | null): void;
  addRepeat(parent: string | null): void;
}

function NodeList({ nodes, parent, ops }: { nodes: WorkoutNode[]; parent: string | null; ops: NodeOps }) {
  return (
    <div class="nodes">
      {nodes.map((n) => (n.type === 'step'
        ? <StepRow key={n.id} step={n} ops={ops} />
        : (
          <div class="repeat" key={n.id}>
            <div class="repeat-head">
              <span>Repeat</span>
              <Stepper value={n.times} min={1} max={99} format={(v) => `${v}×`}
                onChange={(v) => ops.update(n.id, (x) => ({ ...x, times: v }))} />
              <NodeMenu node={n} ops={ops} />
            </div>
            <NodeList nodes={n.children} parent={n.id} ops={ops} />
            <label class="setting small">
              <input type="checkbox" checked={!!n.skipLastRest}
                onChange={(e) => ops.update(n.id, (x) => ({ ...x, skipLastRest: e.currentTarget.checked }))} />
              <span>Skip the last rest on the final round</span>
            </label>
          </div>
        )))}
      <div class="row add">
        <button class="btn" onClick={() => ops.addStep(parent)}>+ Step</button>
        <button class="btn" onClick={() => ops.addRepeat(parent)}>+ Repeat</button>
      </div>
    </div>
  );
}

function StepRow({ step, ops }: { step: Step; ops: NodeOps }) {
  return (
    <div class={`step kind-${step.kind}`}>
      <div class="step-top">
        <select value={step.kind} aria-label="Step type"
          onChange={(e) => ops.update(step.id, (x) => ({ ...x, kind: e.currentTarget.value as StepKind }))}>
          {STEP_KINDS.map((k) => <option value={k}>{KIND_LABEL[k]}</option>)}
        </select>
        <DurationField seconds={step.seconds} onChange={(s) => ops.update(step.id, (x) => ({ ...x, seconds: s }))} />
        <NodeMenu node={step} ops={ops} />
      </div>
      <input class="label-input" placeholder="Name (optional, e.g. Burpees)" value={step.label ?? ''}
        onInput={(e) => ops.update(step.id, (x) => ({ ...x, label: e.currentTarget.value }))} />
    </div>
  );
}

function NodeMenu({ node, ops }: { node: WorkoutNode; ops: NodeOps }) {
  return (
    <div class="node-menu">
      <button class="icon" aria-label="Move up" onClick={() => ops.move(node.id, -1)}>↑</button>
      <button class="icon" aria-label="Move down" onClick={() => ops.move(node.id, 1)}>↓</button>
      <button class="icon" aria-label="Duplicate" onClick={() => ops.duplicate(node)}>⧉</button>
      <button class="icon" aria-label="Remove" onClick={() => ops.remove(node.id)}>✕</button>
    </div>
  );
}

function DurationField({ seconds, onChange }: { seconds: number; onChange(s: number): void }) {
  const [text, setText] = useState<string | null>(null);
  const stepBy = seconds >= 120 ? 30 : 5;
  return (
    <div class="duration">
      <button class="icon" aria-label="Shorter" onClick={() => onChange(Math.max(5, seconds - stepBy))}>−</button>
      <input
        inputMode="numeric"
        aria-label="Duration (m:ss)"
        value={text ?? formatClock(seconds)}
        onFocus={(e) => { setText(formatClock(seconds)); e.currentTarget.select(); }}
        onInput={(e) => setText(e.currentTarget.value)}
        onBlur={() => {
          const s = text === null ? undefined : parseDuration(text);
          if (s !== undefined && s > 0) onChange(Math.min(s, 6 * 3600));
          setText(null);
        }}
      />
      <button class="icon" aria-label="Longer" onClick={() => onChange(seconds + stepBy)}>+</button>
    </div>
  );
}

function Stepper(p: { value: number; min: number; max: number; format(v: number): string; onChange(v: number): void }) {
  return (
    <div class="duration">
      <button class="icon" aria-label="Fewer" onClick={() => p.onChange(Math.max(p.min, p.value - 1))}>−</button>
      <span class="stepper-value">{p.format(p.value)}</span>
      <button class="icon" aria-label="More" onClick={() => p.onChange(Math.min(p.max, p.value + 1))}>+</button>
    </div>
  );
}

function Toggle(p: { label: string; value: boolean; onChange(v: boolean): void }) {
  return (
    <label class="setting">
      <span>{p.label}</span>
      <input type="checkbox" class="switch" checked={p.value} onChange={(e) => p.onChange(e.currentTarget.checked)} />
    </label>
  );
}
