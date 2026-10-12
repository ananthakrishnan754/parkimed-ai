#!/usr/bin/env bash
# ParkiMed AI — run every stage end to end.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==[1/6] Generate mock data (until mPower approval) =="
./venv/bin/python scripts/generate_mock_data.py

echo "==[2/6] Download UCI datasets =="
./venv/bin/python src/data/download_uci.py

echo "==[3/6] Build unified training CSV =="
./venv/bin/python src/data/build_training_data.py

echo "==[4/6] Train fusion model =="
./venv/bin/python -m src.models.train --model fusion

echo "==[5/6] Train baselines =="
for m in voice motion tap spiral; do
  ./venv/bin/python -m src.models.train --model "$m" || echo "[warn] baseline $m skipped"
done

echo "==[6/6] Evaluate + export TFLite =="
./venv/bin/python -m src.models.evaluate
./venv/bin/python -m src.export.export_tflite --model models/fusion_best.keras --float16

echo "Done. Artifacts in models/ and outputs/."