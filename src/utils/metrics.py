"""Shared evaluation metrics helpers."""
from __future__ import annotations

import numpy as np
from sklearn.metrics import accuracy_score, f1_score, roc_auc_score, confusion_matrix


def report_metrics(y_true: np.ndarray, y_prob: np.ndarray, threshold: float = 0.5) -> dict:
    """y_true: (N,) 0/1; y_prob: (N,) 1-D probability of positive class."""
    y_pred = (y_prob >= threshold).astype(int)
    acc = accuracy_score(y_true, y_pred)
    f1 = f1_score(y_true, y_pred, zero_division=0)
    try:
        auc = roc_auc_score(y_true, y_prob)
    except ValueError:
        auc = float("nan")
    tn, fp, fn, tp = confusion_matrix(y_true, y_pred, labels=[0, 1]).ravel()
    return {
        "accuracy": float(acc),
        "f1": float(f1),
        "auc": float(auc),
        "sensitivity": float(tp / (tp + fn)) if tp + fn else 0.0,
        "specificity": float(tn / (tn + fp)) if tn + fp else 0.0,
        "tp": int(tp), "fp": int(fp), "fn": int(fn), "tn": int(tn),
    }