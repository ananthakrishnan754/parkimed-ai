"""Build data/features/features.csv from organized raw files.

Expected raw layout (one folder per patient, one session per subfolder):

    data/raw/
    └── <patient_id>/
        └── <session>/
            ├── metadata.json     {"label": "ON"|"OFF", "severity": 2.5, "age": 62}
            ├── voice.wav          (30s vowel)
            ├── accel.csv          (t,x,y,z)  OR raw accel z-axis only
            ├── gyro.csv           (t,x,y,z)
            ├── tap_events.csv     (interval_ms,duration_ms) per tap
            └── spiral.csv         (x,y) polyline points

mPower notes: its tables map onto this layout — tapping/raw_accel.csv + gyro
→ accel/gyro, tapping/taps.csv → tap_events, phonation/audio → voice.wav,
gait/raw_accel.csv → accel. Copy or symlink mPower exports into data/raw/
and this script detects columns generically.

Output columns per row:
    subject_id, label, severity,
    voice_0..voice_38, motion_0..motion_35,
    tap_0..tap_19, spiral_0..spiral_14
"""
from __future__ import annotations

import json
import sys
import wave
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.append(str(Path(__file__).resolve().parents[2]))
from config import FEATURES_DIR, RAW_DIR
from src.data.preprocessing import process_voice_arr, process_motion_csv, SAMPLE_RATE
from src.features.feature_extraction import (
    voice_features, voice_pitch_jitter_shimmer,
    motion_features, tap_features, spiral_features,
)

FEATURE_PREFIXES = ["voice_", "motion_", "tap_", "spiral_"]


def load_wav(path: Path) -> tuple[np.ndarray, int]:
    with wave.open(str(path), "rb") as w:
        sr = w.getframerate()
        n = w.getnframes()
        audio = np.frombuffer(w.readframes(n), dtype=np.int16).astype(np.float32) / 32768.0
        if w.getnchannels() == 2:
            audio = audio.reshape(-1, 2).mean(axis=1)
    return audio, sr


def load_tap_events(path: Path) -> tuple[np.ndarray, np.ndarray]:
    df = pd.read_csv(path)
    if {"interval_ms", "duration_ms"}.issubset(df.columns):
        return df["interval_ms"].to_numpy(), df["duration_ms"].to_numpy()
    # fallback: raw touch log with time_ms columns
    if len(df.columns) == 2:
        return df.iloc[:, 0].to_numpy(), df.iloc[:, 1].to_numpy()
    return np.array([], dtype=float), np.array([], dtype=float)


def load_spiral(path: Path) -> np.ndarray:
    df = pd.read_csv(path)
    if {"x", "y"}.issubset(df.columns):
        return df[["x", "y"]].to_numpy(np.float32)
    if len(df.columns) >= 2:
        return df.iloc[:, :2].to_numpy(np.float32)
    return np.zeros((0, 2))


def one_sample(session_dir: Path) -> dict | None:
    meta_path = session_dir / "metadata.json"
    if not meta_path.exists():
        return None
    meta = json.loads(meta_path.read_text())
    label = str(meta.get("label", "")).upper()
    if label not in ("ON", "OFF"):
        return None

    sample = {"subject_id": session_dir.parent.name, "label": label,
              "severity": float(meta.get("severity", 2.5))}

    # Voice -------------------------------------------------------------
    wav = session_dir / "voice.wav"
    if wav.exists():
        audio, sr = load_wav(wav)
        audio_s, _ = process_voice_arr(audio, sr)
        sample["voice"] = voice_features(audio_s, sr)
        pitch, jitter, shimmer = voice_pitch_jitter_shimmer(audio_s, sr)
        sample["voice_extra"] = np.array([pitch, jitter, shimmer], np.float32)
    else:
        sample["voice"] = None

    # Motion ------------------------------------------------------------
    accel = session_dir / "accel.csv"
    gyro = session_dir / "gyro.csv"
    windows = None
    if accel.exists():
        windows = process_motion_csv(accel)
        if gyro.exists():
            g = process_motion_csv(gyro)
            if g is not None and g.size:
                windows = np.concatenate([windows, g], axis=-1) if windows is not None else g
    sample["motion"] = motion_features(windows) if windows is not None and windows.size else None

    # Tap / spiral ------------------------------------------------------
    tap = session_dir / "tap_events.csv"
    if tap.exists():
        iv, du = load_tap_events(tap)
        sample["tap"] = tap_features(iv.tolist(), du.tolist())
    else:
        sample["tap"] = None

    spiral = session_dir / "spiral.csv"
    if spiral.exists():
        sample["spiral"] = spiral_features(load_spiral(spiral))
    else:
        sample["spiral"] = None

    if not any(sample.get(k) is not None for k in ("voice", "motion", "tap", "spiral")):
        return None
    return sample


def build_csv(out: Path = FEATURES_DIR / "features.csv") -> Path:
    rows = []
    patient_dirs = [p for p in RAW_DIR.iterdir() if p.is_dir()]
    for patient in sorted(patient_dirs):
        for session in sorted(patient.iterdir()):
            if not session.is_dir():
                continue
            s = one_sample(session)
            if s is None:
                continue
            row = {"subject_id": s["subject_id"], "label": s["label"],
                   "severity": s["severity"]}
            for name, dim in (("voice", 39), ("motion", 36), ("tap", 20), ("spiral", 15)):
                vec = s[name]
                for i in range(dim):
                    row[f"{name}_{i}"] = float(vec[i]) if vec is not None else 0.0
            rows.append(row)

    if not rows:
        raise SystemExit(
            "No valid samples found under data/raw/. Run download_uci.py, use "
            "scripts/generate_mock_data.py, or add mPower exports first."
        )
    df = pd.DataFrame(rows)
    out.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(out, index=False)
    print(f"[ok] built {len(df)} samples across {df.subject_id.nunique()} patients -> {out}")
    print(df.label.value_counts().to_string())
    return out


if __name__ == "__main__":
    build_csv()