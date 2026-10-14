// Voice pack: pre-recorded clips (tools/make_voice_pack.py) as raw 8-bit PCM in one file.

export interface VoicePack {
  clip(key: string): Uint8Array | undefined;
}

interface PackIndex {
  sampleRate: number;
  clips: Record<string, [number, number]>;
}

export function makePack(index: PackIndex, data: Uint8Array): VoicePack {
  return {
    clip(key) {
      const e = index.clips[key];
      return e ? data.subarray(e[0], e[0] + e[1]) : undefined;
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
    return makePack(index, new Uint8Array(bin));
  })();
  loading.catch(() => { loading = undefined; });
  return loading;
}
