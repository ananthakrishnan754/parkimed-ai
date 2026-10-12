# ParkiMed AI — Source Code

Adaptive Multimodal Fusion Framework for Smartphone-Based Medication Monitoring
for Parkinson's Disease. B.Tech AI & ML final-year project.

## Repository Layout

```
parkimed-ai/
├── config.py                 # Global configuration & paths
├── requirements.txt          # Python dependencies
├── src/
│   ├── data/
│   │   ├── download_uci.py   # Download UCI datasets (no auth)
│   │   └── preprocessing.py  # Clean / normalize / window sensor data
│   ├── features/
│   │   └── feature_extraction.py  # Voice/Motion/Tap/Spiral features
│   ├── models/
│   │   ├── fusion_model.py   # Adaptive attention fusion architecture
│   │   ├── baseline_models.py# Single-modality baselines
│   │   ├── train.py          # Training entrypoint
│   │   └── evaluate.py       # Evaluation & reporting
│   ├── export/
│   │   └── export_tflite.py  # Convert trained model -> parkimed.tflite
│   └── utils/
│       ├── metrics.py        # Accuracy, F1, AUC, latency
│       └── logger.py         # Simple logging setup
├── scripts/
│   └── run_pipeline.sh       # Full pipeline in one shot
├── models/                   # Trained artifacts live here
├── outputs/                  # Metrics / charts / reports
└── data/                     # Raw + processed datasets
```

## Setup

```bash
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
```

## Pipeline

```bash
# 1. Download UCI datasets (no registration needed)
python src/data/download_uci.py

# 2. Preprocess (works on any raw sensor folders you add under data/raw/)
python src/data/preprocessing.py

# 3. Extract features
python -m src.features.feature_extraction

# 4. Train the fusion model
python -m src.models.train --model fusion

# 5. Evaluate
python -m src.models.evaluate

# 6. Export to TFLite
python -m src.export.export_tflite --model models/fusion_best.h5
```

> **mPower data:** requires a free Synapse account (https://www.synapse.org/syn4993293).
> Download approved files into `data/raw/` manually, then run `preprocessing.py`.
> Optional scripted sync via the `synapseclient` package (add your credentials in
> `download_mpower.py` if you want it fully automated).

## Notes for the team

- Data is always split **by patient** (train/val/test) to avoid leakage.
- The fusion model must beat every single-modality baseline — that comparison
  is the core evidence for the project report.
- Preprocessing constants are exported to JSON so the Android app can mirror
  them exactly.