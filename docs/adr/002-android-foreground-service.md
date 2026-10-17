# ADR-002: Android plays the rendered track from a foreground service

Date: 2026-10-17 · Status: Accepted

## Context
The owner wants Tempo on Android too. Android allows apps to keep running in the background with a
foreground service and a visible notification, which iOS web apps can't do.

## Decision
Native Kotlin + Compose. Keep ADR-001 (one rendered WAV, the player position is the clock) so cues
are identical on both platforms, and play it from a `mediaPlayback` foreground service. Use what
Android adds on top:
- notification with a live countdown (system chronometer) and Back / Pause / Next buttons;
- vibration on step start, 3-2-1 and finish (from the service's 200 ms ticker);
- custom step names spoken by the phone's TextToSpeech, rendered to clips before Start and mixed in;
- audio focus: pause for calls, resume after.

## Consequences
- Same voice pack (`public/voice/`) is packaged as assets; one source of truth for cues.
- Custom-name voices come from the phone's TTS, so they sound different from the pack voice.
- The ticker needs the service alive, which the foreground service guarantees; if the OS kills it
  anyway (aggressive OEM battery savers), resume works from the saved session.

## Rejected
- Wrapping the web app (Capacitor/TWA): background playback would depend on the WebView.
- Scheduling beeps with timers instead of a rendered track: drift and Doze risk, and two cue engines to maintain.
