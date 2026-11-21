import os
import unittest
import uuid
import tempfile

if not os.getenv("JWT_SECRET_KEY"):
    os.environ["JWT_SECRET_KEY"] = "test-secret-key-12345678901234567890"

from database import SessionLocal
from models import User, FeedbackLog
from ml_duration_model import (
    predict_duration,
    clamp_duration,
    normalize_task_name,
    invalidate_model_cache,
    MODEL_FILE
)
import logger


class Phase11MLEdgeCaseTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original_log_file = logger.LOG_FILE
        cls.test_csv = tempfile.NamedTemporaryFile(delete=False, suffix=".csv")
        cls.test_csv.close()
        logger.LOG_FILE = cls.test_csv.name

        cls.original_model_bytes = None
        if os.path.exists(MODEL_FILE):
            with open(MODEL_FILE, "rb") as f:
                cls.original_model_bytes = f.read()

        cls.db = SessionLocal()
        cls.user_name = f"ml_edge_user_{uuid.uuid4().hex[:8]}"
        cls.user_obj = User(username=cls.user_name, email=f"{cls.user_name}@example.com", hashed_password="pw")
        cls.db.add(cls.user_obj)
        cls.db.commit()
        cls.db.refresh(cls.user_obj)

    @classmethod
    def tearDownClass(cls):
        logger.LOG_FILE = cls.original_log_file
        if os.path.exists(cls.test_csv.name):
            try:
                os.unlink(cls.test_csv.name)
            except Exception:
                pass

        if cls.user_obj:
            user = cls.db.query(User).filter(User.id == cls.user_obj.id).first()
            if user:
                cls.db.delete(user)
        cls.db.commit()
        cls.db.close()

        if cls.original_model_bytes is not None:
            with open(MODEL_FILE, "wb") as f:
                f.write(cls.original_model_bytes)
            invalidate_model_cache()

    def test_01_tier_threshold_progression(self):
        """TEST 1: Verify Tier 1 personalization activates ONLY when logs >= 3."""
        task_name = f"Progression Task {uuid.uuid4().hex[:4]}"

        # 0 logs -> cold start (planned=60 -> 60)
        pred0 = predict_duration(task_name, 60, 540, self.user_obj.id, self.db)
        self.assertEqual(pred0, 60)

        # Add 1 log (actual=120)
        self.db.add(FeedbackLog(user_id=self.user_obj.id, task_name=task_name, planned_start=540, planned_end=600, planned_duration=60, actual_duration=120, completed="yes"))
        self.db.commit()
        pred1 = predict_duration(task_name, 60, 540, self.user_obj.id, self.db)
        self.assertEqual(pred1, 60)  # Tier 1 inactive (< 3)

        # Add 2nd log
        self.db.add(FeedbackLog(user_id=self.user_obj.id, task_name=task_name, planned_start=600, planned_end=660, planned_duration=60, actual_duration=120, completed="yes"))
        self.db.commit()
        pred2 = predict_duration(task_name, 60, 540, self.user_obj.id, self.db)
        self.assertEqual(pred2, 60)  # Tier 1 inactive (< 3)

        # Add 3rd log -> Tier 1 activates! (60 * 2.0 = 120)
        self.db.add(FeedbackLog(user_id=self.user_obj.id, task_name=task_name, planned_start=660, planned_end=720, planned_duration=60, actual_duration=120, completed="yes"))
        self.db.commit()
        pred3 = predict_duration(task_name, 60, 540, self.user_obj.id, self.db)
        self.assertEqual(pred3, 120)  # Tier 1 activated!

    def test_02_extreme_duration_clamping(self):
        """TEST 2: Extreme user actual durations are clamped to [0.5x, 2.5x, min 5]."""
        extreme_task = f"Extreme Task {uuid.uuid4().hex[:4]}"

        # Insert 3 logs with 10x actual duration (planned=60, actual=600)
        for i in range(3):
            self.db.add(FeedbackLog(
                user_id=self.user_obj.id,
                task_name=extreme_task,
                planned_start=540,
                planned_end=600,
                planned_duration=60,
                actual_duration=600,
                completed="yes"
            ))
        self.db.commit()

        # Raw ratio = 10.0 -> Clamped to 2.5x planned = 150 minutes
        pred = predict_duration(extreme_task, 60, 540, self.user_obj.id, self.db)
        self.assertEqual(pred, 150)

    def test_03_missing_or_corrupted_model_resilience(self):
        """TEST 3: Missing or corrupted duration_model.pkl falls back gracefully to planned duration."""
        invalidate_model_cache()

        # Temporarily hide model file if exists
        temp_hidden = f"{MODEL_FILE}.bak_test"
        if os.path.exists(MODEL_FILE):
            os.rename(MODEL_FILE, temp_hidden)

        try:
            # Missing model file -> safe fallback to planned duration
            pred_missing = predict_duration("unknown task", 45, 540)
            self.assertEqual(pred_missing, 45)

            # Corrupted model file -> write garbage bytes to MODEL_FILE
            with open(MODEL_FILE, "wb") as f:
                f.write(b"CORRUPTED_GARBAGE_PICKLE_BYTES_12345")
            invalidate_model_cache()

            pred_corrupt = predict_duration("unknown task", 45, 540)
            self.assertEqual(pred_corrupt, 45)
        finally:
            # Restore model file
            if os.path.exists(MODEL_FILE):
                os.remove(MODEL_FILE)
            if os.path.exists(temp_hidden):
                os.rename(temp_hidden, MODEL_FILE)
            invalidate_model_cache()


if __name__ == "__main__":
    unittest.main()
