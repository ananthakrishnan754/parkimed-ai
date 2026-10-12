# ParkiMed Doctor Dashboard

Reads assessments synced to **Cloud Firestore** from the Android app and shows
per-patient ON/OFF + severity trends. Authentication is Google Application
Default Credentials on a service-account.

```
Android app ──(SyncWorker)──▶ Firestore "assessments" ◀── this dashboard (Flask)
```

## Setup (one-time, needs the Firebase project)

1. Create the Firebase project (console.firebase.google.com) and add the
   Android app with package **`com.parkimed.ai`** — this produces the
   `google-services.json` that goes into `ParkiMed-Android/app/`.
2. Enable **Cloud Firestore** in the project (test mode is fine for dev).
3. Create a **service account** (Project settings → Service accounts → Generate
   new private key → `parkimed-sa.json`). This file is secret — don't commit it.

## Run

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
export GOOGLE_APPLICATION_CREDENTIALS=/abs/path/parkimed-sa.json
.venv/bin/python app.py
# open http://127.0.0.1:5000
```

## Pages

- `/` — patient table (device): latest severity, ON/OFF, last assessment, count
- `/patient/<device_id>` — severity + confidence time series and ON/OFF history
- `/raw/<device_id>` — raw JSON of all rows for export/analysis

## Firestore security note

For the team demo this set of rules lets the app **write** and everyone **read**:
```js
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /assessments/{a} {
      allow read: if true;
      allow write: if true; // tighten before production!
    }
  }
}
```