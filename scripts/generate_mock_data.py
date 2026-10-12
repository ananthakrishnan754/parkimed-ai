"""Generate synthetic Parkinson's ON/OFF data so the full pipeline runs TODAY
without waiting for mPower approval.

Physics behind the mock (keeps it scientifically plausible):
  OFF state  -> higher tremor amplitude (4-6 Hz oscillation in accel/gyro),
                slower / more variable tapping, rougher spiral, higher voice jitter
  ON state   -> steadier motion, faster uniform tapping, smooth spiral, stable voice

The synthetic data is ONLY for pipeline validation and demo. Real reports
must be generated from mPower + real patient data.
"""
from __future__ import annotations

import json
import sys
import wave
from pathlib import Path

import numpy as np

sys.path.append(str(Path(__file__).resolve().parents[1]))
from config import RAW_DIR, SAMPLE_RATE, AUDIO_RATE

rng = np.random.default_rng(7)

PATIENTS = 12
SESSIONS_PER_PATIENT = 10  # mix of ON / OFF sessions
TREMOR_FREQ = 5.0          # Hz, classic PD resting tremor band


def tremor_signal(n: int, amplitude: float, fs: int = SAMPLE_RATE) -> np.ndarray:
    t = np.arange(n) / fs
    wobble = 0.4 + 0.3 * np.sin(2 * np.pi * 0.7 * t)
    return amplitude * np.sin(2 * np.pi * (TREMOR_FREQ + wobble) * t)


def make_voice(seconds: float, off: bool) -> tuple[np.ndarray, int]:
    sr = AUDIO_RATE
    n = int(seconds * sr)
    t = np.arange(n) / sr
    f0 = 150 + 8 * np.sin(2 * np.pi * 3 * t)  # pitch modulation
    f0 = f0 * (1 + (0.03 if off else 0.012))   # higher jitter when OFF
    phase = 2 * np.pi * np.cumsum(f0) / sr
    voiced = (0.5 + 0.5 * np.sin(2 * np.pi * 0.4 * t))   # energy envelope
    noise = rng.normal(0, (0.06 if off else 0.02), n)    # breathiness when OFF
    audio = (0.6 * voiced * np.sin(phase) + noise).astype(np.float32)
    return audio, sr


def write_wav(path: Path, audio: np.ndarray, sr: int) -> None:
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes((audio * 32767).astype(np.int16).tobytes())


def make_tap_events(off: bool) -> tuple[np.ndarray, np.ndarray]:
    n = rng.integers(28, 40)
    base = 180.0 if off else 120.0            # OFF = slower taps
    vari = 45.0 if off else 8.0               # OFF = erratic rhythm
    intervals = np.maximum(rng.normal(base, vari, n), 60.0)
    durations = rng.normal(90.0 if off else 70.0, 18.0, n)
    return intervals, durations


def make_spiral(off: bool) -> np.ndarray:
    n = 800
    theta = np.linspace(0, 6 * np.pi, n)
    r = 0.5 + 0.03 * theta
    x = r * np.cos(theta)
    y = r * np.sin(theta)
    trem = 0.05 if off else 0.008              # OFF = wigglier trace
    x += rng.normal(0, trem, n)
    y += rng.normal(0, trem, n)
    return np.stack([x, y], axis=1)


def make_motion(seconds: float, off: bool) -> tuple[np.ndarray, np.ndarray]:
    n = int(seconds * SAMPLE_RATE)
    tcol = np.arange(n) / SAMPLE_RATE
    ax = tremor_signal(n, 1.2 if off else 0.25)
    ay = tremor_signal(n, 1.0 if off else 0.2)
    az = 9.8 + tremor_signal(n, 0.4 if off else 0.1)
    gx = tremor_signal(n, 0.9 if off else 0.15)
    gy = tremor_signal(n, 0.8 if off else 0.12)
    gz = tremor_signal(n, 0.6 if off else 0.1)
    accel = np.stack([ax, ay, az], axis=-1)
    gyro = np.stack([gx, gy, gz], axis=-1)
    return np.stack([tcol, ax, ay, az], axis=-1), np.stack([tcol, gx, gy, gz], axis=-1)


def main() -> None:
    for p in range(PATIENTS):
        pid = f"mock_p{p:02d}"
        for s in range(SESSIONS_PER_PATIENT):
            off = (hash(f"{pid}-{s}") % 5) < 2     # ~40% OFF sessions
            session = RAW_DIR / pid / f"session_{s:02d}"
            session.mkdir(parents=True, exist_ok=True)

            (session / "metadata.json").write_text(json.dumps({
                "label": "OFF" if off else "ON",
                "severity": float(round(4.1 if off else 1.2, 2)),
                "age": int(rng.integers(55, 78)),
            }))

            audio, sr = make_voice(3.0, off)         # 3s for speed (real: 30s)
            write_wav(session / "voice.wav", audio, sr)

            iv, du = make_tap_events(off)
            np.savetxt(session / "tap_events.csv",
                       np.stack([iv, du], axis=-1),
                       header="interval_ms,duration_ms", delimiter=",", comments="")

            np.savetxt(session / "spiral.csv", make_spiral(off),
                       header="x,y", delimiter=",", comments="")

            accel_csv, gyro_csv = make_motion(3.0, off)
            np.savetxt(session / "accel.csv", accel_csv,
                       header="t,x,y,z", delimiter=",", comments="")
            np.savetxt(session / "gyro.csv", gyro_csv,
                       header="t,x,y,z", delimiter=",", comments="")
    print(f"[ok] generated {PATIENTS} mock patients x {SESSIONS_PER_PATIENT} sessions in {RAW_DIR}")
    print("Next: python src/data/build_training_data.py")


if __name__ == "__main__":
    main()