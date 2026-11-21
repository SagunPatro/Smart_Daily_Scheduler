import os
import statistics
import threading
from typing import Optional, Any, Tuple, Dict
import numpy as np
import pandas as pd
from sklearn.preprocessing import LabelEncoder
from sklearn.ensemble import RandomForestRegressor
import joblib

MODEL_FILE = "duration_model.pkl"

# ---------- MODEL CACHING SINGLETON & THREAD LOCK ----------
_CACHED_MODEL: Optional[RandomForestRegressor] = None
_CACHED_LE: Optional[LabelEncoder] = None
_LAST_MTIME: Optional[float] = None
_CACHE_LOCK = threading.Lock()


def normalize_task_name(name: str) -> str:
    """
    Normalizes a task name by trimming whitespace and converting to lowercase.
    Ensures ' Study ', 'study', 'STUDY' all resolve to 'study'.
    """
    if not name:
        return ""
    return str(name).strip().lower()


def load_cached_model() -> Tuple[Optional[RandomForestRegressor], Optional[LabelEncoder]]:
    """
    Retrieves the global (model, label_encoder) from in-memory cache in a thread-safe manner.
    Loads from disk only on first access or when duration_model.pkl mtime changes.
    """
    global _CACHED_MODEL, _CACHED_LE, _LAST_MTIME

    if not os.path.exists(MODEL_FILE):
        return None, None

    try:
        current_mtime = os.path.getmtime(MODEL_FILE)
        with _CACHE_LOCK:
            if _CACHED_MODEL is not None and _CACHED_LE is not None and _LAST_MTIME == current_mtime:
                return _CACHED_MODEL, _CACHED_LE

        loaded = joblib.load(MODEL_FILE)
        if isinstance(loaded, (tuple, list)) and len(loaded) >= 2:
            with _CACHE_LOCK:
                _CACHED_MODEL = loaded[0]
                _CACHED_LE = loaded[1]
                _LAST_MTIME = current_mtime
                return _CACHED_MODEL, _CACHED_LE
        return None, None
    except Exception:
        return None, None


def invalidate_model_cache() -> None:
    """
    Invalidates the in-memory cached model in a thread-safe manner so the next prediction reloads from disk.
    Called immediately after successful retraining.
    """
    global _CACHED_MODEL, _CACHED_LE, _LAST_MTIME
    with _CACHE_LOCK:
        _CACHED_MODEL = None
        _CACHED_LE = None
        _LAST_MTIME = None


def is_model_cached() -> bool:
    """
    Returns True if an active model object is currently loaded in the thread-safe memory cache.
    """
    with _CACHE_LOCK:
        return _CACHED_MODEL is not None


def get_model_last_trained() -> Optional[str]:
    """
    Returns the ISO timestamp of the active duration_model.pkl file modification time, or None if missing.
    """
    if not os.path.exists(MODEL_FILE):
        return None
    try:
        from datetime import datetime, timezone
        mtime = os.path.getmtime(MODEL_FILE)
        return datetime.fromtimestamp(mtime, tz=timezone.utc).isoformat()
    except Exception:
        return None



def clamp_duration(planned_duration: int, raw_predicted: float) -> int:
    """
    Applies safety guardrails to flexible-task duration predictions:
    - Minimum allowed: max(5, int(0.5 * planned_duration))
    - Maximum allowed: max(min_val, int(2.5 * planned_duration))
    - Absolute minimum: 5 minutes
    """
    planned = max(1, int(planned_duration))
    min_val = max(5, int(0.5 * planned))
    max_val = max(min_val, int(2.5 * planned))
    raw = int(round(float(raw_predicted)))
    return max(min_val, min(max_val, raw))


def predict_duration(
    task_name: str,
    planned_duration: int,
    start_minute: int,
    user_id: Optional[int] = None,
    db_session: Optional[Any] = None
) -> int:
    """
    Three-tier duration prediction strategy:
    - Tier 1 (Personalized User History): >= 3 completed logs for (user_id, normalized_task).
    - Tier 2 (Global ML Model): Cached RandomForest model with normalized LabelEncoder.
    - Tier 3 (Cold Start): Safe fallback to planned_duration.
    All outputs are clamped within safety guardrails [0.5x, 2.5x, min 5].
    """
    norm_task = normalize_task_name(task_name)
    planned = max(1, int(planned_duration))

    # ---------- TIER 1: PERSONALIZED USER HISTORY ----------
    if user_id is not None and db_session is not None:
        try:
            from models import FeedbackLog
            user_logs = db_session.query(FeedbackLog).filter(
                FeedbackLog.user_id == user_id,
                FeedbackLog.completed == "yes"
            ).all()

            matching_logs = [
                log for log in user_logs
                if normalize_task_name(log.task_name) == norm_task and log.planned_duration > 0
            ]

            if len(matching_logs) >= 3:
                ratios = [log.actual_duration / float(log.planned_duration) for log in matching_logs]
                median_ratio = statistics.median(ratios)
                personalized_raw = planned * median_ratio
                return clamp_duration(planned, personalized_raw)
        except Exception:
            # Fall through safely to Tier 2 on any DB lookup error
            pass

    # ---------- TIER 2: GLOBAL ML MODEL ----------
    model, le = load_cached_model()
    if model is not None and le is not None:
        try:
            start_hour = int(start_minute) // 60
            if hasattr(le, "classes_") and norm_task in le.classes_:
                task_encoded = le.transform([norm_task])[0]
                X = pd.DataFrame(
                    [[planned, start_hour, task_encoded]],
                    columns=["planned_duration", "start_hour", "task_encoded"]
                )
                predicted_raw = model.predict(X)[0]
                return clamp_duration(planned, predicted_raw)
        except Exception:
            # Fall through safely to Tier 3 on any prediction error
            pass

    # ---------- TIER 3: COLD START ----------
    return clamp_duration(planned, planned)


