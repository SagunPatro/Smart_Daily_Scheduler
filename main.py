import json
from typing import List
from fastapi import FastAPI, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from datetime import datetime
from database import engine, Base, SessionLocal
from auth import router as auth_router, get_current_user
from models import User, Task, SavedSchedule, FeedbackLog, UserPreference
from scheduler import generate_schedule
from logger import log_task
from ml_duration_model import train_model, normalize_task_name, is_model_cached, get_model_last_trained
from preference_learning import extract_preference_from_text
from schemas import (
    ScheduleRequest, FeedbackRequest, FeedbackResponse, TaskCreate, TaskUpdate, TaskResponse,
    SavedScheduleResponse, RetrainResponse, MLStatusResponse, UserPreferenceResponse
)

# ---------- App ----------
app = FastAPI(
    title="Smart Daily Scheduler API",
    version="1.0.0",
    description="Backend REST API for Smart Daily Scheduler Android Application"
)

# ---------- DB ----------
Base.metadata.create_all(bind=engine)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# ---------- CORS ----------
origins = [
    "http://127.0.0.1:8000",
    "http://localhost:8000",
    "http://127.0.0.1:5500",
    "http://localhost:5500"
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------- Routers ----------
app.include_router(auth_router)

# ---------- Public Routes ----------
@app.get("/")
def root():
    return {"status": "Backend running ✅"}


@app.get("/health")
def health():
    return {"status": "ok"}


# ---------- TASK CRUD ENDPOINTS ----------
@app.get("/tasks", response_model=List[TaskResponse])
def get_user_tasks(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    return db.query(Task).filter(Task.user_id == current_user.id).all()


@app.post("/tasks", response_model=TaskResponse, status_code=201)
def create_task(
    task_data: TaskCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    task = Task(
        user_id=current_user.id,
        task_name=task_data.task_name,
        duration=task_data.duration,
        is_fixed=task_data.is_fixed,
        fixed_start=task_data.fixed_start,
        fixed_end=task_data.fixed_end
    )
    db.add(task)
    db.commit()
    db.refresh(task)
    return task


@app.put("/tasks/{task_id}", response_model=TaskResponse)
def update_task(
    task_id: int,
    task_data: TaskUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    task = db.query(Task).filter(Task.id == task_id, Task.user_id == current_user.id).first()
    if not task:
        raise HTTPException(status_code=404, detail="Task not found or unauthorized")

    if task_data.task_name is not None:
        task.task_name = task_data.task_name
    if task_data.duration is not None:
        task.duration = task_data.duration
    if task_data.is_fixed is not None:
        task.is_fixed = task_data.is_fixed
    if task_data.fixed_start is not None:
        task.fixed_start = task_data.fixed_start
    if task_data.fixed_end is not None:
        task.fixed_end = task_data.fixed_end

    db.commit()
    db.refresh(task)
    return task


@app.delete("/tasks/{task_id}")
def delete_task(
    task_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    task = db.query(Task).filter(Task.id == task_id, Task.user_id == current_user.id).first()
    if not task:
        raise HTTPException(status_code=404, detail="Task not found or unauthorized")

    db.delete(task)
    db.commit()
    return {"message": "Task deleted successfully"}


# ---------- SCHEDULE ENDPOINTS ----------
@app.post("/schedule")
def create_schedule(
    req: ScheduleRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    try:
        result = generate_schedule(
            req.day_start,
            req.day_end,
            req.tasks,
            user_id=current_user.id,
            db_session=db
        )
        schedule_id = None
        if req.save_to_history:
            # Auto-clear schedules from prior months on new save (end-of-month rollover)
            now = datetime.utcnow()
            start_of_current_month = datetime(now.year, now.month, 1, 0, 0, 0)
            db.query(SavedSchedule).filter(
                SavedSchedule.user_id == current_user.id,
                SavedSchedule.created_at < start_of_current_month
            ).delete(synchronize_session=False)

            sched_record = SavedSchedule(
                user_id=current_user.id,
                title=req.title or "Daily Schedule",
                day_start=req.day_start,
                day_end=req.day_end,
                schedule_json=json.dumps(result)
            )
            db.add(sched_record)
            db.commit()
            db.refresh(sched_record)
            schedule_id = sched_record.id

        return {
            "schedule_id": schedule_id,
            "schedule": result.get("schedule", []),
            "warnings": result.get("warnings", [])
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/schedules", response_model=List[SavedScheduleResponse])
def get_user_schedules(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    # Auto-clear schedules from prior months (end-of-month rollover)
    now = datetime.utcnow()
    start_of_current_month = datetime(now.year, now.month, 1, 0, 0, 0)
    db.query(SavedSchedule).filter(
        SavedSchedule.user_id == current_user.id,
        SavedSchedule.created_at < start_of_current_month
    ).delete(synchronize_session=False)
    db.commit()

    records = db.query(SavedSchedule).filter(SavedSchedule.user_id == current_user.id).order_by(SavedSchedule.created_at.desc()).all()
    res = []
    for r in records:
        res.append({
            "id": r.id,
            "user_id": r.user_id,
            "title": r.title,
            "day_start": r.day_start,
            "day_end": r.day_end,
            "schedule_data": json.loads(r.schedule_json),
            "created_at": r.created_at.isoformat()
        })
    return res


@app.get("/schedules/{schedule_id}", response_model=SavedScheduleResponse)
def get_schedule_by_id(
    schedule_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    r = db.query(SavedSchedule).filter(SavedSchedule.id == schedule_id, SavedSchedule.user_id == current_user.id).first()
    if not r:
        raise HTTPException(status_code=404, detail="Schedule not found or unauthorized")

    return {
        "id": r.id,
        "user_id": r.user_id,
        "title": r.title,
        "day_start": r.day_start,
        "day_end": r.day_end,
        "schedule_data": json.loads(r.schedule_json),
        "created_at": r.created_at.isoformat()
    }


@app.delete("/schedules")
def clear_all_schedules(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    deleted_count = db.query(SavedSchedule).filter(
        SavedSchedule.user_id == current_user.id
    ).delete(synchronize_session=False)
    db.commit()
    return {"message": f"Successfully cleared {deleted_count} saved schedules"}


@app.delete("/schedules/{schedule_id}")
def delete_schedule(
    schedule_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    r = db.query(SavedSchedule).filter(SavedSchedule.id == schedule_id, SavedSchedule.user_id == current_user.id).first()
    if not r:
        raise HTTPException(status_code=404, detail="Schedule not found or unauthorized")

    db.delete(r)
    db.commit()
    return {"message": "Schedule deleted successfully"}


# ---------- FEEDBACK ENDPOINT ----------
@app.post("/feedback")
def submit_feedback(
    data: FeedbackRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    # 1. Authoritative user-isolated learning-data persistence in SQLite
    record = FeedbackLog(
        user_id=current_user.id,
        task_name=data.task_name,
        planned_start=data.planned_start,
        planned_end=data.planned_end,
        planned_duration=data.planned_duration,
        actual_duration=data.actual_duration,
        completed=data.completed,
        feedback=data.feedback or ""
    )
    db.add(record)
    db.commit()
    db.refresh(record)

    # 2. Maintain legacy CSV artifact for backward compatibility
    log_task(
        task_name=data.task_name,
        planned_start=data.planned_start,
        planned_end=data.planned_end,
        planned_duration=data.planned_duration,
        actual_duration=data.actual_duration,
        completed=data.completed,
        feedback=data.feedback or ""
    )

    # 3. Lightweight rule-based preference learning from feedback text
    pref_recognized = False
    pref_description = None
    pref_type = None

    extracted = extract_preference_from_text(data.feedback)
    if extracted:
        # Check if an existing preference exists for this user and target_activity
        existing_pref = db.query(UserPreference).filter(
            UserPreference.user_id == current_user.id,
            UserPreference.target_activity == extracted.target_activity
        ).first()

        if existing_pref:
            # Update previous preference with revised feedback
            existing_pref.preference_type = extracted.preference_type
            existing_pref.preferred_activity = extracted.preferred_activity
            existing_pref.relationship = extracted.relationship
            existing_pref.rule_description = extracted.rule_description
            existing_pref.source_feedback_id = record.id
            existing_pref.updated_at = datetime.utcnow()
        else:
            new_pref = UserPreference(
                user_id=current_user.id,
                preference_type=extracted.preference_type,
                target_activity=extracted.target_activity,
                preferred_activity=extracted.preferred_activity,
                relationship=extracted.relationship,
                rule_description=extracted.rule_description,
                source_feedback_id=record.id,
                created_at=datetime.utcnow(),
                updated_at=datetime.utcnow()
            )
            db.add(new_pref)

        db.commit()
        pref_recognized = True
        pref_description = extracted.rule_description
        pref_type = extracted.preference_type

    if pref_recognized:
        return {
            "message": f"Preference saved: {pref_description}.",
            "preference_recognized": True,
            "preference": pref_description,
            "preference_type": pref_type
        }
    else:
        return {"message": "Feedback saved ✅"}


# ---------- PREFERENCES ENDPOINTS ----------
@app.get("/preferences", response_model=List[UserPreferenceResponse])
def get_user_preferences(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    prefs = db.query(UserPreference).filter(UserPreference.user_id == current_user.id).all()
    res = []
    for p in prefs:
        res.append({
            "id": p.id,
            "user_id": p.user_id,
            "preference_type": p.preference_type,
            "target_activity": p.target_activity,
            "preferred_activity": p.preferred_activity,
            "relationship": p.relationship,
            "rule_description": p.rule_description,
            "created_at": p.created_at.isoformat() if p.created_at else "",
            "updated_at": p.updated_at.isoformat() if p.updated_at else ""
        })
    return res


@app.delete("/preferences/{pref_id}")
def delete_user_preference(
    pref_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    pref = db.query(UserPreference).filter(UserPreference.id == pref_id, UserPreference.user_id == current_user.id).first()
    if not pref:
        raise HTTPException(status_code=404, detail="Preference not found or unauthorized")
    db.delete(pref)
    db.commit()
    return {"message": "Preference deleted successfully"}



# ---------- RETRAIN ENDPOINT ----------
@app.post("/retrain", response_model=RetrainResponse)
def retrain_model_endpoint(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    result = train_model(db_session=db)
    if not result.get("success", False):
        raise HTTPException(status_code=400, detail=result.get("message", "Model retraining failed"))
    return result


# ---------- ML STATUS ENDPOINT ----------
@app.get("/ml/status", response_model=MLStatusResponse)
def get_ml_status(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    # 1. Total completed feedback count system-wide
    total_feedback_count = db.query(FeedbackLog).filter(FeedbackLog.completed == "yes").count()

    # 2. Completed feedback records belonging strictly to current_user.id
    user_logs = db.query(FeedbackLog).filter(
        FeedbackLog.user_id == current_user.id,
        FeedbackLog.completed == "yes"
    ).all()
    user_feedback_count = len(user_logs)

    # 3. Group completed user logs by normalized task name and identify tasks with >= 3 entries
    task_counts = {}
    for log in user_logs:
        if log.planned_duration > 0:
            norm_name = normalize_task_name(log.task_name)
            task_counts[norm_name] = task_counts.get(norm_name, 0) + 1

    personalized_tasks = sorted([name for name, count in task_counts.items() if count >= 3])
    personalized_task_count = len(personalized_tasks)

    # 4. Model last trained timestamp and cache status
    model_last_trained = get_model_last_trained()
    cached = is_model_cached()

    return {
        "total_feedback_count": total_feedback_count,
        "user_feedback_count": user_feedback_count,
        "personalized_task_count": personalized_task_count,
        "personalized_tasks": personalized_tasks,
        "model_last_trained": model_last_trained,
        "is_model_cached": cached
    }


