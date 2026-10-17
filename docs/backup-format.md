# Backup format (both apps)

One JSON file, `tempo-backup-YYYY-MM-DD.json`, written by **Export** and read by **Import** in the
web app and the Android app.

```json
{
  "app": "tempo",
  "format": 1,
  "exportedAt": 1792000000000,
  "workouts": [ Workout, ... ],
  "history": [ HistoryEntry, ... ]
}
```

`Workout` and `HistoryEntry` are exactly the shapes in `src/model/types.ts` and `src/model/history.ts`:

```json
{
  "id": "k3j9x0aa", "name": "Tabata", "updatedAt": 1792000000000,
  "cues": { "getReady": 10, "countdownBeeps": 3, "halfway": true, "tenSeconds": true,
            "announceRounds": true, "vibrate": true, "speakNames": true },
  "nodes": [
    { "type": "repeat", "id": "r1", "times": 8, "skipLastRest": true, "children": [
      { "type": "step", "id": "s1", "kind": "work", "label": "Burpees", "seconds": 20 },
      { "type": "step", "id": "s2", "kind": "rest", "seconds": 10 }
    ] }
  ]
}
```
```json
{ "id": "h1", "workoutId": "k3j9x0aa", "name": "Tabata", "startedAt": 1792000000000,
  "plannedSec": 240, "doneSec": 240, "status": "completed" }
```

- `kind` is one of: warmup, run, jog, walk, sprint, work, rest, recover, cooldown, stretch.
- `cues.vibrate` and `cues.speakNames` are used by Android only; the web app keeps them untouched.
  Missing values mean `true`.
- Times are epoch milliseconds; durations are seconds.

## Import rules (merge, never wipe)
- Workouts are matched by `id`: the one with the newer `updatedAt` wins; new ones are added.
- History entries are matched by `id`: the one with the larger `doneSec` wins (ties: `completed` beats others); new ones are added.
- Files with `app` ≠ `"tempo"` or a `format` newer than the app understands are rejected with a message.