def train_model(
    db_session: Optional[Any] = None,
    csv_file: str = "user_logs.csv"
) -> Dict[str, Any]:
    """
    Retrains the global duration estimation model:
    1. Loads seed data from the first 25 legitimate rows of user_logs.csv.
    2. Loads completed records from authoritative SQLite feedback_logs table.
    3. Combines datasets and normalizes task names without double-counting.
    4. Validates minimum sample threshold (>= 5 samples).
    5. Fits RandomForestRegressor and LabelEncoder.
    6. Validates predictions are finite and non-negative.
    7. Atomically saves to duration_model.pkl and invalidates memory cache.
    """
    data_rows = []
    seen_keys = set()

    # 1. Load legitimate seed data from first 25 rows of user_logs.csv
    if os.path.exists(csv_file):
        try:
            with open(csv_file, "r", encoding="utf-8") as f:
                lines = [line.strip() for line in f if line.strip()][:25]

            for line in lines:
                parts = [p.strip() for p in line.split(",")]
                if len(parts) >= 8:
                    # [date, task_name, planned_start, planned_end, planned_duration, actual_duration, completed, feedback]
                    task_name = parts[1]
                    try:
                        planned_start = int(parts[2])
                        actual_dur = int(parts[5])
                        planned_dur = int(parts[4])
                        completed = parts[6].lower()
                    except ValueError:
                        continue

                    if completed == "yes" and planned_dur > 0:
                        norm_name = normalize_task_name(task_name)
                        key = (norm_name, planned_start, planned_dur, actual_dur)
                        if key not in seen_keys:
                            seen_keys.add(key)
                            data_rows.append({
                                "task_name": norm_name,
                                "planned_duration": planned_dur,
                                "planned_start": planned_start,
                                "actual_duration": actual_dur
                            })
        except Exception as e:
            print(f"[WARN] Error reading seed rows from {csv_file}: {e}")

    # 2. Load completed records from feedback_logs (Authoritative Database Source)
    close_session = False
    if db_session is None:
        try:
            from database import SessionLocal
            db_session = SessionLocal()
            close_session = True
        except Exception:
            db_session = None

    if db_session is not None:
        try:
            from models import FeedbackLog
            db_records = db_session.query(FeedbackLog).filter(
                FeedbackLog.completed == "yes"
            ).all()

            for rec in db_records:
                if rec.planned_duration > 0:
                    data_rows.append({
                        "task_name": normalize_task_name(rec.task_name),
                        "planned_duration": rec.planned_duration,
                        "planned_start": rec.planned_start,
                        "actual_duration": rec.actual_duration
                    })
        except Exception as e:
            print(f"[WARN] Error reading feedback_logs from database: {e}")
        finally:
            if close_session and db_session is not None:
                db_session.close()

    # 3. Check sample threshold
    total_samples = len(data_rows)
    if total_samples < 5:
        msg = f"Not enough completed task samples to train (minimum 5 required, found {total_samples})"
        print(f"[WARN] {msg}")
        return {"success": False, "sample_count": total_samples, "message": msg}

    df = pd.DataFrame(data_rows)

    # 4. Feature engineering
    df["start_hour"] = df["planned_start"] // 60
    le = LabelEncoder()
    df["task_encoded"] = le.fit_transform(df["task_name"])

    X = df[["planned_duration", "start_hour", "task_encoded"]]
    y = df["actual_duration"]

    # 5. Model fitting
    model = RandomForestRegressor(
        n_estimators=100,
        random_state=42
    )
    model.fit(X, y)

    # 6. Validation gates: verify predictions are finite and non-negative
    preds = model.predict(X)
    if not np.all(np.isfinite(preds)) or not np.all(preds >= 0):
        msg = "Model validation failed: non-finite or negative predictions detected"
        print(f"[ERROR] {msg}")
        return {"success": False, "sample_count": total_samples, "message": msg}

    # 7. Atomic model replacement
    tmp_file = f"{MODEL_FILE}.tmp"
    try:
        joblib.dump((model, le), tmp_file)
        os.replace(tmp_file, MODEL_FILE)
        invalidate_model_cache()
        msg = "Model retrained successfully"
        print(f"[INFO] {msg}! (samples: {total_samples})")
        return {"success": True, "sample_count": total_samples, "message": msg}
    except Exception as e:
        if os.path.exists(tmp_file):
            try:
                os.remove(tmp_file)
            except Exception:
                pass
        msg = f"Failed to save retrained model artifact: {e}"
        print(f"[ERROR] {msg}")
        return {"success": False, "sample_count": total_samples, "message": msg}



if __name__ == "__main__":
    train_model()

