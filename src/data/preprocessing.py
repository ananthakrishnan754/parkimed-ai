"""Preprocessing: clean / normalize / window raw sensor data.

Expects the unified raw layout documented in Readme, but degrades gracefully
with whatever is present under data/raw/. The mPower tables are documented
here (mpower/taps, mpower/voice, mpower/gait) and the same normalization logic
is mirrored in the Android app via exported preprocessing_config.json.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pandas as pd
from scipy import signal as scipy_signal

import sys
sys.path.append(str(Path(__file__).resolve().parents[2]))
from config import (
    RAW_DIR, PROCESSED_DIR, SAMPLE_RATE, AUDIO_RATE, WINDOW_SIZE,
    WINDOW_OVERLAP, VOICE_TARGET_SECS, export_preprocessing_config,
)


# --------------------------------------------------------------------------
# Motion
# --------------------------------------------------------------------------
def remove_gravity(x, y, z, fs=SAMPLE_RATE, cutoff=0.25):
    """High-pass filter accel readings to strip gravity (original mPower does
    this to isolate movement). Also normalizes orientation by magnitude."""
    b, a = scipy_signal.butter(4, cutoff / (0.5 * fs), btype="high")
    gx = scipy_signal.filtfilt(b, a, np.asarray(x, float))
    gy = scipy_signal.filtfilt(b, a, np.asarray(y, float))
    gz = scipy_signal.filtfilt(b, a, np.asarray(z, float))
    return gx, gy, gz


def resample_timeseries(t, values, fs_target=SAMPLE_RATE):
    """Resample irregular sensor timestamps to a uniform target rate."""
    t = np.asarray(t, float)
    v = np.asarray(values, float)
    t = t - t[0]
    n = int((t[-1] - t[0]) * fs_target)
    if n <= 1:
        return np.zeros(0), np.zeros(0)
    t_uniform = np.linspace(t[0], t[-1], n)
    v_uniform = np.interp(t_uniform, t, v)
    return t_uniform, v_uniform


def windowize(data, win=WINDOW_SIZE, overlap=WINDOW_OVERLAP):
    """Split a numeric 1D array into overlapping fixed windows."""
    step = win - overlap
    if len(data) < win:
        return np.zeros((0, win))
    n_windows = 1 + (len(data) - win) // step
    return np.stack([data[i * step: i * step + win] for i in range(n_windows)])


def process_motion_csv(csv_path: Path) -> np.ndarray | None:
    """accel/gyro CSV (t, x, y, z) -> windows of de-gravity normalized values."""
    df = pd.read_csv(csv_path)
    if {"t", "x", "y", "z"}.issubset(df.columns):
        t_uniform, x = resample_timeseries(df.t, df.x)
        _, y = resample_timeseries(df.t, df.y)
        _, z = resample_timeseries(df.t, df.z)
    elif len(df.columns) == 4:
        t_uniform, x = resample_timeseries(df.iloc[:, 0], df.iloc[:, 1])
        _, y = resample_timeseries(df.iloc[:, 0], df.iloc[:, 2])
        _, z = resample_timeseries(df.iloc[:, 0], df.iloc[:, 3])
    else:
        return None

    gx, gy, gz = remove_gravity(x, y, z)
    stack = np.stack([windowize(gx), windowize(gy), windowize(gz)], axis=-1)
    return stack  # shape (N, window, 3)


# --------------------------------------------------------------------------
# Voice
# --------------------------------------------------------------------------
def process_voice_arr(audio: np.ndarray, sr: int) -> tuple[np.ndarray, float]:
    """Trim to VOICE_TARGET_SECS, normalize amplitude, return (signal, snr)."""
    target_n = int(AUDIO_RATE * VOICE_TARGET_SECS)
    if sr != AUDIO_RATE:
        n = int(len(audio) * AUDIO_RATE / sr)
        idx = np.linspace(0, len(audio) - 1, n).astype(int)
        audio = audio[idx]
    audio = audio[:target_n]
    if audio.size == 0:
        return np.zeros(0), 0.0
    peak = np.max(np.abs(audio)) + 1e-9
    audio = audio / peak
    silence = np.where(np.abs(audio) < 0.05)[0]
    snr = 10 * np.log10(peak / 0.05 + 1e-9) if silence.size > 0.05 * audio.size else 30.0
    return audio, snr


# --------------------------------------------------------------------------
# Build one unified processed feature source
# --------------------------------------------------------------------------
def process_all() -> None:
    """Walk data/raw/* , apply the right preprocessor, dump .npz features."""
    processed = {}
    for task_dir in RAW_DIR.glob("*"):
        if not task_dir.is_dir():
            continue
        for sample in sorted(task_dir.glob("*.csv")):
            key = f"{task_dir.name}/{sample.stem}"
            if task_dir.name in ("accel", "gyro", "motion", "gait"):
                processed[key] = process_motion_csv(sample)
    # Persist each processed sample as npz for the feature stage.
    for key, arr in processed.items():
        if arr is None or arr.size == 0:
            continue
        safe = key.replace("/", "__")
        np.savez_compressed(PROCESSED_DIR / f"{safe}.npz", data=arr)
    print(f"[ok] processed {len(processed)} motion samples")

    # Freeze config for the app side.
    cfg = export_preprocessing_config()
    print(f"[ok] exported preprocessing_config.json -> {cfg}")


if __name__ == "__main__":
    process_all()