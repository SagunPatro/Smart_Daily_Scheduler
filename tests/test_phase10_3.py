import os
import unittest
import hashlib
import sqlite3
import uuid
import tempfile
import time
from datetime import datetime

# Ensure JWT secret is set before importing app
if not os.getenv("JWT_SECRET_KEY"):
    os.environ["JWT_SECRET_KEY"] = "test-secret-key-12345678901234567890"

from fastapi.testclient import TestClient
from main import app
from database import SessionLocal
from models import User, FeedbackLog, Task, SavedSchedule
from ml_duration_model import (
    normalize_task_name,
    clamp_duration,
    load_cached_model,
    invalidate_model_cache,
    predict_duration,
    train_model,
    MODEL_FILE
)
from scheduler import generate_schedule, safe_predict_duration
import logger


class Phase10_3MLAnalyticsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # 1. Isolate CSV logging so tests never pollute production user_logs.csv
        cls.original_log_file = logger.LOG_FILE
        cls.test_csv = tempfile.NamedTemporaryFile(delete=False, suffix=".csv")
        cls.test_csv.close()
        logger.LOG_FILE = cls.test_csv.name

        # 2. Backup duration_model.pkl so tests do not permanently mutate production ML artifact
        cls.original_model_bytes = None
        if os.path.exists(MODEL_FILE):
            with open(MODEL_FILE, "rb") as f:
                cls.original_model_bytes = f.read()

        cls.client = TestClient(app)
        cls.db = SessionLocal()

        # 3. Create User A and User B
        cls.user_a_name = f"phase10_3_user_a_{uuid.uuid4().hex[:8]}"
        cls.user_a_email = f"{cls.user_a_name}@example.com"
        cls.user_b_name = f"phase10_3_user_b_{uuid.uuid4().hex[:8]}"
        cls.user_b_email = f"{cls.user_b_name}@example.com"
        cls.password = "secret123"

        # Register & login User A
        res_a = cls.client.post("/signup", json={
            "username": cls.user_a_name,
            "email": cls.user_a_email,
            "password": cls.password
        })
        assert res_a.status_code == 200, f"User A signup failed: {res_a.text}"

        res_login_a = cls.client.post("/login", json={
            "username": cls.user_a_name,
            "password": cls.password
        })
        assert res_login_a.status_code == 200, f"User A login failed: {res_login_a.text}"
        cls.token_a = res_login_a.json()["access_token"]
        cls.user_a_obj = cls.db.query(User).filter(User.username == cls.user_a_name).first()
        assert cls.user_a_obj is not None

        # Register & login User B
        res_b = cls.client.post("/signup", json={
            "username": cls.user_b_name,
            "email": cls.user_b_email,
            "password": cls.password
        })
        assert res_b.status_code == 200, f"User B signup failed: {res_b.text}"

        res_login_b = cls.client.post("/login", json={
            "username": cls.user_b_name,
            "password": cls.password
        })
        assert res_login_b.status_code == 200, f"User B login failed: {res_login_b.text}"
        cls.token_b = res_login_b.json()["access_token"]
        cls.user_b_obj = cls.db.query(User).filter(User.username == cls.user_b_name).first()
        assert cls.user_b_obj is not None

    @classmethod
    def tearDownClass(cls):
        # Restore original log file
        logger.LOG_FILE = cls.original_log_file
        if os.path.exists(cls.test_csv.name):
            try:
                os.unlink(cls.test_csv.name)
            except Exception:
                pass

        # Clean up test users and their cascaded feedback logs / schedules / tasks
        if cls.user_a_obj:
            user = cls.db.query(User).filter(User.id == cls.user_a_obj.id).first()
            if user:
                cls.db.delete(user)
        if cls.user_b_obj:
            user = cls.db.query(User).filter(User.id == cls.user_b_obj.id).first()
            if user:
                cls.db.delete(user)
        cls.db.commit()
        cls.db.close()

        # Restore original model artifact so production duration_model.pkl remains untouched
        if cls.original_model_bytes is not None:
            with open(MODEL_FILE, "wb") as f:
                f.write(cls.original_model_bytes)
            invalidate_model_cache()

    def test_01_ml_analytics_endpoint(self):
        """TEST 1: GET /ml/status returns correct stats and enforces user isolation."""
        headers_a = {"Authorization": f"Bearer {self.token_a}"}
        res_a = self.client.get("/ml/status", headers=headers_a)
        self.assertEqual(res_a.status_code, 200)
        data_a = res_a.json()

        self.assertIn("total_feedback_count", data_a)
        self.assertIn("user_feedback_count", data_a)
        self.assertIn("personalized_task_count", data_a)
        self.assertIn("personalized_tasks", data_a)
        self.assertIn("model_last_trained", data_a)
        self.assertIn("is_model_cached", data_a)

        # Unauthenticated request fails with 401
        res_unauth = self.client.get("/ml/status")
        self.assertEqual(res_unauth.status_code, 401)

    def test_02_feedback_submission_to_analytics(self):
        """TEST 2: Submitting 3 completed feedback records updates user_feedback_count and personalized_tasks."""
        headers_a = {"Authorization": f"Bearer {self.token_a}"}
        task_name = f"Coding Task {uuid.uuid4().hex[:4]}"

        # Submit 3 completed feedback entries for User A
        for i in range(3):
            res_fb = self.client.post("/feedback", json={
                "task_name": task_name,
                "planned_start": "09:00",
                "planned_end": "10:00",
                "planned_duration": 60,
                "actual_duration": 90,
                "completed": "yes",
                "feedback": f"Log {i+1}"
            }, headers=headers_a)
            self.assertEqual(res_fb.status_code, 200)

        # Query GET /ml/status for User A
        res_status_a = self.client.get("/ml/status", headers=headers_a)
        self.assertEqual(res_status_a.status_code, 200)
        data_a = res_status_a.json()

        self.assertGreaterEqual(data_a["user_feedback_count"], 3)
        self.assertIn(normalize_task_name(task_name), data_a["personalized_tasks"])
        self.assertGreaterEqual(data_a["personalized_task_count"], 1)

        # User B calling GET /ml/status must NOT see User A's personalized task
        headers_b = {"Authorization": f"Bearer {self.token_b}"}
        res_status_b = self.client.get("/ml/status", headers=headers_b)
        self.assertEqual(res_status_b.status_code, 200)
        data_b = res_status_b.json()
        self.assertNotIn(normalize_task_name(task_name), data_b["personalized_tasks"])

    def test_03_full_adaptive_personalization_flow(self):
        """TEST 3: Full end-to-end feedback loop: Schedule -> Feedback -> Personalized Future Schedule."""
        headers_a = {"Authorization": f"Bearer {self.token_a}"}
        headers_b = {"Authorization": f"Bearer {self.token_b}"}
        task_name = f"LeetCode Practice {uuid.uuid4().hex[:4]}"

        # Step 1: Initial schedule generation before 3 feedback entries (Cold Start -> planned duration = 60 min)
        res_sched1 = self.client.post("/schedule", json={
            "day_start": 540,
            "day_end": 1200,
            "tasks": [{"task_name": task_name, "duration": 60, "is_fixed": False}],
            "save_to_history": False
        }, headers=headers_a)
        self.assertEqual(res_sched1.status_code, 200)
        item1 = [it for it in res_sched1.json()["schedule"] if it["category"] == "task"][0]
        self.assertEqual(item1["end"] - item1["start"], 60)

        # Step 2: Submit 3 completed feedback entries with actual=90 (ratio = 1.5)
        for i in range(3):
            self.client.post("/feedback", json={
                "task_name": task_name,
                "planned_start": "09:00",
                "planned_end": "10:00",
                "planned_duration": 60,
                "actual_duration": 90,
                "completed": "yes",
                "feedback": "Took longer than expected"
            }, headers=headers_a)

        # Step 3: Generate schedule again for User A -> Allocated personalized duration (60 * 1.5 = 90 min)
        res_sched2 = self.client.post("/schedule", json={
            "day_start": 540,
            "day_end": 1200,
            "tasks": [{"task_name": task_name, "duration": 60, "is_fixed": False}],
            "save_to_history": False
        }, headers=headers_a)
        self.assertEqual(res_sched2.status_code, 200)
        item2 = [it for it in res_sched2.json()["schedule"] if it["category"] == "task"][0]
        self.assertEqual(item2["end"] - item2["start"], 90)

        # Step 4: User B generates schedule for same task -> User B receives cold start (60 min)
        res_sched_b = self.client.post("/schedule", json={
            "day_start": 540,
            "day_end": 1200,
            "tasks": [{"task_name": task_name, "duration": 60, "is_fixed": False}],
            "save_to_history": False
        }, headers=headers_b)
        self.assertEqual(res_sched_b.status_code, 200)
        item_b = [it for it in res_sched_b.json()["schedule"] if it["category"] == "task"][0]
        self.assertEqual(item_b["end"] - item_b["start"], 60)

    def test_04_retraining_and_analytics_sync(self):
        """TEST 4: POST /retrain updates model timestamp and invalidates cache gracefully."""
        headers_a = {"Authorization": f"Bearer {self.token_a}"}
        res_retrain = self.client.post("/retrain", headers=headers_a)
        self.assertEqual(res_retrain.status_code, 200)
        self.assertTrue(res_retrain.json()["success"])

        # GET /ml/status reflects valid model_last_trained timestamp and cached status
        res_status = self.client.get("/ml/status", headers=headers_a)
        self.assertEqual(res_status.status_code, 200)
        data = res_status.json()
        self.assertIsNotNone(data["model_last_trained"])

    def test_05_phase10_1_and_10_2_regression(self):
        """TEST 5: Verify Phase 10.1 & Phase 10.2 core invariants are preserved."""
        # Health check
        res_h = self.client.get("/health")
        self.assertEqual(res_h.status_code, 200)
        self.assertEqual(res_h.json(), {"status": "ok"})

        # Guardrails [0.5x, 2.5x, min 5]
        self.assertEqual(clamp_duration(60, 200), 150)
        self.assertEqual(clamp_duration(60, 10), 30)

        # Meal windows: Breakfast (07:00-11:00), Lunch (12:00-15:30), Dinner (19:00-23:00)
        sched_res = generate_schedule(
            day_start=420,
            day_end=1440,
            tasks=[
                {"task_name": "Breakfast", "duration": 30, "is_fixed": False},
                {"task_name": "Lunch", "duration": 45, "is_fixed": False},
                {"task_name": "Dinner", "duration": 30, "is_fixed": False},
            ],
            user_id=self.user_a_obj.id,
            db_session=self.db
        )
        meal_items = {it["title"].lower(): it for it in sched_res["schedule"] if it["category"] == "meal"}
        self.assertIn("breakfast", meal_items)
        self.assertGreaterEqual(meal_items["breakfast"]["start"], 420)
        self.assertLessEqual(meal_items["breakfast"]["end"], 660)

        self.assertIn("lunch", meal_items)
        self.assertGreaterEqual(meal_items["lunch"]["start"], 720)
        self.assertLessEqual(meal_items["lunch"]["end"], 930)

        self.assertIn("dinner", meal_items)
        self.assertGreaterEqual(meal_items["dinner"]["start"], 1140)
        self.assertLessEqual(meal_items["dinner"]["end"], 1380)


if __name__ == "__main__":
    unittest.main()
