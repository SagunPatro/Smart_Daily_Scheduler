from pydantic import BaseModel, EmailStr, field_validator, model_validator
from typing import Optional, List, Any, Union


# ---------- AUTH ----------
class UserSignup(BaseModel):
    username: str
    email: EmailStr
    password: str

    @field_validator('username')
    def username_not_empty(cls, v):
        v_str = v.strip()
        if not v_str:
            raise ValueError("Username cannot be empty or whitespace")
        return v_str

    @field_validator('password')
    def password_length(cls, v):
        if len(v) < 6:
            raise ValueError("Password must be at least 6 characters long")
        return v


class UserLogin(BaseModel):
    username: str
    password: str


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    username: str


class TokenData(BaseModel):
    user_id: Optional[int] = None


# ---------- TASK ----------
class TaskCreate(BaseModel):
    task_name: str
    duration: int                       # minutes
    is_fixed: bool = False
    fixed_start: Optional[int] = None   # minutes from midnight (e.g. 540 = 09:00)
    fixed_end: Optional[int] = None     # minutes from midnight (e.g. 600 = 10:00)

    @field_validator('task_name')
    def task_name_not_empty(cls, v):
        v_str = v.strip()
        if not v_str:
            raise ValueError("Task name cannot be empty or whitespace")
        return v_str

    @field_validator('duration')
    def duration_positive(cls, v):
        if v <= 0:
            raise ValueError("Duration must be a positive integer in minutes")
        return v

    @field_validator('fixed_start', 'fixed_end')
    def valid_time_range(cls, v):
        if v is not None:
            if v < 0 or v > 1439:
                raise ValueError("Time values must be between 0 and 1439 minutes (00:00 to 23:59)")
        return v

    @model_validator(mode='after')
    def validate_fixed_task(self):
        if self.is_fixed:
            if self.fixed_start is None or self.fixed_end is None:
                raise ValueError("Fixed tasks require both fixed_start and fixed_end")
            if self.fixed_end <= self.fixed_start:
                raise ValueError("fixed_end must be greater than fixed_start")
        return self


class TaskUpdate(BaseModel):
    task_name: Optional[str] = None
    duration: Optional[int] = None
    is_fixed: Optional[bool] = None
    fixed_start: Optional[int] = None
    fixed_end: Optional[int] = None

    @field_validator('task_name')
    def task_name_not_empty(cls, v):
        if v is not None:
            v_str = v.strip()
            if not v_str:
                raise ValueError("Task name cannot be empty or whitespace")
            return v_str
        return v

    @field_validator('duration')
    def duration_positive(cls, v):
        if v is not None and v <= 0:
            raise ValueError("Duration must be a positive integer in minutes")
        return v

    @field_validator('fixed_start', 'fixed_end')
    def valid_time_range(cls, v):
        if v is not None:
            if v < 0 or v > 1439:
                raise ValueError("Time values must be between 0 and 1439 minutes")
        return v


class TaskResponse(BaseModel):
    id: int
    user_id: Optional[int] = None
    task_name: str
    duration: int
    is_fixed: bool
    fixed_start: Optional[int] = None
    fixed_end: Optional[int] = None

    class Config:
        from_attributes = True


# ---------- SCHEDULE ----------
class ScheduleRequest(BaseModel):
    day_start: int   # minutes from midnight (e.g. 540 = 09:00)
    day_end: int     # minutes from midnight (e.g. 1380 = 23:00)
    tasks: List[TaskCreate]
    title: Optional[str] = "Daily Schedule"
    save_to_history: bool = True

    @field_validator('day_start', 'day_end')
    def valid_day_time(cls, v):
        if v < 0 or v > 1439:
            raise ValueError("Day time values must be between 0 and 1439 minutes")
        return v

    @model_validator(mode='after')
    def validate_day_range(self):
        if self.day_end <= self.day_start:
            raise ValueError("day_end must be greater than day_start")
        return self


class SavedScheduleResponse(BaseModel):
    id: int
    user_id: int
    title: Optional[str] = None
    day_start: int
    day_end: int
    schedule_data: Any
    created_at: str

    class Config:
        from_attributes = True


# ---------- FEEDBACK ----------
class FeedbackRequest(BaseModel):
    task_name: str
    planned_start: Union[int, str]      # minutes from midnight or 'HH:MM'
    planned_end: Union[int, str]        # minutes from midnight or 'HH:MM'
    planned_duration: int   # minutes
    actual_duration: int    # minutes
    completed: str
    feedback: Optional[str] = ""

    @field_validator('planned_start', 'planned_end', mode='before')
    def parse_time_to_minutes(cls, v):
        if isinstance(v, str):
            v_str = v.strip()
            if ":" in v_str:
                parts = v_str.split(":")
                if len(parts) == 2 and parts[0].isdigit() and parts[1].isdigit():
                    h, m = int(parts[0]), int(parts[1])
                    if 0 <= h <= 23 and 0 <= m <= 59:
                        return h * 60 + m
                    raise ValueError("Hour must be 0-23 and minute must be 0-59")
                raise ValueError("Time string must be in HH:MM format")
            elif v_str.isdigit():
                v = int(v_str)
            else:
                raise ValueError("Time must be integer minutes or HH:MM string")
        if isinstance(v, int):
            if v < 0 or v > 1439:
                raise ValueError("Time values must be between 0 and 1439 minutes (00:00 to 23:59)")
            return v
        raise ValueError("Time must be integer minutes or HH:MM string")

    @field_validator('task_name')
    def task_name_not_empty(cls, v):
        v_str = v.strip()
        if not v_str:
            raise ValueError("Task name cannot be empty or whitespace")
        return v_str

    @field_validator('planned_duration')
    def planned_duration_positive(cls, v):
        if v <= 0:
            raise ValueError("Planned duration must be a positive integer in minutes")
        return v

    @field_validator('actual_duration')
    def actual_duration_non_negative(cls, v):
        if v < 0:
            raise ValueError("Actual duration must be non-negative")
        return v

    @field_validator('completed')
    def completed_valid(cls, v):
        v_str = v.strip().lower()
        if v_str not in ("yes", "no"):
            raise ValueError("Completed must be 'yes' or 'no'")
        return v_str

    @model_validator(mode='after')
    def validate_planned_range(self):
        if self.planned_end <= self.planned_start:
            raise ValueError("planned_end must be greater than planned_start")
        return self


class FeedbackResponse(BaseModel):
    message: str
    preference_recognized: bool = False
    preference: Optional[str] = None
    preference_type: Optional[str] = None


# ---------- USER PREFERENCES ----------
class UserPreferenceResponse(BaseModel):
    id: int
    user_id: int
    preference_type: str
    target_activity: str
    preferred_activity: Optional[str] = None
    relationship: str
    rule_description: str
    created_at: str
    updated_at: str

    class Config:
        from_attributes = True



# ---------- RETRAIN ----------
class RetrainResponse(BaseModel):
    success: bool
    sample_count: int
    message: str


# ---------- ML STATUS ----------
class MLStatusResponse(BaseModel):
    total_feedback_count: int
    user_feedback_count: int
    personalized_task_count: int
    personalized_tasks: List[str]
    model_last_trained: Optional[str]
    is_model_cached: bool



