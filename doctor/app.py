"""ParkiMed doctor dashboard — reads Firestore assessments.

Run with:
    export GOOGLE_APPLICATION_CREDENTIALS=/path/to/parkimed-sa.json
    python app.py
"""
from __future__ import annotations

import os

from flask import Flask, abort, render_template_string
from google.cloud import firestore

app = Flask(__name__)

INDEX_HTML = """
<!doctype html>
<html><head><meta charset="utf-8"><title>ParkiMed — Patients</title>
<style>
body{font-family:system-ui;background:#0B0F14;color:#F2F7FB;padding:24px}
td,th{padding:8px 14px;text-align:left}table{border-collapse:collapse;width:100%}
th{border-bottom:2px solid #00E0B0;color:#00E0B0}.on{color:#00E0B0;font-weight:600}
.off{color:#FF5C5C;font-weight:600}
</style></head><body>
<h1>ParkiMed — Patients</h1>
<table><tr><th>Device</th><th>Last severity</th><th>State</th><th>Assessments</th><th>Last seen</th></tr>
{% for p in patients %}
<tr>
 <td><a href="/patient/{{ p.device }}">{{ p.device[:8] }}</a></td>
 <td>{{ "%.2f"|format(p.severity) }}</td>
 <td class="{{ 'on' if p.onOff=='ON' else 'off' }}">{{ p.onOff }}</td>
 <td>{{ p.count }}</td>
 <td>{{ p.last_seen }}</td>
</tr>
{% endfor %}
</table>
<h3>Total assessments: {{ total }}</h3>
</body></html>
"""

PATIENT_HTML = """
<!doctype html>
<html><head><meta charset="utf-8"><title>ParkiMed — {{ device[:8] }}</title>
<style>
body{font-family:system-ui;background:#0B0F14;color:#F2F7FB;padding:24px}
td,th{padding:6px 12px}.on{color:#00E0B0}.off{color:#FF5C5C}
</style></head><body>
<h1>Patient {{ device[:8] }}</h1>
<canvas id="c" width="900" height="250"></canvas>
<table><tr><th>Time</th><th>ON/OFF</th><th>Severity</th><th>Confidence</th><th>Sources</th></tr>
{% for a in rows %}
<tr>
  <td>{{ a.timestamp }}</td>
  <td class="{{ 'on' if a.onOff=='ON' else 'off' }}">{{ a.onOff }}</td>
  <td>{{ "%.2f"|format(a.severity) }}</td>
  <td>{{ "%.0f"|format(a.confidence*100) }}%</td>
  <td>{{ a.sources }}</td>
</tr>
{% endfor %}
</table>
<script>
const sev = {{ sev_json | safe }};
const c = document.getElementById('c'), ctx = c.getContext('2d');
ctx.strokeStyle = '#00E0B0';
if (sev.length > 1) {
  const max = 5, w = c.width, h = c.height;
  ctx.beginPath();
  sev.forEach((s, i) => {
    const x = i / (sev.length - 1) * w;
    const y = h - (s / max) * h;
    i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
  });
  ctx.stroke();
}
</script>
<p><a href="/">← all patients</a> · <a href="/raw/{{ device }}">raw JSON</a></p>
</body></html>
"""


def _db() -> firestore.Client:
    return firestore.Client()


def _fetch_all():
    docs = _db().collection("assessments").stream()
    return [d.to_dict() | {"id": d.id} for d in docs]


@app.get("/")
def index():
    rows = _fetch_all()
    by_dev: dict[str, dict] = {}
    for r in rows:
        dev = r.get("deviceId", "unknown")
        b = by_dev.setdefault(dev, {"rows": []})
        b["rows"].append(r)
    patients = []
    for dev, b in by_dev.items():
        last = max(b["rows"], key=lambda r: r.get("timestamp", 0))
        patients.append(
            {
                "device": dev,
                "severity": last.get("severity", 0),
                "onOff": last.get("onOff", "-"),
                "count": len(b["rows"]),
                "last_seen": last and _fmt_ts(last.get("timestamp", 0)) or "-",
            }
        )
    patients.sort(key=lambda p: p["last_seen"], reverse=True)
    return render_template_string(INDEX_HTML, patients=patients, total=len(rows))


@app.get("/patient/<device>")
def patient(device: str):
    rows = [r for r in _fetch_all() if r.get("deviceId") == device]
    if not rows:
        abort(404)
    rows.sort(key=lambda r: r.get("timestamp", 0))
    sev = [min(max(float(r.get("severity", 0)), 0), 5) for r in rows]
    items = [
        {
            "timestamp": _fmt_ts(r.get("timestamp", 0)),
            "onOff": r.get("onOff", "-"),
            "severity": float(r.get("severity", 0)),
            "confidence": float(r.get("confidence", 0)),
            "sources": r.get("sourcesUsed", "-"),
        }
        for r in rows
    ]
    return render_template_string(
        PATIENT_HTML,
        device=device,
        rows=items,
        sev_json=json.dumps(sev),
    )


@app.get("/raw/<device>")
def raw(device: str):
    rows = [r for r in _fetch_all() if r.get("deviceId") == device]
    if not rows:
        abort(404)
    rows.sort(key=lambda r: r.get("timestamp", 0))
    return json.dumps(rows, indent=2, default=str), 200, {"Content-Type": "application/json"}


def _fmt_ts(ts):
    import datetime

    try:
        return datetime.datetime.fromtimestamp(ts / 1000).strftime("%Y-%m-%d %H:%M")
    except Exception:
        return str(ts)


import json  # noqa: E402  (kept below for readability of the templates)

if __name__ == "__main__":
    app.run(host=os.environ.get("HOST", "127.0.0.1"), port=int(os.environ.get("PORT", 5000)))