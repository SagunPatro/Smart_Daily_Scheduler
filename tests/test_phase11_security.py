import os
import unittest
import uuid
import tempfile
import jwt
from datetime import datetime, timedelta, timezone

if not os.getenv("JWT_SECRET_KEY"):
    os.environ["JWT_SECRET_KEY"] = "test-secret-key-12345678901234567890"

from fastapi.testclient import TestClient
from main import app
from database import SessionLocal
from models import User, Task, SavedSchedule, FeedbackLog
from ml_duration_model import MODEL_FILE, invalidate_model_cache
import logger


class Phase11SecurityTests(unittest.TestCase):
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

        cls.client = TestClient(app)
        cls.db = SessionLocal()

        cls.user_a_name = f"sec_user_a_{uuid.uuid4().hex[:8]}"
        cls.user_a_email = f"{cls.user_a_name}@example.com"
        cls.user_b_name = f"sec_user_b_{uuid.uuid4().hex[:8]}"
        cls.user_b_email = f"{cls.user_b_name}@example.com"
        cls.password = "secret123"

        # Register User A
        cls.client.post("/signup", json={"username": cls.user_a_name, "email": cls.user_a_email, "password": cls.password})
        res_a = cls.client.post("/login", json={"username": cls.user_a_name, "password": cls.password})
        cls.token_a = res_a.json()["access_token"]
        cls.user_a_obj = cls.db.query(User).filter(User.username == cls.user_a_name).first()

        # Register User B
        cls.client.post("/signup", json={"username": cls.user_b_name, "email": cls.user_b_email, "password": cls.password})
        res_b = cls.client.post("/login", json={"username": cls.user_b_name, "password": cls.password})
        cls.token_b = res_b.json()["access_token"]
        cls.user_b_obj = cls.db.query(User).filter(User.username == cls.user_b_name).first()

    @classmethod
    def tearDownClass(cls):
        logger.LOG_FILE = cls.original_log_file
        if os.path.exists(cls.test_csv.name):
            try:
                os.unlink(cls.test_csv.name)
            except Exception:
                pass

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

        if cls.original_model_bytes is not None:
            with open(MODEL_FILE, "wb") as f:
                f.write(cls.original_model_bytes)
            invalidate_model_cache()

    def test_01_authentication_hardening(self):
        """TEST 1: Protected endpoints return 401 on missing, invalid, or expired JWTs."""
        protected_endpoints = [
            ("GET", "/tasks"),
            ("POST", "/tasks"),
            ("POST", "/schedule"),
            ("GET", "/schedules"),
            ("POST", "/feedback"),
            ("POST", "/retrain"),
            ("GET", "/ml/status")
        ]

        # 1. Missing Authorization header
        for method, endpoint in protected_endpoints:
            if method == "GET":
                res = self.client.get(endpoint)
            else:
                res = self.client.post(endpoint, json={})
            self.assertEqual(res.status_code, 401, f"Failed missing auth check for {method} {endpoint}")

        # 2. Invalid / malformed Bearer token
        bad_headers = {"Authorization": "Bearer invalid.jwt.token"}
        for method, endpoint in protected_endpoints:
            if method == "GET":
                res = self.client.get(endpoint, headers=bad_headers)
            else:
                res = self.client.post(endpoint, json={}, headers=bad_headers)
            self.assertEqual(res.status_code, 401, f"Failed invalid token check for {method} {endpoint}")

        # 3. Wrong signing secret
        secret_key = os.getenv("JWT_SECRET_KEY")
        payload = {"sub": str(self.user_a_obj.id), "exp": datetime.now(timezone.utc) + timedelta(minutes=60)}
        wrong_token = jwt.encode(payload, "wrong-secret-key-12345", algorithm="HS256")
        wrong_headers = {"Authorization": f"Bearer {wrong_token}"}
        for method, endpoint in protected_endpoints:
            if method == "GET":
                res = self.client.get(endpoint, headers=wrong_headers)
            else:
                res = self.client.post(endpoint, json={}, headers=wrong_headers)
            self.assertEqual(res.status_code, 401, f"Failed wrong secret check for {method} {endpoint}")

    def test_02_task_authorization_isolation(self):
        """TEST 2: User A cannot read, update, or delete User B's task."""
        headers_b = {"Authorization": f"Bearer {self.token_b}"}
        headers_a = {"Authorization": f"Bearer {self.token_a}"}

        # User B creates a task
        res_create = self.client.post("/tasks", json={
            "task_name": "User B Secret Task",
            "duration": 45,
            "is_fixed": False
        }, headers=headers_b)
        self.assertEqual(res_create.status_code, 201)
        task_b_id = res_create.json()["id"]

        # User A attempts to update User B's task -> 404
        res_update = self.client.put(f"/tasks/{task_b_id}", json={"task_name": "Hacked Name"}, headers=headers_a)
        self.assertEqual(res_update.status_code, 404)

        # User A attempts to delete User B's task -> 404
        res_delete = self.client.delete(f"/tasks/{task_b_id}", headers=headers_a)
        self.assertEqual(res_delete.status_code, 404)

        # User A lists tasks -> User B's task is NOT included
        res_list_a = self.client.get("/tasks", headers=headers_a)
        self.assertEqual(res_list_a.status_code, 200)
        task_ids_a = [t["id"] for t in res_list_a.json()]
        self.assertNotIn(task_b_id, task_ids_a)

    def test_03_schedule_authorization_isolation(self):
        """TEST 3: User A cannot read or delete User B's saved schedule."""
        headers_b = {"Authorization": f"Bearer {self.token_b}"}
        headers_a = {"Authorization": f"Bearer {self.token_a}"}

        # User B creates and saves a schedule
        res_sched = self.client.post("/schedule", json={
            "day_start": 540,
            "day_end": 1200,
            "title": "User B Private Schedule",
            "save_to_history": True,
            "tasks": [{"task_name": "Task 1", "duration": 30, "is_fixed": False}]
        }, headers=headers_b)
        self.assertEqual(res_sched.status_code, 200)
        sched_b_id = res_sched.json()["schedule_id"]
        self.assertIsNotNone(sched_b_id)

        # User A attempts to get User B's schedule by ID -> 404
        res_get = self.client.get(f"/schedules/{sched_b_id}", headers=headers_a)
        self.assertEqual(res_get.status_code, 404)

        # User A attempts to delete User B's schedule by ID -> 404
        res_del = self.client.delete(f"/schedules/{sched_b_id}", headers=headers_a)
        self.assertEqual(res_del.status_code, 404)

        # User A lists schedules -> User B's schedule is NOT included
        res_list_a = self.client.get("/schedules", headers=headers_a)
        self.assertEqual(res_list_a.status_code, 200)
        sched_ids_a = [s["id"] for s in res_list_a.json()]
        self.assertNotIn(sched_b_id, sched_ids_a)

    def test_04_feedback_ownership_isolation(self):
        """TEST 4: Feedback submissions strictly derive user ownership from JWT, preventing user_id spoofing."""
        headers_a = {"Authorization": f"Bearer {self.token_a}"}
        res_fb = self.client.post("/feedback", json={
            "task_name": "Security Isolation Task",
            "planned_start": "09:00",
            "planned_end": "10:00",
            "planned_duration": 60,
            "actual_duration": 75,
            "completed": "yes",
            "feedback": "No spoofing",
            "user_id": 999999
        }, headers=headers_a)
        self.assertEqual(res_fb.status_code, 200)

        # Verify DB entry is owned by User A, not 999999
        record = self.db.query(FeedbackLog).filter(FeedbackLog.task_name == "Security Isolation Task").first()
        self.assertIsNotNone(record)
        self.assertEqual(record.user_id, self.user_a_obj.id)
        self.assertNotEqual(record.user_id, 999999)


if __name__ == "__main__":
    unittest.main()
