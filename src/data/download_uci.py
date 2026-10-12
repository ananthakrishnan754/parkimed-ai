"""Download UCI Parkinson's datasets (no registration required).

Provides a reliable baseline dataset while waiting on mPower Synapse approval.

Datasets
--------
1. UCI Parkinson's Telemonitoring  -> 24 voice metrics + UPDRS scores
2. UCI Parkinson's Speech Set      -> sustained phonation features + PD label
"""
from __future__ import annotations

import io
import requests
import pandas as pd
from pathlib import Path
import sys

sys.path.append(str(Path(__file__).resolve().parents[2]))
from config import RAW_DIR

UCI_URIS = {
    "telemonitoring": "https://archive.ics.uci.edu/ml/machine-learning-databases/parkinsons/telemonitoring/parkinsons_updrs.data",
    "speech": "https://archive.ics.uci.edu/ml/machine-learning-databases/parkinsons/parkinsons.data",
}

TELEMONITORING_COLS = [
    "subject#", "age", "sex", "test_time", "motor_UPDRS", "total_UPDRS",
    "Jitter(%)", "Jitter(Abs)", "Jitter:RAP", "Jitter:PPQ5", "Jitter:DDP",
    "Shimmer", "Shimmer(dB)", "Shimmer:APQ3", "Shimmer:APQ5", "Shimmer:APQ11",
    "Shimmer:DDA", "NHR", "HNR", "RPDE", "DFA", "PPE",
]


def _fetch(uri: str) -> str:
    r = requests.get(uri, timeout=120)
    r.raise_for_status()
    return r.text


def download_telemonitoring() -> Path:
    out = RAW_DIR / "uci_telemonitoring.csv"
    if out.exists():
        print(f"[skip] {out.name} already present")
        return out
    print("Downloading UCI Telemonitoring ...")
    df = pd.read_csv(io.StringIO(_fetch(UCI_URIS["telemonitoring"])))
    # Keep only the columns we actually use for voice-based severity regression.
    keep = [c for c in TELEMONITORING_COLS if c in df.columns]
    df[keep].to_csv(out, index=False)
    print(f"[ok] saved {len(df)} rows -> {out}")
    return out


def download_speech_set() -> Path:
    out = RAW_DIR / "uci_speech.csv"
    if out.exists():
        print(f"[skip] {out.name} already present")
        return out
    print("Downloading UCI Parkinson Speech Set ...")
    pd.read_csv(io.StringIO(_fetch(UCI_URIS["speech"]))).to_csv(out, index=False)
    print(f"[ok] saved -> {out}")
    return out


def main() -> None:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    download_telemonitoring()
    download_speech_set()


if __name__ == "__main__":
    main()