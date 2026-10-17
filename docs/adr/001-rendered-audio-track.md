# ADR-001: Render the whole workout into one audio track

Date: 2026-10-14 · Status: Accepted

## Context
The owner's previous iPhone timer stopped mid-run. On iOS a web page's JavaScript is suspended when
the screen locks or Safari goes to the background, so `setTimeout` beeps, `speechSynthesis` and
JS-driven countdowns all stop. A playing `<audio>` element keeps playing.

## Decision
When a workout is opened, compile it to a timeline, build every cue (voice + beeps), and render it
into **one WAV** (mono, 16-bit PCM; 16000 Hz on the web, 22050 Hz on Android). Start plays it. `audio.currentTime` is the
only clock: the screen, skip/previous and resume all derive from it.

Voice comes from a pre-recorded pack (Piper TTS, generated at build time), so it can be mixed into
the track. The voice says the step *type* (Run, Walk, Work…); custom names are shown on screen only.

## Consequences
- Cues survive screen lock, app switching and JS throttling; the display can't drift from the audio.
- Track size on the web is ~1.9 MB per minute (~48 MB for 25 minutes), kept in memory as a Blob.
- 2026-10-17: moved from 8-bit 11 kHz to 16-bit after the owner found the voice rough and quiet;
  the voice pack is now Piper `en_US-lessac-high`, 16-bit 22 kHz, normalised to -0.5 dBFS.
- Lock-screen controls come from the Media Session API; the step name updates on the lock screen
  only when JS runs (the audio itself is unaffected).
- Phone calls or Siri pause the audio; the runner resumes with play (lock screen or app).
- Speaking arbitrary custom names isn't possible without a voice clip for them.

## Rejected
- Web Audio scheduling: the AudioContext can be suspended in the background and respects the silent switch.
- `speechSynthesis` during the run: needs JS at the moment of the cue.
- Native iOS: needs a Mac and an Apple developer account.
