import os
import unittest
import hashlib
import sqlite3
import uuid

# Ensure JWT secret is set before importing app
if not os.getenv("JWT_SECRET_KEY"):
    os.environ["JWT_SECRET_KEY"] = "test-secret-key-12345678901234567890"

from fastapi.testclient import TestClient
from main import app
from database import SessionLocal
from models import User, FeedbackLog, Task, SavedSchedule


class Phase10_1LearningDataTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import logger
        import tempfile
        cls.original_log_file = logger.LOG_FILE
        cls.test_csv = tempfile.NamedTemporaryFile(delete=False, suffix=".csv")
        cls.test_csv.close()
        logger.LOG_FILE = cls.test_csv.name

        cls.client = TestClient(app)
        cls.db = SessionLocal()

        # Generate unique test users
        cls.user_a_name = f"user_a_{uuid.uuid4().hex[:8]}"
        cls.user_a_email = f"{cls.user_a_name}@example.com"
        cls.user_b_name = f"user_b_{uuid.uuid4().hex[:8]}"
        cls.user_b_email = f"{cls.user_b_name}@example.com"
        cls.password = "secret123"

        # Register User A
        res_a = cls.client.post("/signup", json={
            "username": cls.user_a_name,
            "email": cls.user_a_email,
            "password": cls.password
        })
        assert res_a.status_code == 200, f"User A signup failed: {res_a.text}"

        # Login User A
        res_login_a = cls.client.post("/login", json={
            "username": cls.user_a_name,
            "password": cls.password
        })
        assert res_login_a.status_code == 200, f"User A login failed: {res_login_a.text}"
        cls.token_a = res_login_a.json()["access_token"]
        cls.user_a_obj = cls.db.query(User).filter(User.username == cls.user_a_name).first()
        assert cls.user_a_obj is not None

        # Register User B
        res_b = cls.client.post("/signup", json={
            "username": cls.user_b_name,
            "email": cls.user_b_email,
            "password": cls.password
        })
        assert res_b.status_code == 200, f"User B signup failed: {res_b.text}"

        # Login User B
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
        import logger
        logger.LOG_FILE = cls.original_log_file
        if os.path.exists(cls.test_csv.name):
            try:
                os.unlink(cls.test_csv.name)
            except Exception:
                pass

        # Clean up test users and their cascaded logs
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

    def test_01_authenticated_feedback(self):
        """TEST 1: User logs in. POST /feedback succeeds. A learning record is created.
        Also verifies time conversion: HH:MM string ('09:30') is converted to 570 minutes from midnight."""
        headers = {"Authorization": f"Bearer {self.token_a}"}
        # Test with HH:MM strings to verify time conversion: "09:30" -> 570, "10:30" -> 630
        payload = {
            "task_name": "Math Practice",
            "planned_start": "09:30",
            "planned_end": "10:30",
            "planned_duration": 60,
            "actual_duration": 65,
            "completed": "yes",
            "feedback": "Felt focused"
        }
        res = self.client.post("/feedback", json=payload, headers=headers)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json(), {"message": "Feedback saved ✅"})

        # Verify learning record was created in database with integer minutes
        record = self.db.query(FeedbackLog).filter(
            FeedbackLog.user_id == self.user_a_obj.id,
            FeedbackLog.task_name == "Math Practice"
        ).first()
        self.assertIsNotNone(record)
        self.assertEqual(record.planned_start, 570)  # 09:30 -> 570
        self.assertEqual(record.planned_end, 630)    # 10:30 -> 630
        self.assertEqual(record.planned_duration, 60)
        self.assertEqual(record.actual_duration, 65)
        self.assertEqual(record.completed, "yes")
        self.assertEqual(record.feedback, "Felt focused")

    def test_02_user_attribution(self):
        """TEST 2: Verify record.user_id == authenticated_user.id."""
        headers = {"Authorization": f"Bearer {self.token_a}"}
        payload = {
            "task_name": "Attribution Check Task",
            "planned_start": 600,
            "planned_end": 660,
            "planned_duration": 60,
            "actual_duration": 50,
            "completed": "yes",
            "feedback": "Attribution verified"
        }
        res = self.client.post("/feedback", json=payload, headers=headers)
        self.assertEqual(res.status_code, 200)

        record = self.db.query(FeedbackLog).filter(
            FeedbackLog.task_name == "Attribution Check Task"
        ).first()
        self.assertIsNotNone(record)
        self.assertEqual(record.user_id, self.user_a_obj.id)
        self.assertEqual(record.user.id, self.user_a_obj.id)
        self.assertEqual(record.user.username, self.user_a_name)

    def test_03_user_isolation(self):
        """TEST 3: Verify User B does not receive/own User A's record, and vice versa."""
        # Submit User A feedback
        headers_a = {"Authorization": f"Bearer {self.token_a}"}
        payload_a = {
            "task_name": "User A Unique Task",
            "planned_start": 700,
            "planned_end": 760,
            "planned_duration": 60,
            "actual_duration": 70,
            "completed": "yes",
            "feedback": "Only User A"
        }
        res_a = self.client.post("/feedback", json=payload_a, headers=headers_a)
        self.assertEqual(res_a.status_code, 200)

        # Submit User B feedback
        headers_b = {"Authorization": f"Bearer {self.token_b}"}
        payload_b = {
            "task_name": "User B Unique Task",
            "planned_start": 800,
            "planned_end": 860,
            "planned_duration": 60,
            "actual_duration": 55,
            "completed": "yes",
            "feedback": "Only User B"
        }
        res_b = self.client.post("/feedback", json=payload_b, headers=headers_b)
        self.assertEqual(res_b.status_code, 200)

        # Query User A records
        user_a_records = self.db.query(FeedbackLog).filter(FeedbackLog.user_id == self.user_a_obj.id).all()
        user_a_tasks = [r.task_name for r in user_a_records]
        self.assertIn("User A Unique Task", user_a_tasks)
        self.assertNotIn("User B Unique Task", user_a_tasks)

        # Query User B records
        user_b_records = self.db.query(FeedbackLog).filter(FeedbackLog.user_id == self.user_b_obj.id).all()
        user_b_tasks = [r.task_name for r in user_b_records]
        self.assertIn("User B Unique Task", user_b_tasks)
        self.assertNotIn("User A Unique Task", user_b_tasks)

    def test_04_client_cannot_choose_another_user(self):
        """TEST 4: Client payload attempts to manipulate ownership; server must ignore/reject spoofing."""
        # Unauthenticated request rejected with 401
        payload = {
            "task_name": "Unauthenticated Task",
            "planned_start": 540,
            "planned_end": 600,
            "planned_duration": 60,
            "actual_duration": 60,
            "completed": "yes",
            "feedback": "test"
        }
        res_unauth = self.client.post("/feedback", json=payload)
        self.assertEqual(res_unauth.status_code, 401)

        # User A attempts to pass user_id = User B's id in payload
        headers_a = {"Authorization": f"Bearer {self.token_a}"}
        spoofed_payload = {
            "user_id": self.user_b_obj.id,
            "task_name": "Spoof Attempt Task",
            "planned_start": 900,
            "planned_end": 960,
            "planned_duration": 60,
            "actual_duration": 60,
            "completed": "yes",
            "feedback": "Spoofed attempt"
        }
        res_spoof = self.client.post("/feedback", json=spoofed_payload, headers=headers_a)
        self.assertEqual(res_spoof.status_code, 200)

        # Record must be owned strictly by User A (from JWT sub), NOT User B
        record = self.db.query(FeedbackLog).filter(FeedbackLog.task_name == "Spoof Attempt Task").first()
        self.assertIsNotNone(record)
        self.assertEqual(record.user_id, self.user_a_obj.id)
        self.assertNotEqual(record.user_id, self.user_b_obj.id)

    def test_05_validation(self):
        """TEST 5: Verify invalid task name, duration, completion values are rejected."""
        headers = {"Authorization": f"Bearer {self.token_a}"}

        # Blank task name
        res = self.client.post("/feedback", json={
            "task_name": "   ",
            "planned_start": 540,
            "planned_end": 600,
            "planned_duration": 60,
            "actual_duration": 60,
            "completed": "yes"
        }, headers=headers)
        self.assertEqual(res.status_code, 422)

        # Planned duration <= 0
        res = self.client.post("/feedback", json={
            "task_name": "Invalid Duration",
            "planned_start": 540,
            "planned_end": 600,
            "planned_duration": 0,
            "actual_duration": 60,
            "completed": "yes"
        }, headers=headers)
        self.assertEqual(res.status_code, 422)

        # Negative actual duration
        res = self.client.post("/feedback", json={
            "task_name": "Invalid Actual",
            "planned_start": 540,
            "planned_end": 600,
            "planned_duration": 60,
            "actual_duration": -10,
            "completed": "yes"
        }, headers=headers)
        self.assertEqual(res.status_code, 422)

        # Invalid completed string
        res = self.client.post("/feedback", json={
            "task_name": "Invalid Completed",
            "planned_start": 540,
            "planned_end": 600,
            "planned_duration": 60,
            "actual_duration": 60,
            "completed": "maybe"
        }, headers=headers)
        self.assertEqual(res.status_code, 422)

        # Invalid time range (> 1439)
        res = self.client.post("/feedback", json={
            "task_name": "Invalid Time",
            "planned_start": 540,
            "planned_end": 1500,
            "planned_duration": 60,
            "actual_duration": 60,
            "completed": "yes"
        }, headers=headers)
        self.assertEqual(res.status_code, 422)

        # planned_end <= planned_start
        res = self.client.post("/feedback", json={
            "task_name": "Inverted Time",
            "planned_start": 600,
            "planned_end": 500,
            "planned_duration": 60,
            "actual_duration": 60,
            "completed": "yes"
        }, headers=headers)
        self.assertEqual(res.status_code, 422)

    def test_06_existing_records_preserved(self):
        """TEST 6: Verify pre-existing users, tasks, and saved_schedules remain intact."""
        con = sqlite3.connect("smart_scheduler.db")
        cur = con.cursor()
        users_count = cur.execute("SELECT count(*) FROM users").fetchone()[0]
        tasks_count = cur.execute("SELECT count(*) FROM tasks").fetchone()[0]
        schedules_count = cur.execute("SELECT count(*) FROM saved_schedules").fetchone()[0]
        con.close()

        # 6 pre-existing users + 2 test users created in setUpClass = at least 8
        self.assertGreaterEqual(users_count, 6)
        # Pre-existing tasks count was 4
        self.assertGreaterEqual(tasks_count, 4)
        # Pre-existing saved schedules count was 21
        self.assertGreaterEqual(schedules_count, 21)

    def test_07_existing_authentication_regression(self):
        """TEST 7: Verify Phase 6 JWT authentication flow still works."""
        # Login with an existing user (user_id 6: Sagun Patro) or our test user
        res = self.client.post("/login", json={
            "username": self.user_a_name,
            "password": self.password
        })
        self.assertEqual(res.status_code, 200)
        data = res.json()
        self.assertIn("access_token", data)
        self.assertEqual(data["token_type"], "bearer")
        self.assertEqual(data["username"], self.user_a_name)

    def test_08_existing_schedule_regression(self):
        """TEST 8: Verify normal schedule generation still works."""
        headers = {"Authorization": f"Bearer {self.token_a}"}
        payload = {
            "day_start": 540,
            "day_end": 1080,
            "title": "Phase 10 Test Schedule",
            "save_to_history": False,
            "tasks": [
                {
                    "task_name": "Reading Docs",
                    "duration": 60,
                    "is_fixed": False
                }
            ]
        }
        res = self.client.post("/schedule", json=payload, headers=headers)
        self.assertEqual(res.status_code, 200)
        data = res.json()
        self.assertIn("schedule", data)
        self.assertGreater(len(data["schedule"]), 0)

    def test_09_fixed_task_overlap_regression(self):
        """TEST 9: Verify fixed-task overlap behavior returns 400 Bad Request."""
        headers = {"Authorization": f"Bearer {self.token_a}"}
        payload = {
            "day_start": 480,
            "day_end": 1200,
            "title": "Conflicting Schedule",
            "save_to_history": False,
            "tasks": [
                {
                    "task_name": "Meeting 1",
                    "duration": 60,
                    "is_fixed": True,
                    "fixed_start": 600,
                    "fixed_end": 660
                },
                {
                    "task_name": "Meeting 2",
                    "duration": 60,
                    "is_fixed": True,
                    "fixed_start": 630,
                    "fixed_end": 690
                }
            ]
        }
        res = self.client.post("/schedule", json=payload, headers=headers)
        self.assertEqual(res.status_code, 400)
        self.assertIn("overlap", res.json()["detail"].lower())

    def test_10_ml_artifact_integrity(self):
        """TEST 10: Verify duration_model.pkl exists and is a valid model artifact."""
        import joblib
        self.assertTrue(os.path.exists("duration_model.pkl"))
        model, le = joblib.load("duration_model.pkl")
        self.assertIsNotNone(model)
        self.assertIsNotNone(le)
        self.assertTrue(hasattr(model, "predict"))
        self.assertTrue(hasattr(le, "classes_"))



if __name__ == "__main__":
    unittest.main()
