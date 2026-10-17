"""Build the voice pack: every spoken word/phrase the app can say, as raw 16-bit PCM.

Run with `npm run voices`. Writes public/voice/pack.bin and public/voice/pack.json.
Needs the Piper venv in tools/.venv and the voice model in tools/voices/ (see CLAUDE.md).
"""
import json
import subprocess
import wave
from pathlib import Path

import numpy as np
from piper import PiperVoice

ROOT = Path(__file__).resolve().parent.parent
MODEL = ROOT / "tools/voices/en_US-lessac-high.onnx"
OUT = ROOT / "public/voice"
RATE = 22050  # Piper's native rate; the apps resample to their track rate (PACK_RATE in render.ts / Render.kt)
PEAK = 0.95  # every clip is normalised to this peak (about -0.5 dBFS) so the voice is loud and even

NUMBER_WORDS = [
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
    "nineteen",
]
TENS = {20: "twenty", 30: "thirty", 40: "forty", 50: "fifty"}


def number_text(n: int) -> str:
    if n < 20:
        return NUMBER_WORDS[n]
    tens, ones = divmod(n, 10)
    return TENS[tens * 10] + ("" if ones == 0 else " " + NUMBER_WORDS[ones])


# key -> text. Keys are what src/audio/voice.ts asks for.
PHRASES = {
    # step kinds (src/model/types.ts StepKind)
    "warmup": "Warm up",
    "run": "Run",
    "jog": "Jog",
    "walk": "Walk",
    "sprint": "Sprint",
    "work": "Work",
    "rest": "Rest",
    "recover": "Recover",
    "cooldown": "Cool down",
    "stretch": "Stretch",
    # building blocks for durations and rounds
    "second": "second",
    "seconds": "seconds",
    "minute": "minute",
    "minutes": "minutes",
    "round": "Round",
    "of": "of",
    # cues
    "get_ready": "Get ready",
    "halfway": "Halfway",
    "last_round": "Last round",
    "ten_seconds": "Ten seconds",
    "complete": "Workout complete. Great job!",
    "paused": "Paused",
}
for i in range(1, 60):
    PHRASES[f"n{i}"] = number_text(i)


def synth(voice: PiperVoice, text: str, tmp: Path) -> bytes:
    wav_path = tmp / "clip.wav"
    with wave.open(str(wav_path), "wb") as wf:
        voice.synthesize_wav(text, wf)
    # trim leading/trailing silence, cut rumble, even out the level, then peak-normalise
    raw = subprocess.run(
        [
            "ffmpeg", "-loglevel", "error", "-i", str(wav_path),
            "-af", "silenceremove=start_periods=1:start_threshold=-45dB,"
                   "areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse,"
                   "highpass=f=80,acompressor=threshold=-24dB:ratio=3:attack=5:release=80:makeup=2",
            "-ar", str(RATE), "-ac", "1", "-f", "s16le", "-",
        ],
        check=True, capture_output=True,
    ).stdout
    pcm = np.frombuffer(raw, dtype="<i2").astype(np.float32)
    peak = float(np.abs(pcm).max()) or 1.0
    return np.clip(pcm * (PEAK * 32767 / peak), -32768, 32767).astype("<i2").tobytes()


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    tmp = ROOT / "tools/.tmp"
    tmp.mkdir(exist_ok=True)
    voice = PiperVoice.load(str(MODEL))
    index, blob = {}, bytearray()
    for key, text in PHRASES.items():
        pcm = synth(voice, text, tmp)
        index[key] = [len(blob), len(pcm)]
        blob += pcm
    (OUT / "pack.bin").write_bytes(blob)
    # offsets/lengths are in bytes; 2 bytes per sample
    (OUT / "pack.json").write_text(json.dumps({"sampleRate": RATE, "format": "s16le", "clips": index}))
    print(f"{len(index)} clips, {len(blob) / 1024:.0f} KiB")


if __name__ == "__main__":
    main()
