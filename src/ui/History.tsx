import { useState } from 'preact/hooks';
import {
  addDays, dayKey, effectiveStatus, groupByDay, streakDays, totals, weekStart, weeklyMinutes,
  type HistoryEntry,
} from '../model/history';
import { formatClock } from '../model/timeline';
import { deleteHistory, exportBackup, importBackup, loadHistory } from '../store/store';
import { BackupError } from '../model/backup';

interface Props {
  liveEntryId?: string;
  onBack(): void;
  /** Called after an import so the app reloads its workouts. */
  onImported(): void;
}

export function History({ liveEntryId, onBack, onImported }: Props) {
  const [entries, setEntries] = useState<HistoryEntry[]>(loadHistory);
  const [month, setMonth] = useState(() => startOfMonth(Date.now()));
  const now = Date.now();
  const thisWeek = totals(entries, weekStart(now), addDays(weekStart(now), 7));
  const lastWeek = totals(entries, addDays(weekStart(now), -7), weekStart(now));
  const streak = streakDays(entries, now);
  const weeks = weeklyMinutes(entries, now, 8);
  const maxMin = Math.max(1, ...weeks.map((w) => w.minutes));

  const [note, setNote] = useState('');

  const doExport = async () => {
    const name = `tempo-backup-${new Date().toISOString().slice(0, 10)}.json`;
    const file = new File([exportBackup()], name, { type: 'application/json' });
    // iPhone: the share sheet offers "Save to Files", AirDrop, etc.
    if (navigator.canShare?.({ files: [file] })) {
      try { await navigator.share({ files: [file], title: 'Tempo backup' }); setNote('Backup shared.'); } catch { /* cancelled */ }
      return;
    }
    const url = URL.createObjectURL(file);
    const a = Object.assign(document.createElement('a'), { href: url, download: name });
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setNote('Backup downloaded.');
  };

  const doImport = async (input: HTMLInputElement) => {
    const f = input.files?.[0];
    input.value = '';
    if (!f) return;
    try {
      const r = importBackup(await f.text());
      setEntries(loadHistory());
      onImported();
      setNote(`Imported: ${r.workouts} workouts, ${r.history} history entries.`);
    } catch (e) {
      setNote(e instanceof BackupError ? e.message : "Couldn't read that file.");
    }
  };

  const remove = (e: HistoryEntry) => {
    if (!confirm(`Delete “${e.name}” from ${new Date(e.startedAt).toLocaleDateString()}?`)) return;
    deleteHistory(e.id);
    setEntries(loadHistory());
  };

  return (
    <div class="screen">
      <header class="bar">
        <button class="btn ghost" onClick={onBack}>‹ Back</button>
        <h1>History</h1>
        <span style={{ width: 60 }} />
      </header>

      <div class="stats">
        <Stat value={String(thisWeek.workouts)} label="workouts this week" />
        <Stat value={minutes(thisWeek.seconds)} label={`this week · ${minutes(lastWeek.seconds)} last week`} />
        <Stat value={String(streak)} label={streak === 1 ? 'day streak' : 'days streak'} />
      </div>

      <h3>Minutes per week</h3>
      <div class="card weeks" role="img" aria-label={`Minutes per week, last 8 weeks: ${weeks.map((w) => w.minutes).join(', ')}`}>
        {weeks.map((w, i) => (
          <div class="week" key={w.start}>
            <span class="week-val">{w.minutes > 0 ? w.minutes : ''}</span>
            <div class={`week-bar${i === weeks.length - 1 ? ' current' : ''}`} style={{ height: `${(w.minutes / maxMin) * 100}%` }} />
            <span class="week-label">{new Date(w.start).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}</span>
          </div>
        ))}
      </div>

      <Calendar month={month} entries={entries} now={now} onMonth={setMonth} />

      <h3>Workouts</h3>
      {entries.length === 0 && <p class="muted center">Nothing yet. Finished and stopped workouts show up here.</p>}
      {groupByDay(entries).map((g) => (
        <div key={g.day}>
          <div class="day-head">{dayTitle(g.entries[0].startedAt, now)}</div>
          <ul class="list">
            {g.entries.map((e) => {
              const status = effectiveStatus(e, liveEntryId);
              return (
                <li class="card entry" key={e.id}>
                  <div class="entry-main">
                    <strong>{e.name}</strong>
                    <span class="muted">
                      {new Date(e.startedAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}
                      {' · '}{formatClock(e.doneSec)}
                      {status !== 'completed' && ` of ${formatClock(e.plannedSec)}`}
                    </span>
                  </div>
                  <span class={`badge ${status}`}>
                    {status === 'completed' ? '✓ Done' : status === 'active' ? 'Unfinished' : `Stopped ${Math.round((e.doneSec / Math.max(1, e.plannedSec)) * 100)}%`}
                  </span>
                  <button class="icon" aria-label="Delete entry" onClick={() => remove(e)}>✕</button>
                </li>
              );
            })}
          </ul>
        </div>
      ))}

      <h3>Backup</h3>
      <div class="card backup">
        <p class="muted">Save workouts and history to a file, or load one from Tempo on another phone (iPhone or Android).</p>
        <div class="row">
          <button class="btn" onClick={() => void doExport()}>Export backup</button>
          <label class="btn">
            Import backup
            <input type="file" accept=".json,application/json" hidden onChange={(e) => void doImport(e.currentTarget)} />
          </label>
        </div>
        {note && <p class="muted" role="status">{note}</p>}
      </div>
    </div>
  );
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div class="card stat">
      <span class="stat-value">{value}</span>
      <span class="muted small">{label}</span>
    </div>
  );
}

function Calendar({ month, entries, now, onMonth }: { month: number; entries: HistoryEntry[]; now: number; onMonth(m: number): void }) {
  const first = new Date(month);
  const days = new Set(entries.map((e) => dayKey(e.startedAt)));
  const gridStart = weekStart(month);
  const cells: number[] = [];
  for (let d = gridStart; cells.length < 42; d = addDays(d, 1)) {
    if (cells.length % 7 === 0 && cells.length > 0 && new Date(d).getMonth() !== first.getMonth() && d > month) break;
    cells.push(d);
  }
  const shift = (n: number) => onMonth(new Date(first.getFullYear(), first.getMonth() + n, 1).getTime());
  const today = dayKey(now);
  return (
    <>
      <h3 class="cal-head">
        <button class="icon" aria-label="Previous month" onClick={() => shift(-1)}>‹</button>
        <span>{first.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</span>
        <button class="icon" aria-label="Next month" onClick={() => shift(1)} disabled={month >= startOfMonth(now)}>›</button>
      </h3>
      <div class="card calendar">
        {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => <span class="cal-dow" key={i}>{d}</span>)}
        {cells.map((d) => {
          const k = dayKey(d);
          const inMonth = new Date(d).getMonth() === first.getMonth();
          return (
            <span key={k} class={`cal-day${inMonth ? '' : ' out'}${days.has(k) ? ' done' : ''}${k === today ? ' today' : ''}`}>
              {new Date(d).getDate()}
            </span>
          );
        })}
      </div>
    </>
  );
}

const startOfMonth = (ms: number) => { const d = new Date(ms); return new Date(d.getFullYear(), d.getMonth(), 1).getTime(); };

const minutes = (sec: number) => `${Math.round(sec / 60)} min`;

function dayTitle(ms: number, now: number): string {
  const k = dayKey(ms);
  if (k === dayKey(now)) return 'Today';
  if (k === dayKey(addDays(now, -1))) return 'Yesterday';
  return new Date(ms).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short' });
}
