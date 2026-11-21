from datetime import datetime
from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, DateTime
from sqlalchemy.orm import relationship as orm_relationship
from database import Base

# ---------- USER MODEL ----------
class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String, unique=True, index=True, nullable=False)
    email = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=False)

    tasks = orm_relationship("Task", back_populates="user", cascade="all, delete-orphan")
    schedules = orm_relationship("SavedSchedule", back_populates="user", cascade="all, delete-orphan")
    feedback_logs = orm_relationship("FeedbackLog", back_populates="user", cascade="all, delete-orphan")
    preferences = orm_relationship("UserPreference", back_populates="user", cascade="all, delete-orphan")

# ---------- TASK MODEL ----------
class Task(Base):
    __tablename__ = "tasks"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)

    task_name = Column(String, nullable=False)
    duration = Column(Integer, nullable=False)

    is_fixed = Column(Boolean, default=False)
    fixed_start = Column(Integer, nullable=True)  # minutes from midnight
    fixed_end = Column(Integer, nullable=True)    # minutes from midnight

    user = orm_relationship("User", back_populates="tasks")

# ---------- SAVED SCHEDULE MODEL ----------
class SavedSchedule(Base):
    __tablename__ = "saved_schedules"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)

    title = Column(String, nullable=True, default="Daily Schedule")
    day_start = Column(Integer, nullable=False)   # minutes from midnight
    day_end = Column(Integer, nullable=False)     # minutes from midnight
    schedule_json = Column(String, nullable=False) # JSON encoded blocks & warnings
    created_at = Column(DateTime, default=datetime.utcnow)

    user = orm_relationship("User", back_populates="schedules")

# ---------- FEEDBACK / TASK EXECUTION LOG MODEL ----------
class FeedbackLog(Base):
    __tablename__ = "feedback_logs"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)

    task_name = Column(String, nullable=False)
    planned_start = Column(Integer, nullable=False)      # minutes from midnight
    planned_end = Column(Integer, nullable=False)        # minutes from midnight
    planned_duration = Column(Integer, nullable=False)   # minutes
    actual_duration = Column(Integer, nullable=False)    # minutes
    completed = Column(String, nullable=False)           # 'yes' or 'no'
    feedback = Column(String, nullable=True, default="")
    created_at = Column(DateTime, default=datetime.utcnow)

    user = orm_relationship("User", back_populates="feedback_logs")


# ---------- USER PREFERENCE MODEL ----------
class UserPreference(Base):
    __tablename__ = "user_preferences"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)

    preference_type = Column(String, nullable=False)      # e.g. 'REPLACE_BREAK_WITH_TASK', 'ORDER_AFTER', 'AVOID_BREAK_AFTER', 'PREFER_BREAK_AFTER'
    target_activity = Column(String, nullable=False)      # e.g. 'bathing', 'studying', 'coding'
    preferred_activity = Column(String, nullable=True)    # e.g. 'breakfast', 'lunch', 'short_break'
    relationship = Column(String, nullable=False)         # e.g. 'instead_of_break_after', 'after', 'avoid_after'
    rule_description = Column(String, nullable=False)     # e.g. 'Prefer breakfast after bathing'
    source_feedback_id = Column(Integer, ForeignKey("feedback_logs.id", ondelete="SET NULL"), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    user = orm_relationship("User", back_populates="preferences")
    source_feedback = orm_relationship("FeedbackLog")


