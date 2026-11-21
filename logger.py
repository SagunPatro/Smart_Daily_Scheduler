import csv
from datetime import date

LOG_FILE = "user_logs.csv"

def log_task(
    task_name: str,
    planned_start: int,
    planned_end: int,
    planned_duration: int,
    actual_duration: int,
    completed: str,
    feedback: str
):
    """
    Logs task execution feedback in 8-column format compatible with ml_duration_model.py.
    Schema: [date, task_name, planned_start, planned_end, planned_duration, actual_duration, completed, feedback]
    """
    with open(LOG_FILE, "a", newline="") as file:
        writer = csv.writer(file)
        writer.writerow([
            date.today().isoformat(),
            task_name,
            planned_start,
            planned_end,
            planned_duration,
            actual_duration,
            completed,
            feedback
        ])
