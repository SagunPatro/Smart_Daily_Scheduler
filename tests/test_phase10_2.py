import os
import unittest
import hashlib
import sqlite3
import uuid
import tempfile
import time

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


class Phase10_2MLTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # 1. Isolate CSV logging so tests never pollute production user_logs.csv
        cls.original_log_file = logger.LOG_FILE
        cls.test_csv = tempfile.NamedTemporaryFile(delete=False, suffix=".csv")
        cls.test_csv.close()
        logger.LOG_FILE = cls.test_csv.name

        # 2. Backup duration_model.pkl so retraining tests do not permanently mutate production artifact
        cls.original_model_bytes = None
        if os.path.exists(MODEL_FILE):
            with open(MODEL_FILE, "rb") as f:
                cls.original_model_bytes = f.read()

        cls.client = TestClient(app)
        cls.db = SessionLocal()

        # 3. Create User A and User B
        cls.user_a_name = f"ml_user_a_{uuid.uuid4().hex[:8]}"
        cls.user_a_email = f"{cls.user_a_name}@example.com"
        cls.user_b_name = f"ml_user_b_{uuid.uuid4().hex[:8]}"
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

    def test_01_cold_start(self):
        """TEST 1: Cold start for unseen tasks returns planned_duration."""
        unseen_task = f"completely_unknown_task_{uuid.uuid4().hex[:6]}"
        pred = predict_duration(
            task_name=unseen_task,
            planned_duration=45,
            start_minute=540,
            user_id=self.user_a_obj.id,
            db_session=self.db
        )
        self.assertEqual(pred, 45)

    def test_02_global_model_inference(self):
        """TEST 2: Known task with no user history uses global model prediction."""
        # 'study' is a known task in baseline seed data
        pred = predict_duration(
            task_name="study",
            planned_duration=60,
            start_minute=540,
            user_id=None,
            db_session=None
        )
        self.assertIsInstance(pred, int)
        self.assertGreaterEqual(pred, 5)
        # Should be bounded by guardrails [30, 150]
        self.assertGreaterEqual(pred, 30)
        self.assertLessEqual(pred, 150)

    def test_03_model_cache(self):
        """TEST 3: Verify model caching, thread-safety, and automatic mtime-based reload."""
        invalidate_model_cache()
        m1, le1 = load_cached_model()
        self.assertIsNotNone(m1)
        self.assertIsNotNone(le1)

        # Second call should return the exact cached object
        m2, le2 = load_cached_model()
        self.assertIs(m1, m2)
        self.assertIs(le1, le2)

        # Update mtime of duration_model.pkl to simulate external model artifact replacement
        current_time = time.time()
        new_mtime = current_time + 10
        os.utime(MODEL_FILE, (new_mtime, new_mtime))

        # load_cached_model() must detect mtime change and reload fresh model from disk
        m3, le3 = load_cached_model()
        self.assertIsNotNone(m3)
        self.assertIsNot(m1, m3)

    def test_04_duration_guardrails(self):
        """TEST 4: Verify duration guardrails clamp extreme outputs [0.5x, 2.5x, min 5]."""
        # planned = 60 -> min 30, max 150
        self.assertEqual(clamp_duration(60, 250), 150)  # capped at 2.5x
        self.assertEqual(clamp_duration(60, 10), 30)    # floored at 0.5x
        self.assertEqual(clamp_duration(60, 80), 80)    # within range

        # planned = 3 -> absolute min 5
        self.assertEqual(clamp_duration(3, 1), 5)
        self.assertEqual(clamp_duration(3, 3), 5)

    def test_05_task_normalization(self):
        """TEST 5: Verify task names normalize consistently."""
        self.assertEqual(normalize_task_name("Study"), "study")
        self.assertEqual(normalize_task_name("  study  "), "study")
        self.assertEqual(normalize_task_name("STUDY"), "study")
        self.assertEqual(normalize_task_name("  Physics Revision  "), "physics revision")

    def test_06_user_personalization(self):
        """TEST 6: User A with >= 3 completed records receives personalized duration."""
        personalized_task = f"pers_task_{uuid.uuid4().hex[:6]}"

        # Insert 3 completed records where planned=60 and actual=90 (ratio = 1.5)
        for i in range(3):
            rec = FeedbackLog(
                user_id=self.user_a_obj.id,
                task_name=personalized_task,
                planned_start=540 + i * 70,
                planned_end=600 + i * 70,
                planned_duration=60,
                actual_duration=90,
                completed="yes",
                feedback="Needed more time"
            )
            self.db.add(rec)
        self.db.commit()

        # Query personalized prediction for User A
        pred_a = predict_duration(
            task_name=personalized_task,
            planned_duration=60,
            start_minute=540,
            user_id=self.user_a_obj.id,
            db_session=self.db
        )
        # 60 * 1.5 = 90
        self.assertEqual(pred_a, 90)

    def test_07_user_isolation(self):
        """TEST 7: User B does NOT receive User A's personalized prediction."""
        task_iso = f"iso_task_{uuid.uuid4().hex[:6]}"

        # User A has 3 completed records with actual=100 on planned=50 (ratio = 2.0)
        for i in range(3):
            rec = FeedbackLog(
                user_id=self.user_a_obj.id,
                task_name=task_iso,
                planned_start=540 + i * 60,
                planned_end=590 + i * 60,
                planned_duration=50,
                actual_duration=100,
                completed="yes",
                feedback="User A slow"
            )
            self.db.add(rec)
        self.db.commit()

        # User A gets personalized prediction (50 * 2.0 = 100)
        pred_a = predict_duration(
            task_name=task_iso,
            planned_duration=50,
            start_minute=540,
            user_id=self.user_a_obj.id,
            db_session=self.db
        )
        self.assertEqual(pred_a, 100)

        # User B has no records for this task -> cold start fallback to planned=50
        pred_b = predict_duration(
            task_name=task_iso,
            planned_duration=50,
            start_minute=540,
            user_id=self.user_b_obj.id,
            db_session=self.db
        )
        self.assertEqual(pred_b, 50)

    def test_08_retraining_endpoint(self):
        """TEST 8: Authenticated POST /retrain succeeds, updates model file and cache."""
        headers = {"Authorization": f"Bearer {self.token_a}"}
        res = self.client.post("/retrain", headers=headers)
        self.assertEqual(res.status_code, 200)
        data = res.json()
        self.assertTrue(data["success"])
        self.assertGreaterEqual(data["sample_count"], 5)
        self.assertIn("retrained successfully", data["message"].lower())

        # Unauthenticated /retrain fails with 401
        res_unauth = self.client.post("/retrain")
        self.assertEqual(res_unauth.status_code, 401)

    def test_09_fixed_task_invariant(self):
        """TEST 9: Fixed tasks remain strictly pinned to fixed_start and fixed_end."""
        schedule_res = generate_schedule(
            day_start=480,
            day_end=1200,
            tasks=[
                {
                    "task_name": "Board Meeting",
                    "duration": 60,
                    "is_fixed": True,
                    "fixed_start": 600,
                    "fixed_end": 660
                }
            ],
            user_id=self.user_a_obj.id,
            db_session=self.db
        )
        items = schedule_res["schedule"]
        fixed_items = [it for it in items if it["category"] == "fixed_task"]
        self.assertEqual(len(fixed_items), 1)
        self.assertEqual(fixed_items[0]["start"], 600)
        self.assertEqual(fixed_items[0]["end"], 660)

    def test_10_meal_invariant(self):
        """TEST 10: Meals remain scheduled within their designated meal windows (Breakfast, Lunch, Dinner)."""
        schedule_res = generate_schedule(
            day_start=420,  # 07:00
            day_end=1440,   # 24:00
            tasks=[
                {"task_name": "Breakfast", "duration": 20, "is_fixed": False},
                {"task_name": "Lunch", "duration": 45, "is_fixed": False},
                {"task_name": "Dinner", "duration": 30, "is_fixed": False},
            ],
            user_id=self.user_a_obj.id,
            db_session=self.db
        )
        items = schedule_res["schedule"]
        meal_items = {it["title"].lower(): it for it in items if it["category"] == "meal"}

        # Breakfast window: 07:00 (420) - 11:00 (660)
        self.assertIn("breakfast", meal_items)
        self.assertGreaterEqual(meal_items["breakfast"]["start"], 420)
        self.assertLessEqual(meal_items["breakfast"]["end"], 660)

        # Lunch window: 12:00 (720) - 15:30 (930)
        self.assertIn("lunch", meal_items)
        self.assertGreaterEqual(meal_items["lunch"]["start"], 720)
        self.assertLessEqual(meal_items["lunch"]["end"], 930)

        # Dinner window: 19:00 (1140) - 23:00 (1380)
        self.assertIn("dinner", meal_items)
        self.assertGreaterEqual(meal_items["dinner"]["start"], 1140)
        self.assertLessEqual(meal_items["dinner"]["end"], 1380)

    def test_11_schedule_personalization(self):
        """TEST 11: POST /schedule applies user personalization to flexible tasks."""
        task_sched = f"sched_personal_{uuid.uuid4().hex[:6]}"

        # Insert 3 completed records for User A with actual=75 on planned=50 (ratio = 1.5)
        for i in range(3):
            rec = FeedbackLog(
                user_id=self.user_a_obj.id,
                task_name=task_sched,
                planned_start=540,
                planned_end=590,
                planned_duration=50,
                actual_duration=75,
                completed="yes",
                feedback="took longer"
            )
            self.db.add(rec)
        self.db.commit()

        # User A calls POST /schedule
        headers_a = {"Authorization": f"Bearer {self.token_a}"}
        res_a = self.client.post("/schedule", json={
            "day_start": 540,
            "day_end": 1200,
            "title": "Personalized Test",
            "save_to_history": False,
            "tasks": [{"task_name": task_sched, "duration": 50, "is_fixed": False}]
        }, headers=headers_a)
        self.assertEqual(res_a.status_code, 200)
        items_a = res_a.json()["schedule"]
        task_a_items = [it for it in items_a if it["category"] == "task"]
        self.assertEqual(len(task_a_items), 1)
        # Personalized duration: 50 * 1.5 = 75 minutes
        duration_a = task_a_items[0]["end"] - task_a_items[0]["start"]
        self.assertEqual(duration_a, 75)

        # User B calls POST /schedule for the same task
        headers_b = {"Authorization": f"Bearer {self.token_b}"}
        res_b = self.client.post("/schedule", json={
            "day_start": 540,
            "day_end": 1200,
            "title": "Baseline Test",
            "save_to_history": False,
            "tasks": [{"task_name": task_sched, "duration": 50, "is_fixed": False}]
        }, headers=headers_b)
        self.assertEqual(res_b.status_code, 200)
        items_b = res_b.json()["schedule"]
        task_b_items = [it for it in items_b if it["category"] == "task"]
        self.assertEqual(len(task_b_items), 1)
        # User B has no records -> cold start fallback = planned duration (50 min)
        duration_b = task_b_items[0]["end"] - task_b_items[0]["start"]
        self.assertEqual(duration_b, 50)

    def test_12_backend_regression(self):
        """TEST 12: General regression test for Tasks, History, and Health."""
        # 1. /health
        res_h = self.client.get("/health")
        self.assertEqual(res_h.status_code, 200)
        self.assertEqual(res_h.json(), {"status": "ok"})

        headers = {"Authorization": f"Bearer {self.token_a}"}

        # 2. Task CRUD
        res_create = self.client.post("/tasks", json={
            "task_name": "Regression Task",
            "duration": 45,
            "is_fixed": False
        }, headers=headers)
        self.assertEqual(res_create.status_code, 201)
        task_id = res_create.json()["id"]

        res_get = self.client.get("/tasks", headers=headers)
        self.assertEqual(res_get.status_code, 200)
        self.assertTrue(any(t["id"] == task_id for t in res_get.json()))

        res_del = self.client.delete(f"/tasks/{task_id}", headers=headers)
        self.assertEqual(res_del.status_code, 200)

        # 3. Fixed task overlap error (400)
        res_overlap = self.client.post("/schedule", json={
            "day_start": 540,
            "day_end": 1200,
            "tasks": [
                {"task_name": "Fixed 1", "duration": 60, "is_fixed": True, "fixed_start": 600, "fixed_end": 660},
                {"task_name": "Fixed 2", "duration": 60, "is_fixed": True, "fixed_start": 630, "fixed_end": 690}
            ]
        }, headers=headers)
        self.assertEqual(res_overlap.status_code, 400)

    def test_13_feedback_api_integration(self):
        """TEST 13: Authenticated POST /feedback creates FeedbackLog with user_id derived from JWT and prevents spoofing."""
        headers_a = {"Authorization": f"Bearer {self.token_a}"}
        payload = {
            "task_name": "API Feedback Integration Task",
            "planned_start": "09:00",
            "planned_end": "10:00",
            "planned_duration": 60,
            "actual_duration": 75,
            "completed": "yes",
            "feedback": "API integration feedback text",
            "user_id": 99999  # Attempting to spoof user_id
        }

        res = self.client.post("/feedback", json=payload, headers=headers_a)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json(), {"message": "Feedback saved ✅"})

        # Verify record in database
        record = self.db.query(FeedbackLog).filter(
            FeedbackLog.task_name == "API Feedback Integration Task"
        ).first()

        self.assertIsNotNone(record)
        # Verify user_id is derived strictly from User A's JWT token, NOT spoofed 99999
        self.assertEqual(record.user_id, self.user_a_obj.id)
        self.assertNotEqual(record.user_id, 99999)

        # Verify all fields
        self.assertEqual(record.planned_start, 540)  # 09:00 -> 540
        self.assertEqual(record.planned_end, 600)    # 10:00 -> 600
        self.assertEqual(record.planned_duration, 60)
        self.assertEqual(record.actual_duration, 75)
        self.assertEqual(record.completed, "yes")
        self.assertEqual(record.feedback, "API integration feedback text")


if __name__ == "__main__":
    unittest.main()
