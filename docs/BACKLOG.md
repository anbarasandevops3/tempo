# Tempo backlog

> Source of truth for stories. Owned by product-manager. Story format: see `.claude/agents/product-manager.md`.

## 0.1.0: first version (iPhone web app)

### TMP-1: Timer keeps running and talking with the screen locked
Priority: P0 · Status: In QA (browser passed; iPhone checks pending) · Owner role: audio-engineer
Story: As the runner, I want cues to keep coming with the phone locked in my pocket, so my workout never stops like in my old app.
Acceptance criteria:
- [x] The workout is rendered into one audio track before Start (ADR-001).
- [ ] iPhone, screen locked for a whole 25-minute workout: every voice cue and beep arrives on time (checklist A1).
- [ ] Switching to another app and back: timer is still in the right place (A2).
- [ ] Ring/silent switch on silent: cues still play (A4).
- [ ] Lock-screen play/pause/next/previous work (A5).

### TMP-2: Build and edit workouts
Priority: P1 · Status: In QA · Owner role: frontend-dev
- [x] Create blank or from template; add steps and repeats (nested); set type, optional name, duration (m:ss, −/+).
- [x] Move up/down, duplicate, remove; "skip last rest on final round".
- [x] Cue settings per workout: get-ready, countdown beeps, round/halfway/ten-second announcements.
- [x] Saved on the phone (localStorage); delete with confirmation.

### TMP-3: Run screen
Priority: P1 · Status: In QA · Owner role: frontend-dev
- [x] Full-screen colour per step type; step name, big countdown, progress bar, next step, round x/y, elapsed/left.
- [x] Pause/play, next, previous (within 3 s of a step's start → previous step); Stop asks to confirm.
- [x] Optional "Screen on" (wake lock).
- [x] Done screen.

### TMP-4: Resume after the app is closed
Priority: P1 · Status: In QA · Owner role: frontend-dev
- [x] Position saved every 2 s and when the app is hidden; Home offers Resume/Discard (for 3 hours).

### TMP-5: Templates
Priority: P1 · Status: Done
- [x] Run/walk intervals, Tabata, Dynamic warm-up, Sprint intervals, HIIT 30/30, HIIT circuit.

### TMP-6: Install on iPhone
Priority: P1 · Status: Ready · Owner role: devops
- [ ] Hosted over https (GitHub Pages, needs owner go-ahead) so it can be added to the home screen and works offline.

### TMP-8: Workout history
Priority: P1 · Status: In QA (browser passed; iPhone checks pending) · Owner role: frontend-dev
Story: As the runner, I want every workout logged automatically so I can see how consistent I am.
Acceptance criteria:
- [x] Start creates an entry (date, workout, planned time); time done is saved every 2 s and when the app is hidden, so a killed app still keeps the log.
- [x] Time done = time actually played; skipping ahead doesn't count.
- [x] Finished → "✓ Done". Stopped → "Stopped x%". Stopped with under 30 s played → not logged (accidental start).
- [x] Resuming continues the same entry; discarding the resume closes it as stopped.
- [x] History screen: workouts and minutes this week (vs last week), day streak, minutes per week (8 weeks), month calendar, list by day; delete an entry with confirmation.
- [x] Home shows "Last done …" per workout.
- [ ] iPhone: a workout finished with the screen locked is logged as Done with the full time (checklist C1).

## Next

### TMP-7: Android app
Priority: P2 · Status: Idea · Planned for the weekend. Native Kotlin with a foreground service, or the same PWA. Add an `android-dev` role.

### TMP-9: Vibration cues
Priority: P3 · Status: Idea · Not possible in iPhone web apps; Android only.

### TMP-10: Export / import workouts
Priority: P3 · Status: Idea · JSON file or link, for backup and moving to Android.
