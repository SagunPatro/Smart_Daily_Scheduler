import unittest
import os
import tempfile
from datetime import datetime
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from database import Base
from models import User, Task, FeedbackLog, UserPreference
from preference_learning import (
    extract_preference_from_text,
    activities_match,
    normalize_activity_name
)
from scheduler import generate_schedule


class TestPreferenceLearning(unittest.TestCase):

    def setUp(self):
        # Create an isolated in-memory SQLite database for testing
        self.engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
        Base.metadata.create_all(bind=self.engine)
        self.Session = sessionmaker(bind=self.engine)
        self.db = self.Session()

        # Seed two distinct users to verify cross-user isolation
        self.user1 = User(username="user1", email="user1@example.com", hashed_password="pw1")
        self.user2 = User(username="user2", email="user2@example.com", hashed_password="pw2")
        self.db.add_all([self.user1, self.user2])
        self.db.commit()
        self.db.refresh(self.user1)
        self.db.refresh(self.user2)

    def tearDown(self):
        self.db.close()
        Base.metadata.drop_all(bind=self.engine)

    # -------------------------------------------------------------------------
    # 1. Extraction of Supported Preference Phrases
    # -------------------------------------------------------------------------
    def test_extract_replace_break_with_task(self):
        phrases = [
            "I prefer breakfast rather than a short break after bathing.",
            "I would prefer breakfast rather than a short break after the bathing task.",
            "prefer breakfast instead of a short break after bathing",
            "prefer breakfast over a break after bathing",
            "I prefer lunch rather than a short break after studying."
        ]
        for p in phrases:
            extracted = extract_preference_from_text(p)
            self.assertIsNotNone(extracted, f"Failed to extract from: {p}")
            self.assertEqual(extracted.preference_type, "REPLACE_BREAK_WITH_TASK")
            self.assertEqual(extracted.relationship, "instead_of_break_after")
            self.assertTrue(activities_match(extracted.target_activity, "bathing") or activities_match(extracted.target_activity, "studying"))

    def test_extract_order_after(self):
        phrases = [
            "Schedule lunch after studying.",
            "schedule lunch after study",
            "Place dinner after meeting.",
            "I prefer lunch after studying.",
            "Please schedule breakfast after walk."
        ]
        for p in phrases:
            extracted = extract_preference_from_text(p)
            self.assertIsNotNone(extracted, f"Failed to extract from: {p}")
            self.assertEqual(extracted.preference_type, "ORDER_AFTER")
            self.assertEqual(extracted.relationship, "after")

    def test_extract_prefer_break_after(self):
        phrases = [
            "I prefer a short break after coding.",
            "need a break after coding",
            "Take a short break after workout.",
            "add a break after study"
        ]
        for p in phrases:
            extracted = extract_preference_from_text(p)
            self.assertIsNotNone(extracted, f"Failed to extract from: {p}")
            self.assertEqual(extracted.preference_type, "PREFER_BREAK_AFTER")
            self.assertEqual(extracted.preferred_activity, "short_break")

    def test_extract_avoid_break_after(self):
        phrases = [
            "Avoid placing a break immediately after bathing.",
            "avoid break after bathing",
            "No break after bathing",
            "Don't put a break after bathing",
            "Do not place a short break after bathing task."
        ]
        for p in phrases:
            extracted = extract_preference_from_text(p)
            self.assertIsNotNone(extracted, f"Failed to extract from: {p}")
            self.assertEqual(extracted.preference_type, "AVOID_BREAK_AFTER")
            self.assertTrue(activities_match(extracted.target_activity, "bathing"))

    # -------------------------------------------------------------------------
    # 2. Safely Ignoring Ambiguous or Unsupported Comments
    # -------------------------------------------------------------------------
    def test_ignore_unsupported_or_ambiguous_comments(self):
        unsupported = [
            "The weather is nice today.",
            "It took longer because of an unexpected interruption.",
            "I was distracted.",
            "Great schedule!",
            "I want more time for studying",
            "Break was too long",
            "Maybe do something later",
            "",
            None,
            "   "
        ]
        for comment in unsupported:
            extracted = extract_preference_from_text(comment)
            self.assertIsNone(extracted, f"Should have ignored comment: {comment}")

    # -------------------------------------------------------------------------
    # 3. Persistence and Deduplication Per User (No Cross-User Leakage)
    # -------------------------------------------------------------------------
    def test_per_user_storage_and_isolation(self):
        # User 1 adds a preference
        pref1 = UserPreference(
            user_id=self.user1.id,
            preference_type="REPLACE_BREAK_WITH_TASK",
            target_activity="bathing",
            preferred_activity="breakfast",
            relationship="instead_of_break_after",
            rule_description="Prefer Breakfast after bathing"
        )
        self.db.add(pref1)
        self.db.commit()

        # Query User 1
        u1_prefs = self.db.query(UserPreference).filter(UserPreference.user_id == self.user1.id).all()
        self.assertEqual(len(u1_prefs), 1)
        self.assertEqual(u1_prefs[0].rule_description, "Prefer Breakfast after bathing")

        # Query User 2 - MUST be completely empty (no leakage)
        u2_prefs = self.db.query(UserPreference).filter(UserPreference.user_id == self.user2.id).all()
        self.assertEqual(len(u2_prefs), 0)

    def test_preference_update_on_revised_feedback(self):
        # User 1 sets initial preference: "Prefer a short break after coding"
        pref = UserPreference(
            user_id=self.user1.id,
            preference_type="PREFER_BREAK_AFTER",
            target_activity="coding",
            preferred_activity="short_break",
            relationship="after",
            rule_description="Prefer a short break after coding"
        )
        self.db.add(pref)
        self.db.commit()

        # Later, user submits revised feedback: "Avoid break after coding"
        revised = extract_preference_from_text("Avoid break after coding")
        existing = self.db.query(UserPreference).filter(
            UserPreference.user_id == self.user1.id,
            UserPreference.target_activity == revised.target_activity
        ).first()

        self.assertIsNotNone(existing)
        existing.preference_type = revised.preference_type
        existing.preferred_activity = revised.preferred_activity
        existing.relationship = revised.relationship
        existing.rule_description = revised.rule_description
        existing.updated_at = datetime.utcnow()
        self.db.commit()

        # Verify only 1 record exists and it is revised
        all_prefs = self.db.query(UserPreference).filter(UserPreference.user_id == self.user1.id).all()
        self.assertEqual(len(all_prefs), 1)
        self.assertEqual(all_prefs[0].preference_type, "AVOID_BREAK_AFTER")
        self.assertEqual(all_prefs[0].rule_description, "Avoid break after coding")

    # -------------------------------------------------------------------------
    # 4. Applying Preferences During Scheduling
    # -------------------------------------------------------------------------
    def test_apply_replace_break_with_task_preference(self):
        # Seed user preference: prefer breakfast rather than a break after bathing
        pref = UserPreference(
            user_id=self.user1.id,
            preference_type="REPLACE_BREAK_WITH_TASK",
            target_activity="bathing",
            preferred_activity="breakfast",
            relationship="instead_of_break_after",
            rule_description="Prefer Breakfast after bathing"
        )
        self.db.add(pref)
        self.db.commit()

        # Day window 09:00 - 14:00 (540 - 840)
        # Tasks: bathing (20m), study (40m), breakfast (15m)
        tasks = [
            {"task_name": "bathing", "duration": 20, "is_fixed": False},
            {"task_name": "study", "duration": 40, "is_fixed": False},
            {"task_name": "breakfast", "duration": 15, "is_fixed": False}
        ]

        result = generate_schedule(540, 840, tasks, user_id=self.user1.id, db_session=self.db)
        blocks = result["schedule"]

        # Expected:
        # 1. 09:00 - 09:20 | Task: bathing
        # 2. 09:20 - 09:35 | Task: Breakfast (scheduled immediately after bathing, break avoided!)
        bathing_idx = None
        for i, b in enumerate(blocks):
            if "bathing" in b["title"].lower():
                bathing_idx = i
                break

        self.assertIsNotNone(bathing_idx)
        next_block = blocks[bathing_idx + 1]
        self.assertEqual(next_block["category"], "meal")
        self.assertIn("breakfast", next_block["title"].lower())
        # Break between bathing and breakfast is avoided: breakfast starts immediately when bathing ends
        self.assertEqual(next_block["start"], blocks[bathing_idx]["end"])
        self.assertEqual(next_block["end"], next_block["start"] + 15)

    def test_apply_avoid_break_preference(self):
        pref = UserPreference(
            user_id=self.user1.id,
            preference_type="AVOID_BREAK_AFTER",
            target_activity="bathing",
            preferred_activity=None,
            relationship="avoid_after",
            rule_description="Avoid break after bathing"
        )
        self.db.add(pref)
        self.db.commit()

        tasks = [
            {"task_name": "bathing", "duration": 20, "is_fixed": False},
            {"task_name": "study", "duration": 40, "is_fixed": False}
        ]

        result = generate_schedule(540, 840, tasks, user_id=self.user1.id, db_session=self.db)
        blocks = result["schedule"]

        # Bathing followed directly by study with NO break in between
        bathing_idx = next(i for i, b in enumerate(blocks) if "bathing" in b["title"].lower())
        next_block = blocks[bathing_idx + 1]
        self.assertEqual(next_block["category"], "task")
        self.assertIn("study", next_block["title"].lower())
        self.assertEqual(next_block["start"], blocks[bathing_idx]["end"])

    # -------------------------------------------------------------------------
    # 5. Handling Infeasible Preferences Safely
    # -------------------------------------------------------------------------
    def test_infeasible_preference_does_not_violate_fixed_task(self):
        # User wants breakfast after bathing instead of break
        pref = UserPreference(
            user_id=self.user1.id,
            preference_type="REPLACE_BREAK_WITH_TASK",
            target_activity="bathing",
            preferred_activity="breakfast",
            relationship="instead_of_break_after",
            rule_description="Prefer Breakfast after bathing"
        )
        self.db.add(pref)
        self.db.commit()

        # Fixed meeting is at 09:25 - 10:00.
        # Bathing is 20m (09:00 - 09:20). Only 5 mins left before fixed meeting.
        # Breakfast takes 15m -> CANNOT fit between 09:20 and 09:25!
        tasks = [
            {"task_name": "bathing", "duration": 20, "is_fixed": False},
            {"task_name": "Fixed Meeting", "duration": 35, "is_fixed": True, "fixed_start": 565, "fixed_end": 600},
            {"task_name": "breakfast", "duration": 15, "is_fixed": False}
        ]

        result = generate_schedule(540, 840, tasks, user_id=self.user1.id, db_session=self.db)
        blocks = result["schedule"]

        # Ensure Fixed Meeting strictly starts at 565 (09:25) and ends at 600 (10:00)
        meeting = next(b for b in blocks if "meeting" in b["title"].lower())
        self.assertEqual(meeting["start"], 565)
        self.assertEqual(meeting["end"], 600)

        # Breakfast must be scheduled AFTER the fixed meeting, not overlapping it!
        breakfast = next(b for b in blocks if "breakfast" in b["title"].lower())
        self.assertGreaterEqual(breakfast["start"], 600)

    def test_ordinary_feedback_without_preference_stored_cleanly(self):
        # Ordinary feedback without any scheduling phrase
        log = FeedbackLog(
            user_id=self.user1.id,
            task_name="study",
            planned_start=540,
            planned_end=600,
            planned_duration=60,
            actual_duration=55,
            completed="yes",
            feedback="Completed task on time without any issues."
        )
        self.db.add(log)
        self.db.commit()

        extracted = extract_preference_from_text(log.feedback)
        self.assertIsNone(extracted)

        # No preference should be created
        prefs = self.db.query(UserPreference).filter(UserPreference.user_id == self.user1.id).all()
        self.assertEqual(len(prefs), 0)

    def test_user_input_sequence_preserved_with_fixed_and_flexible_tasks(self):
        """Verify that flexible tasks placed after fixed tasks in user input order
        remain after those fixed tasks on the generated timeline."""
        tasks = [
            {"task_name": "freshen up", "duration": 30, "is_fixed": False},
            {"task_name": "breakfast", "duration": 30, "is_fixed": False},
            {"task_name": "tv", "duration": 30, "is_fixed": False},
            {"task_name": "meet", "duration": 60, "is_fixed": True, "fixed_start": 720, "fixed_end": 780},     # 12:00 - 13:00
            {"task_name": "lunch", "duration": 30, "is_fixed": True, "fixed_start": 840, "fixed_end": 870},    # 14:00 - 14:30
            {"task_name": "workout", "duration": 60, "is_fixed": True, "fixed_start": 960, "fixed_end": 1020}, # 16:00 - 17:00
            {"task_name": "evg snacks", "duration": 15, "is_fixed": False},
            {"task_name": "study", "duration": 60, "is_fixed": False},
            {"task_name": "dinner", "duration": 30, "is_fixed": False},
        ]

        result = generate_schedule(540, 1380, tasks, user_id=self.user1.id, db_session=self.db)
        blocks = result["schedule"]

        # Find items
        def find_block(name):
            return next(b for b in blocks if name.lower() in b["title"].lower())

        meet = find_block("meet")
        lunch = find_block("lunch")
        workout = find_block("workout")
        evg_snacks = find_block("evg snacks")
        study = find_block("study")
        dinner = find_block("dinner")

        # Fixed tasks are at their exact times
        self.assertEqual(meet["start"], 720)
        self.assertEqual(meet["end"], 780)
        self.assertEqual(lunch["start"], 840)
        self.assertEqual(lunch["end"], 870)
        self.assertEqual(workout["start"], 960)
        self.assertEqual(workout["end"], 1020)

        # Evening snacks was placed AFTER workout by user -> MUST start at or after workout end (1020 = 17:00)
        self.assertGreaterEqual(evg_snacks["start"], workout["end"])
        # Study was placed AFTER evg snacks -> MUST start at or after evg snacks end
        self.assertGreaterEqual(study["start"], evg_snacks["end"])
        # Dinner was placed AFTER study -> MUST start at or after study end
        self.assertGreaterEqual(dinner["start"], study["end"])
        # Dinner must be in its dinner window (1140 = 19:00 to 1380 = 23:00)
        self.assertGreaterEqual(dinner["start"], 1140)


if __name__ == "__main__":
    unittest.main()

