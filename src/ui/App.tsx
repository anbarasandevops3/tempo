import { useState } from 'preact/hooks';
import type { Workout } from '../model/types';
import { finishStopped, loadHistory, loadSession, loadWorkouts, saveSession, saveWorkouts, type SavedSession } from '../store/store';
import { Home } from './Home';
import { Editor } from './Editor';
import { Run } from './Run';
import { History } from './History';

type Screen =
  | { name: 'home' }
  | { name: 'edit'; workout: Workout; isNew: boolean }
  | { name: 'run'; workoutId: string; resumeAt?: number; resumeEntryId?: string }
  | { name: 'history' };

export function App() {
  const [workouts, setWorkouts] = useState<Workout[]>(loadWorkouts);
  const [session, setSession] = useState<SavedSession | undefined>(loadSession);
  const [screen, setScreen] = useState<Screen>({ name: 'home' });

  const update = (list: Workout[]) => {
    setWorkouts(list);
    saveWorkouts(list);
  };
  const home = () => {
    setSession(loadSession());
    setScreen({ name: 'home' });
  };

  if (screen.name === 'edit') {
    return (
      <Editor
        initial={screen.workout}
        isNew={screen.isNew}
        onSave={(w) => {
          const exists = workouts.some((x) => x.id === w.id);
          update(exists ? workouts.map((x) => (x.id === w.id ? w : x)) : [w, ...workouts]);
          home();
        }}
        onDelete={() => {
          update(workouts.filter((x) => x.id !== screen.workout.id));
          home();
        }}
        onCancel={home}
      />
    );
  }

  if (screen.name === 'history') {
    return <History liveEntryId={session?.entryId} onBack={home} />;
  }

  if (screen.name === 'run') {
    const w = workouts.find((x) => x.id === screen.workoutId);
    if (w) return <Run workout={w} resumeAt={screen.resumeAt} resumeEntryId={screen.resumeEntryId} onExit={home} />;
  }

  const resumable = session && workouts.find((x) => x.id === session.workoutId);
  return (
    <Home
      workouts={workouts}
      resume={resumable && session ? { workout: resumable, position: session.position } : undefined}
      onResume={() => session && setScreen({
        name: 'run', workoutId: session.workoutId, resumeAt: session.position, resumeEntryId: session.entryId,
      })}
      onDiscardResume={() => {
        if (session?.entryId) finishStopped(session.entryId, loadHistory().find((e) => e.id === session.entryId)?.doneSec ?? 0);
        saveSession(undefined);
        setSession(undefined);
      }}
      onHistory={() => setScreen({ name: 'history' })}
      onStart={(w) => setScreen({ name: 'run', workoutId: w.id })}
      onEdit={(w) => setScreen({ name: 'edit', workout: w, isNew: false })}
      onCreate={(w) => setScreen({ name: 'edit', workout: w, isNew: true })}
    />
  );
}
