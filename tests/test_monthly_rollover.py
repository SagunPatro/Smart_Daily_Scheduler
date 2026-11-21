import os
import unittest
import uuid
import jwt
from datetime import datetime, timedelta, timezone

if not os.getenv("JWT_SECRET_KEY"):
    os.environ["JWT_SECRET_KEY"] = "smart-scheduler-secure-jwt-secret-key-2026-phase10"

from fastapi.testclient import TestClient
from main import app
from database import SessionLocal
from models import User, SavedSchedule
from auth import ACCESS_TOKEN_EXPIRE_MINUTES, create_access_token


class MonthlyRolloverTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(app)
        cls.db = SessionLocal()

        cls.username = f"rollover_user_{uuid.uuid4().hex[:8]}"
        cls.email = f"{cls.username}@example.com"
        cls.password = "password123"

        cls.client.post("/signup", json={"username": cls.username, "email": cls.email, "password": cls.password})
        res = cls.client.post("/login", json={"username": cls.username, "password": cls.password})
        cls.token = res.json()["access_token"]
        cls.user = cls.db.query(User).filter(User.username == cls.username).first()

    @classmethod
    def tearDownClass(cls):
        cls.db.close()

    def test_01_session_duration_is_5_days(self):
        """Verify that JWT session span is 5 days (7200 minutes)."""
        self.assertEqual(ACCESS_TOKEN_EXPIRE_MINUTES, 7200)

        # Generate a token and decode expiration
        token = create_access_token({"sub": self.username, "user_id": self.user.id})
        decoded = jwt.decode(token, os.getenv("JWT_SECRET_KEY"), algorithms=["HS256"])
        exp_timestamp = decoded["exp"]

        exp_dt = datetime.fromtimestamp(exp_timestamp, tz=timezone.utc)
        now_dt = datetime.now(timezone.utc)
        diff = exp_dt - now_dt

        # Should be approximately 5 days (between 4.9 and 5.1 days)
        self.assertAlmostEqual(diff.total_seconds() / 86400, 5.0, delta=0.1)

    def test_02_monthly_rollover_clears_prior_months(self):
        """Verify that schedules from prior months are automatically purged on GET /schedules."""
        headers = {"Authorization": f"Bearer {self.token}"}

        # Insert a simulated schedule from a previous month (e.g. 60 days ago)
        old_date = datetime.utcnow() - timedelta(days=60)
        old_schedule = SavedSchedule(
            user_id=self.user.id,
            title="Old Past Month Schedule",
            day_start=540,
            day_end=1380,
            schedule_json='{"schedule": [], "warnings": []}',
            created_at=old_date
        )
        self.db.add(old_schedule)

        # Insert a current schedule (created today)
        current_schedule = SavedSchedule(
            user_id=self.user.id,
            title="Current Month Schedule",
            day_start=540,
            day_end=1380,
            schedule_json='{"schedule": [], "warnings": []}',
            created_at=datetime.utcnow()
        )
        self.db.add(current_schedule)
        self.db.commit()

        old_id = old_schedule.id
        current_id = current_schedule.id

        # GET /schedules should trigger auto-clearing of prior months
        res = self.client.get("/schedules", headers=headers)
        self.assertEqual(res.status_code, 200)
        returned_ids = [s["id"] for s in res.json()]

        self.assertNotIn(old_id, returned_ids, "Old month schedule should have been cleared")
        self.assertIn(current_id, returned_ids, "Current month schedule should be present")

        # Verify old record is truly deleted from DB
        in_db = self.db.query(SavedSchedule).filter(SavedSchedule.id == old_id).first()
        self.assertIsNone(in_db, "Old schedule record should be removed from database")

    def test_03_bulk_delete_clears_all_user_schedules(self):
        """Verify that DELETE /schedules removes all saved schedules for the authenticated user."""
        headers = {"Authorization": f"Bearer {self.token}"}

        # Add two schedules for the user
        sched1 = SavedSchedule(
            user_id=self.user.id,
            title="Schedule To Delete 1",
            day_start=540,
            day_end=1380,
            schedule_json='{"schedule": [], "warnings": []}'
        )
        sched2 = SavedSchedule(
            user_id=self.user.id,
            title="Schedule To Delete 2",
            day_start=540,
            day_end=1380,
            schedule_json='{"schedule": [], "warnings": []}'
        )
        self.db.add_all([sched1, sched2])
        self.db.commit()

        # DELETE /schedules
        res = self.client.delete("/schedules", headers=headers)
        self.assertEqual(res.status_code, 200)

        # GET /schedules should now be empty
        res_list = self.client.get("/schedules", headers=headers)
        self.assertEqual(res_list.status_code, 200)
        self.assertEqual(len(res_list.json()), 0)


if __name__ == "__main__":
    unittest.main()
