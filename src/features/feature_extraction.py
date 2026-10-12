"""Feature extraction for all four modalities.

Each function returns a fixed-size vector so the four experts get
fixed-shape inputs — the same shapes are mirrored in the Android app.
"""
from __future__ import annotations

import numpy as np
import librosa

# --------------------------------------------------------------------------
# Voice
# --------------------------------------------------------------------------
def voice_features(audio: np.ndarray, sr: int = 16000) -> np.ndarray:
    """39-dim vector: 13 MFCC + 13 delta + 13 delta2, means over frames."""
    if audio.size == 0:
        return np.zeros(39)
    mfcc = librosa.feature.mfcc(y=audio.astype(np.float32), sr=sr, n_mfcc=13)
    if mfcc.shape[1] == 0:
        return np.zeros(39)
    delta = librosa.feature.delta(mfcc)
    ddelta = librosa.feature.delta(mfcc, order=2)
    feats = np.concatenate([
        mfcc.mean(axis=1), delta.mean(axis=1), ddelta.mean(axis=1),
    ])
    return feats / (np.max(np.abs(feats)) + 1e-9)


def voice_pitch_jitter_shimmer(audio: np.ndarray, sr: int = 16000) -> tuple[float, float, float]:
    """Return (pitch_hz, jitter, shimmer) diagnostic extras."""
    if audio.size < sr // 10:
        return 0.0, 0.0, 0.0
    f0, _, _ = librosa.pyin(audio.astype(np.float32), fmin=50, fmax=300, sr=sr)
    f0 = f0[~np.isnan(f0)]
    if f0.size < 2:
        return 0.0, 0.0, 0.0
    pitch = float(np.nanmean(f0))
    jitter = float(np.mean(np.abs(np.diff(f0))) / (np.mean(f0) + 1e-9))
    rms = librosa.feature.rms(y=audio.astype(np.float32))[0]
    rms = rms[rms > 0]
    shimmer = float(np.std(rms) / (np.mean(rms) + 1e-9)) if rms.size else 0.0
    return pitch, jitter, shimmer


# --------------------------------------------------------------------------
# Motion
# --------------------------------------------------------------------------
def window_stats(window: np.ndarray, fs: int = 100) -> np.ndarray:
    """Per-channel stats for a (window_size, 3) array -> 18 dims."""
    out = []
    f = np.fft.rfftfreq(window.shape[0], 1 / fs)
    for ch in range(window.shape[1]):
        c = window[:, ch]
        mag = np.sqrt(c @ c) / len(c)
        fft = np.abs(np.fft.rfft(c - c.mean()))
        if fft.size:
            dom_freq = f[np.argmax(fft)]
        else:
            dom_freq = 0.0
        out.extend([
            float(c.mean()), float(c.std()),
            mag, dom_freq, float(c.max() - c.min()),
            float(1.0 if np.abs(c.mean()) < 0.05 else 0.0),
        ])
    return np.asarray(out, np.float32)


def motion_features(windows: np.ndarray) -> np.ndarray:
    """(N, window, 3) -> 36-dim aggregated vector."""
    if windows.size == 0:
        return np.zeros(36)
    stats = np.vstack([window_stats(w) for w in windows])  # (N, 18)
    return np.concatenate([stats.mean(axis=0), stats.std(axis=0)])


# --------------------------------------------------------------------------
# Tapping
# --------------------------------------------------------------------------
def tap_features(intervals_ms: list[float], durations_ms: list[float]) -> np.ndarray:
    """20-dim vector of tap-timing statistics."""
    iv = np.asarray(intervals_ms, float)
    du = np.asarray(durations_ms, float)
    if iv.size < 2:
        return np.zeros(20)
    out = [
        iv.mean(), iv.std(), iv.min(), iv.max(),
        np.median(iv), np.percentile(iv, 25), np.percentile(iv, 75),
        iv.std() / (iv.mean() + 1e-9),  # rhythm variability
        du.mean(), du.std(),
    ]
    # Slope & curvature estimates of the interval series.
    x = np.arange(iv.size)
    out += [float(np.polyfit(x, iv, 1)[0]), float(np.polyfit(x, iv, 2)[0])]
    out += [iv[0], iv[-1], float(np.diff(iv).mean())]
    # Reaction-adjusted extras.
    out += [du.min(), du.max(), np.median(du), float(np.ptp(du)), du.std()]
    return np.asarray(out[:20], np.float32)


# --------------------------------------------------------------------------
# Spiral / handwriting
# --------------------------------------------------------------------------
def spiral_features(path: np.ndarray) -> np.ndarray:
    """(N, 2) polyline -> 15-dim vector (smoothness, tremor, speed stats)."""
    if path.ndim != 2 or len(path) < 10:
        return np.zeros(15)
    d = np.diff(path, axis=0)
    seglen = np.hypot(d[:, 0], d[:, 1])
    total = seglen.sum()
    smoothness = total / (len(path) + 1e-9)
    tremor = np.sqrt(d[:, 0] ** 2 + d[:, 1] ** 2)  # tremor ~ speed changes
    speed = seglen / 0.01  # assume 100Hz sampling
    # Deviation from a circle fit.
    cx, cy = path.mean(axis=0)
    radii = np.hypot(path[:, 0] - cx, path[:, 1] - cy)
    radial_dev = radii.std() / (radii.mean() + 1e-9)
    out = [
        smoothness, total, tremor.mean(), tremor.std(), tremor.max(),
        speed.mean(), speed.std(), float(np.ptp(path[:, 0])),
        float(np.ptp(path[:, 1])), radial_dev,
        len(path), float(np.arccos(np.clip(np.max(d[:, 0] / (seglen + 1e-9)), -1, 1))),
        float(np.angle(d[-1, 0] + 1j * d[-1, 1])), tremor.sum(), float(path[:, 1].mean()),
    ]
    return np.asarray(out[:15], np.float32)


def extract_all(sample: dict) -> dict[str, np.ndarray]:
    """Top-level dispatcher: dict with raw pieces -> per-modality vectors."""
    feats = {
        "voice": voice_features(sample.get("audio", np.zeros(0))),
        "motion": motion_features(sample.get("windows", np.zeros((0, 300, 3)))),
        "tap": tap_features(sample.get("tap_intervals", []), sample.get("tap_durations", [])),
        "spiral": spiral_features(sample.get("spiral_path", np.zeros((0, 2)))),
    }
    return feats