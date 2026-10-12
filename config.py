"""Global configuration and paths for ParkiMed AI.

Every module imports constants from here so the Android-side mirrors stay
in sync. When preprocessing constants change, they are re-exported to
`preprocessing_config.json` which the app consumes.
"""
import json
from pathlib import Path

# --------------------------------------------------------------------------
# Paths
# --------------------------------------------------------------------------
PROJECT_ROOT = Path(__file__).resolve().parent
RAW_DIR = PROJECT_ROOT / "data" / "raw"
PROCESSED_DIR = PROJECT_ROOT / "data" / "processed"
FEATURES_DIR = PROJECT_ROOT / "data" / "features"
MODELS_DIR = PROJECT_ROOT / "models"
OUTPUTS_DIR = PROJECT_ROOT / "outputs"
CONFIG_JSON = MODELS_DIR / "preprocessing_config.json"

for _p in (RAW_DIR, PROCESSED_DIR, FEATURES_DIR, MODELS_DIR, OUTPUTS_DIR):
    _p.mkdir(parents=True, exist_ok=True)

# --------------------------------------------------------------------------
# Sensor / signal settings
# --------------------------------------------------------------------------
SAMPLE_RATE = 100            # Hz target for motion sensors
AUDIO_RATE = 16000           # Hz target for voice
WINDOW_SIZE = 300            # motion samples per window (3s @ 100Hz)
WINDOW_OVERLAP = 150         # 1s overlap
N_MFCC = 13                  # MFCC coefficients per frame
VOICE_TARGET_SECS = 30       # expected voice clip length
WALK_TARGET_SECS = 30        # expected gait clip length
TAP_TARGET_SECS = 10         # expected tap test length

# --------------------------------------------------------------------------
# Feature dimensions (must match Android app expectations)
# --------------------------------------------------------------------------
VOICE_FEATURES = 39           # 13 MFCC * 3 (delta, delta-delta) packed
MOTION_FEATURES = 36          # per-window stats * 6 channels (accel+gyro)
TAP_FEATURES = 20
SPIRAL_FEATURES = 15

# --------------------------------------------------------------------------
# Model / training
# --------------------------------------------------------------------------
SEED = 42
EPOCHS = 60
BATCH_SIZE = 32
LEARNING_RATE = 1e-3
CLASS_WEIGHTING = True         # handle ON/OFF imbalance
TEST_SPLIT = 0.1
VAL_SPLIT = 0.1

# --------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------
ON_OFF_THRESHOLD = 0.5
SEVERITY_RANGE = (0.0, 5.0)

_INPUT_KEYS = {
    "voice": VOICE_FEATURES,
    "motion": MOTION_FEATURES,
    "tap": TAP_FEATURES,
    "spiral": SPIRAL_FEATURES,
}


def export_preprocessing_config(extra: dict | None = None) -> None:
    """Write the constants the Android app needs to replicate preprocessing."""
    data = {
        "sample_rate_hz": SAMPLE_RATE,
        "audio_rate_hz": AUDIO_RATE,
        "window_size": WINDOW_SIZE,
        "window_overlap": WINDOW_OVERLAP,
        "n_mfcc": N_MFCC,
        "input_shapes": _INPUT_KEYS,
        "on_off_threshold": ON_OFF_THRESHOLD,
        "severity_range": list(SEVERITY_RANGE),
        "feature_order": list(_INPUT_KEYS.keys()),
    }
    if extra:
        data.update(extra)
    CONFIG_JSON.write_text(json.dumps(data, indent=2))
    return CONFIG_JSON