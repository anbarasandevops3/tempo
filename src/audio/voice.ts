// Voice pack: pre-recorded clips (tools/make_voice_pack.py) as raw 16-bit PCM in one file,
// resampled once to the track rate when loaded.

import { resample, SAMPLE_RATE } from './render';

export interface VoicePack {
  clip(key: string): Int16Array | undefined;
}

export interface PackIndex {
  sampleRate: number;
  /** [byte offset, byte length] */
  clips: Record<string, [number, number]>;
}

export function makePack(index: PackIndex, data: ArrayBuffer, rate = SAMPLE_RATE): VoicePack {
  const cache = new Map<string, Int16Array>();
  return {
    clip(key) {
      const hit = cache.get(key);
      if (hit) return hit;
      const e = index.clips[key];
      if (!e) return undefined;
      const raw = new Int16Array(data.slice(e[0], e[0] + e[1]));
      const clip = resample(raw, index.sampleRate, rate);
      cache.set(key, clip);
      return clip;
    },
  };
}

let loading: Promise<VoicePack> | undefined;

export function loadVoicePack(): Promise<VoicePack> {
  loading ??= (async () => {
    const base = import.meta.env.BASE_URL;
    const [index, bin] = await Promise.all([
      fetch(`${base}voice/pack.json`).then((r) => r.json() as Promise<PackIndex>),
      fetch(`${base}voice/pack.bin`).then((r) => r.arrayBuffer()),
    ]);
    return makePack(index, bin);
  })();
  loading.catch(() => { loading = undefined; });
  return loading;
}
