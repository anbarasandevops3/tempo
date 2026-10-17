# Tempo: interval timer

A personal interval timer for interval running, HIIT and warm-ups. Runs as a web app (PWA) on the
owner's iPhone, added to the home screen from Safari. The owner is the only user and the final
decision-maker. An Android app is planned.

Why it exists: the owner's previous iPhone timer app **stopped while running**. Tempo's first rule
is that the timer never stops, drifts or goes silent, including with the screen locked.

## Stack and layout
- Vite + Preact + TypeScript, plain CSS, Vitest. No backend; data in localStorage.
- `src/model/`: workout types, templates, tree edits, timeline compiler, history stats (pure, tested)
- `src/audio/`: cue builder, WAV renderer, voice pack loader (pure, tested)
- `src/player/`: playback, Media Session (lock-screen controls)
- `src/ui/`: Home, Editor, Run, History screens. `src/store/`: localStorage.
- `public/voice/`: generated voice pack (committed). `public/sw.js`: offline cache. `public/manifest.webmanifest`, icons.
- `tools/make_voice_pack.py` (Piper TTS) and `tools/make_icons.py` (Pillow). Python venv in `tools/.venv`, voice model in `tools/voices/` (both git-ignored).
- Design: [docs/adr/001-rendered-audio-track.md](docs/adr/001-rendered-audio-track.md).

## How to run / test / build
```bash
npm run dev        # http://localhost:5173, and http://<laptop-ip>:5173 for the iPhone on the same Wi-Fi
npm test           # Vitest
npm run build      # type-check + production build in dist/
npm run voices     # regenerate public/voice/ after changing PHRASES
npm run deploy     # test, build, publish to GitHub Pages (https://anbarasandevops3.github.io/tempo/)
```
Recreate the Python tools if `tools/.venv` is missing:
```bash
python3 -m venv tools/.venv && tools/.venv/bin/pip install piper-tts pillow
tools/.venv/bin/python -m piper.download_voices --download-dir tools/voices en_US-lessac-medium
```

## The team

Each role is a subagent in `.claude/agents/`.

| Role | Agent | Owns | Never does |
|---|---|---|---|
| Manager | `product-manager` | `docs/BACKLOG.md`, stories, priorities, release scope | Write code |
| UX designer | `ux-designer` | Interaction specs, cue wording, run-screen readability, design reviews | Write production code |
| Tech lead | `architect` | ADRs, module boundaries, protecting the rendered-track design, code review | Implement features |
| Audio engineer | `audio-engineer` | `src/audio/`, `src/player/`, voice pack, lock-screen behaviour, timer reliability | Change screens beyond the player API |
| Frontend dev | `frontend-dev` | `src/ui/`, `src/store/`, styles, to the UX spec | Change the audio pipeline |
| QA | `qa-engineer` | Test plans, automated + browser tests, the iPhone checklist, sign-off | Fix product code |
| Android dev | `android-dev` | Everything in `android/`: Kotlin + Compose app, foreground service, APK | Change the web app beyond shared formats |
| DevOps | `devops` | Versions, CHANGELOG, git, hosting, getting the app onto the phone | Push or publish without the owner's go-ahead |


## SDLC

```
Request (owner) ─▶ product-manager ─▶ architect + ux-designer ─▶ audio-engineer ┐
                     story + criteria    design + interaction spec   frontend-dev   ┘─▶ qa-engineer ─▶ devops ─▶ owner tests on iPhone
                                                                          ▲                 │
                                                                          └──── bugs ───────┘
```
1. **Plan.** product-manager writes `TMP-<n>` in `docs/BACKLOG.md` with acceptance criteria.
2. **Design.** architect reviews anything touching timing/audio (ADR if it changes the core design);
   ux-designer specs anything the runner sees or hears.
3. **Build.** audio-engineer and frontend-dev implement with tests and hand off with test steps.
4. **Test.** qa-engineer runs automated and browser tests and updates the iPhone checklist. The owner runs the iPhone checks.
5. **Release.** devops versions, updates the changelog and deploys (with go-ahead).

Small fixes can skip design. Nothing skips QA. Anything that could stop the timer is P0.

Ask for a role by name, e.g. "product-manager: plan workout history" or "audio-engineer: add a
'Round 3' cue for nested repeats, then qa-engineer test it".

## Conventions
- `docs/BACKLOG.md` is the source of truth for stories.
- Every cue is baked into the rendered track; `audio.currentTime` is the only clock (ADR-001).
- localStorage access is always wrapped in try/catch.
- No sudo, no pushes, no publishing without the owner's go-ahead.

## Project rules
<Rules that apply only to this project. Added after I approve them at wrap-up.>
-

## Memory
At session start, also read [memory.md](memory.md). At wrap-up, follow the workspace rules in `../CLAUDE.md`:
update `memory.md`, add an entry to [log.md](log.md), and propose any new rules for approval.
