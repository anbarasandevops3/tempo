// Plays a rendered workout track. The audio element is the clock: position = audio.currentTime,
// so the display can never drift from the cues, and the timer keeps going while the page is frozen.

import { segmentAt, type Timeline } from '../model/timeline';

export interface PlayerHandlers {
  onEnded(): void;
  onPlayState(playing: boolean): void;
}

export class Player {
  readonly audio: HTMLAudioElement;
  private url: string;
  // Played time = sum of the stretches between seeks, so skipping ahead doesn't count as training.
  private playedBefore = 0;
  private stretchStart = 0;

  constructor(
    wav: Uint8Array,
    readonly timeline: Timeline,
    private title: string,
    handlers: PlayerHandlers,
  ) {
    this.url = URL.createObjectURL(new Blob([wav as BlobPart], { type: 'audio/wav' }));
    this.audio = new Audio();
    this.audio.preload = 'auto';
    this.audio.src = this.url;
    this.audio.addEventListener('ended', () => handlers.onEnded());
    this.audio.addEventListener('play', () => handlers.onPlayState(true));
    this.audio.addEventListener('pause', () => handlers.onPlayState(false));
    // Safari 17+: play as media even with the ring/silent switch on.
    const session = (navigator as Navigator & { audioSession?: { type: string } }).audioSession;
    if (session) session.type = 'playback';
    this.setupMediaSession();
  }

  get position(): number {
    return this.audio.currentTime;
  }

  /** Seconds of the track actually played (excluding skipped parts), capped at the workout length. */
  get played(): number {
    return Math.min(this.timeline.total, this.playedBefore + Math.max(0, this.stretchEnd() - this.stretchStart));
  }

  get playing(): boolean {
    return !this.audio.paused && !this.audio.ended;
  }

  /** Must be called from a tap handler the first time (iOS autoplay rule). */
  play(): Promise<void> {
    return this.audio.play();
  }

  pause(): void {
    this.audio.pause();
  }

  seek(t: number): void {
    this.playedBefore += Math.max(0, this.stretchEnd() - this.stretchStart);
    this.audio.currentTime = Math.max(0, Math.min(t, this.timeline.total));
    this.stretchStart = this.audio.currentTime;
    this.updateMediaSession();
  }

  next(): void {
    const seg = segmentAt(this.timeline, this.position);
    if (!seg) return;
    this.seek(seg.start + seg.duration);
  }

  /** Back to the start of this step, or the previous step if we're within 3 s of its start. */
  previous(): void {
    const seg = segmentAt(this.timeline, this.position);
    if (!seg) return;
    const prev = this.timeline.segments[seg.index - 1];
    this.seek(this.position - seg.start < 3 && prev ? prev.start : seg.start);
  }

  /** Call when the visible step changes, so the lock screen shows it. */
  updateMediaSession(): void {
    if (!('mediaSession' in navigator)) return;
    const seg = segmentAt(this.timeline, this.position);
    navigator.mediaSession.metadata = new MediaMetadata({
      title: seg ? seg.title : this.title,
      artist: this.title,
      album: 'Tempo',
    });
    try {
      navigator.mediaSession.setPositionState({
        duration: this.audio.duration || this.timeline.total,
        position: Math.min(this.position, this.audio.duration || this.timeline.total),
        playbackRate: 1,
      });
    } catch { /* not supported or duration unknown yet */ }
  }

  destroy(): void {
    this.audio.pause();
    this.audio.removeAttribute('src');
    this.audio.load();
    URL.revokeObjectURL(this.url);
    if ('mediaSession' in navigator) {
      for (const a of ['play', 'pause', 'nexttrack', 'previoustrack', 'seekto'] as MediaSessionAction[]) {
        try { navigator.mediaSession.setActionHandler(a, null); } catch { /* ignore */ }
      }
      navigator.mediaSession.metadata = null;
    }
  }

  private stretchEnd(): number {
    return Math.min(this.audio.currentTime, this.timeline.total);
  }

  private setupMediaSession() {
    if (!('mediaSession' in navigator)) return;
    const ms = navigator.mediaSession;
    const set = (a: MediaSessionAction, h: MediaSessionActionHandler) => {
      try { ms.setActionHandler(a, h); } catch { /* unsupported action */ }
    };
    set('play', () => { void this.play(); });
    set('pause', () => this.pause());
    set('nexttrack', () => this.next());
    set('previoustrack', () => this.previous());
    set('seekto', (d) => { if (d.seekTime != null) this.seek(d.seekTime); });
  }
}
