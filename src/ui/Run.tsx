import { useEffect, useRef, useState } from 'preact/hooks';
import { newId, type Workout } from '../model/types';
import { compile, formatClock, segmentAt, type Timeline } from '../model/timeline';
import { buildCues } from '../audio/cues';
import { renderTrack } from '../audio/render';
import { loadVoicePack } from '../audio/voice';
import { Player } from '../player/player';
import { addHistory, finishStopped, loadHistory, saveSession, updateHistory } from '../store/store';

interface Props {
  workout: Workout;
  resumeAt?: number;
  /** History entry of the run being resumed. */
  resumeEntryId?: string;
  onExit(): void;
}

type Phase = 'preparing' | 'ready' | 'running' | 'done' | 'error';

export function Run({ workout, resumeAt, resumeEntryId, onExit }: Props) {
  const [phase, setPhase] = useState<Phase>('preparing');
  const [error, setError] = useState('');
  const [pos, setPos] = useState(resumeAt ?? 0);
  const [playing, setPlaying] = useState(false);
  const [keepAwake, setKeepAwake] = useState(false);
  const playerRef = useRef<Player | undefined>(undefined);
  const tlRef = useRef<Timeline>(compile(workout));
  const tl = tlRef.current;
  // History: the entry this run is logged under, and time already done before a resume.
  const entryRef = useRef<string | undefined>(undefined);
  const baseDoneRef = useRef(0);
  const doneSec = () => baseDoneRef.current + (playerRef.current?.played ?? 0);

  // Build the audio track once.
  useEffect(() => {
    let cancelled = false;
    loadVoicePack()
      .then((pack) => {
        if (cancelled) return;
        const wav = renderTrack(buildCues(tl, workout.cues), tl.total, pack);
        const player = new Player(wav, tl, workout.name, {
          onEnded: () => {
            if (entryRef.current) updateHistory(entryRef.current, { doneSec: doneSec(), status: 'completed' });
            saveSession(undefined);
            setPhase('done');
          },
          onPlayState: setPlaying,
        });
        playerRef.current = player;
        if (resumeAt) {
          player.audio.addEventListener('loadedmetadata', () => player.seek(resumeAt), { once: true });
        }
        setPhase('ready');
      })
      .catch((e: unknown) => { if (!cancelled) { setError(String(e)); setPhase('error'); } });
    return () => { cancelled = true; playerRef.current?.destroy(); };
  }, []);

  // Refresh the display from the audio clock; save progress so a closed app can resume.
  useEffect(() => {
    if (phase !== 'running') return;
    let lastSeg = -1;
    let lastSave = 0;
    const tick = () => {
      const pl = playerRef.current;
      if (!pl) return;
      const t = pl.position;
      setPos(t);
      const seg = segmentAt(tl, t);
      if (seg && seg.index !== lastSeg) { lastSeg = seg.index; pl.updateMediaSession(); }
      if (Date.now() - lastSave > 2000) { lastSave = Date.now(); persist(); }
    };
    const persist = () => {
      const pl = playerRef.current;
      if (!pl || pl.position >= tl.total) return;
      saveSession({ workoutId: workout.id, position: pl.position, savedAt: Date.now(), entryId: entryRef.current });
      if (entryRef.current) updateHistory(entryRef.current, { doneSec: doneSec() });
    };
    const id = setInterval(tick, 100);
    document.addEventListener('visibilitychange', persist);
    window.addEventListener('pagehide', persist);
    return () => {
      clearInterval(id);
      document.removeEventListener('visibilitychange', persist);
      window.removeEventListener('pagehide', persist);
    };
  }, [phase]);

  // Optional screen wake lock while playing.
  useEffect(() => {
    if (!keepAwake || !playing || !('wakeLock' in navigator)) return;
    let lock: WakeLockSentinel | undefined;
    let released = false;
    const acquire = () => {
      if (document.visibilityState !== 'visible') return;
      navigator.wakeLock.request('screen').then((l) => { if (released) void l.release(); else lock = l; }).catch(() => {});
    };
    acquire();
    document.addEventListener('visibilitychange', acquire);
    return () => {
      released = true;
      document.removeEventListener('visibilitychange', acquire);
      void lock?.release();
    };
  }, [keepAwake, playing]);

  const start = () => {
    const pl = playerRef.current;
    if (!pl) return;
    pl.play().then(() => { openEntry(); setPhase('running'); }).catch((e: unknown) => { setError(String(e)); setPhase('error'); });
  };
  const openEntry = () => {
    const existing = resumeEntryId ? loadHistory().find((e) => e.id === resumeEntryId) : undefined;
    if (existing) {
      entryRef.current = existing.id;
      baseDoneRef.current = existing.doneSec;
      return;
    }
    const id = newId();
    addHistory({
      id, workoutId: workout.id, name: workout.name, startedAt: Date.now(),
      plannedSec: tl.total, doneSec: 0, status: 'active',
    });
    entryRef.current = id;
  };
  const toggle = () => {
    const pl = playerRef.current;
    if (!pl) return;
    if (pl.playing) pl.pause(); else void pl.play();
  };
  const stop = () => {
    if (phase === 'running' && !confirm('Stop this workout?')) return;
    if (entryRef.current && phase === 'running') finishStopped(entryRef.current, doneSec());
    saveSession(undefined);
    onExit();
  };

  const seg = segmentAt(tl, pos);
  const next = seg ? tl.segments[seg.index + 1] : undefined;
  const segLeft = seg ? seg.start + seg.duration - pos : 0;
  const totalLeft = tl.total - pos;
  const progress = seg && seg.duration > 0 ? Math.min(1, (pos - seg.start) / seg.duration) : 0;

  if (phase === 'done') {
    return (
      <div class="run kind-done">
        <div class="run-center">
          <div class="run-kind">Done!</div>
          <div class="run-big">{formatClock(tl.total)}</div>
          <div class="run-sub">{workout.name}</div>
        </div>
        <button class="btn wide light" onClick={onExit}>Finish</button>
      </div>
    );
  }

  return (
    <div class={`run kind-${seg?.kind ?? 'ready'}`}>
      <header class="run-top">
        <button class="btn ghost light" onClick={stop}>✕ Stop</button>
        <span>{workout.name}</span>
        <label class="awake">
          <input type="checkbox" checked={keepAwake} onChange={(e) => setKeepAwake(e.currentTarget.checked)} />
          Screen on
        </label>
      </header>

      <div class="run-center">
        {seg?.rounds && seg.rounds > 1 && <div class="run-round">Round {seg.round} / {seg.rounds}</div>}
        <div class="run-kind">{seg?.title ?? workout.name}</div>
        <div class="run-big" aria-live="off">{formatClock(segLeft)}</div>
        <div class="run-bar"><div style={{ width: `${progress * 100}%` }} /></div>
        <div class="run-sub">{next ? <>Next: <strong>{next.title}</strong> · {formatClock(next.duration)}</> : 'Last one!'}</div>
      </div>

      <div class="run-bottom">
        <div class="run-total">
          <span>{formatClock(Math.floor(Math.min(pos, tl.total)))} elapsed</span>
          <span>{formatClock(totalLeft)} left</span>
        </div>
        {phase === 'preparing' && <button class="btn wide light" disabled>Preparing audio…</button>}
        {phase === 'error' && <div class="error">Couldn't play: {error}</div>}
        {phase === 'ready' && <button class="btn wide light big" onClick={start}>{resumeAt ? 'Resume' : 'Start'}</button>}
        {phase === 'running' && (
          <div class="controls">
            <button class="ctrl" aria-label="Previous step" onClick={() => playerRef.current?.previous()}><Icon d={PREV} /></button>
            <button class="ctrl main" aria-label={playing ? 'Pause' : 'Play'} onClick={toggle}><Icon d={playing ? PAUSE : PLAY} /></button>
            <button class="ctrl" aria-label="Next step" onClick={() => playerRef.current?.next()}><Icon d={NEXT} /></button>
          </div>
        )}
      </div>
    </div>
  );
}

const PLAY = 'M8 5v14l11-7z';
const PAUSE = 'M6 5h4v14H6zM14 5h4v14h-4z';
const PREV = 'M6 6h2v12H6zM9.5 12L18 18V6z';
const NEXT = 'M16 6h2v12h-2zM6 18l8.5-6L6 6z';

function Icon({ d }: { d: string }) {
  return <svg viewBox="0 0 24 24" width="1em" height="1em" fill="currentColor" aria-hidden="true"><path d={d} /></svg>;
}
