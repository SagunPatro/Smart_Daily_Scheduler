import os
import unittest
import uuid
import tempfile

if not os.getenv("JWT_SECRET_KEY"):
    os.environ["JWT_SECRET_KEY"] = "test-secret-key-12345678901234567890"

from fastapi.testclient import TestClient
from main import app
from database import SessionLocal
from models import User
from ml_duration_model import MODEL_FILE, invalidate_model_cache
import logger


class Phase11ValidationTests(unittest.TestCase):
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

        cls.user_name = f"val_user_{uuid.uuid4().hex[:8]}"
        cls.user_email = f"{cls.user_name}@example.com"
        cls.password = "secret123"

        cls.client.post("/signup", json={"username": cls.user_name, "email": cls.user_email, "password": cls.password})
        res_login = cls.client.post("/login", json={"username": cls.user_name, "password": cls.password})
        cls.token = res_login.json()["access_token"]
        cls.headers = {"Authorization": f"Bearer {cls.token}"}
        cls.user_obj = cls.db.query(User).filter(User.username == cls.user_name).first()

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

    def test_01_task_input_validation(self):
        """TEST 1: Task creation/update rejects malformed, empty, or negative inputs."""
        # 1. Empty/whitespace task name -> 422
        res1 = self.client.post("/tasks", json={"task_name": "   ", "duration": 30, "is_fixed": False}, headers=self.headers)
        self.assertEqual(res1.status_code, 422)

        # 2. Duration <= 0 -> 422
        res2 = self.client.post("/tasks", json={"task_name": "Task Zero", "duration": 0, "is_fixed": False}, headers=self.headers)
        self.assertEqual(res2.status_code, 422)

        res3 = self.client.post("/tasks", json={"task_name": "Task Negative", "duration": -15, "is_fixed": False}, headers=self.headers)
        self.assertEqual(res3.status_code, 422)

        # 3. Fixed task missing start/end -> 422
        res4 = self.client.post("/tasks", json={"task_name": "Incomplete Fixed", "duration": 30, "is_fixed": True}, headers=self.headers)
        self.assertEqual(res4.status_code, 422)

        # 4. Fixed end <= fixed start -> 422
        res5 = self.client.post("/tasks", json={
            "task_name": "Inverted Fixed",
            "duration": 60,
            "is_fixed": True,
            "fixed_start": 600,
            "fixed_end": 540
        }, headers=self.headers)
        self.assertEqual(res5.status_code, 422)

        # 5. Out of range fixed start/end -> 422
        res6 = self.client.post("/tasks", json={
            "task_name": "Out of Bounds Fixed",
            "duration": 60,
            "is_fixed": True,
            "fixed_start": -10,
            "fixed_end": 600
        }, headers=self.headers)
        self.assertEqual(res6.status_code, 422)

    def test_02_schedule_input_validation(self):
        """TEST 2: Schedule generation rejects invalid day windows and overlapping fixed tasks."""
        # 1. day_end <= day_start -> 422
        res1 = self.client.post("/schedule", json={
            "day_start": 600,
            "day_end": 500,
            "tasks": [{"task_name": "Task 1", "duration": 30, "is_fixed": False}]
        }, headers=self.headers)
        self.assertEqual(res1.status_code, 422)

        # 2. Overlapping fixed tasks -> 400
        res2 = self.client.post("/schedule", json={
            "day_start": 480,
            "day_end": 1200,
            "tasks": [
                {"task_name": "Fixed A", "duration": 60, "is_fixed": True, "fixed_start": 600, "fixed_end": 660},
                {"task_name": "Fixed B", "duration": 60, "is_fixed": True, "fixed_start": 630, "fixed_end": 690}
            ]
        }, headers=self.headers)
        self.assertEqual(res2.status_code, 400)

    def test_03_feedback_input_validation(self):
        """TEST 3: Feedback endpoint rejects invalid durations, empty names, and non-boolean completion strings."""
        # 1. Empty task name -> 422
        res1 = self.client.post("/feedback", json={
            "task_name": "",
            "planned_start": "09:00",
            "planned_end": "10:00",
            "planned_duration": 60,
            "actual_duration": 60,
            "completed": "yes"
        }, headers=self.headers)
        self.assertEqual(res1.status_code, 422)

        # 2. Actual duration < 0 -> 422
        res2 = self.client.post("/feedback", json={
            "task_name": "Task Negative",
            "planned_start": "09:00",
            "planned_end": "10:00",
            "planned_duration": 60,
            "actual_duration": -10,
            "completed": "yes"
        }, headers=self.headers)
        self.assertEqual(res2.status_code, 422)

        # 3. Completed state not 'yes' or 'no' -> 422
        res3 = self.client.post("/feedback", json={
            "task_name": "Task Invalid Completed",
            "planned_start": "09:00",
            "planned_end": "10:00",
            "planned_duration": 60,
            "actual_duration": 60,
            "completed": "maybe"
        }, headers=self.headers)
        self.assertEqual(res3.status_code, 422)


if __name__ == "__main__":
    unittest.main()
