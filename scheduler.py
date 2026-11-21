from typing import List, Dict, Any, Union, Optional
import os

from preference_learning import activities_match, normalize_activity_name

# Safe ML import
try:
    from ml_duration_model import predict_duration
    ML_AVAILABLE = True
except Exception:
    ML_AVAILABLE = False


def minutes_to_time(m: int) -> str:
    h = (m // 60) % 24
    min_val = m % 60
    return f"{h:02d}:{min_val:02d}"


def get_attr(obj: Any, attr: str, default: Any = None) -> Any:
    if isinstance(obj, dict):
        return obj.get(attr, default)
    return getattr(obj, attr, default)


def safe_predict_duration(
    task_name: str,
    planned_duration: int,
    start_time: int,
    user_id: Optional[int] = None,
    db_session: Optional[Any] = None
) -> int:
    if not ML_AVAILABLE or not os.path.exists("duration_model.pkl"):
        return max(5, int(planned_duration))
    try:
        predicted = predict_duration(
            task_name=task_name,
            planned_duration=planned_duration,
            start_minute=start_time,
            user_id=user_id,
            db_session=db_session
        )
        return max(5, int(predicted))
    except Exception:
        # Fallback for unseen labels, model errors, or missing pickle file
        return max(5, int(planned_duration))


# ---------- Scheduler ----------
def generate_schedule(
    day_start: int,
    day_end: int,
    tasks: List[Union[Dict, Any]],
    user_id: Optional[int] = None,
    db_session: Optional[Any] = None
) -> Dict[str, Any]:
    """
    Generates a daily schedule using minutes-from-midnight time units.
    day_start: e.g. 540 (09:00)
    day_end: e.g. 1380 (23:00)
    """
    schedule = []
    warnings = []

    current_time = day_start
    day_end_time = day_end

    SHORT_BREAK = 10
    LONG_BREAK = 30

    # ---------- Meal windows (minutes from midnight) ----------
    MEALS = {
        "breakfast": (7 * 60, 11 * 60),            # 07:00 - 11:00
        "lunch": (12 * 60, 15 * 60 + 30),          # 12:00 - 15:30
        "dinner": (19 * 60, 23 * 60),              # 19:00 - 23:00
    }

    # ---------- User Preference Learning Integration ----------
    user_preferences = []
    if user_id is not None and db_session is not None:
        try:
            from models import UserPreference
            user_preferences = db_session.query(UserPreference).filter(UserPreference.user_id == user_id).all()
        except Exception:
            user_preferences = []

    # ---------- Validate fixed tasks overlap ----------
    fixed_tasks = [t for t in tasks if bool(get_attr(t, "is_fixed", False))]
    fixed_tasks.sort(key=lambda x: get_attr(x, "fixed_start", 0) or 0)

    for i in range(1, len(fixed_tasks)):
        prev = fixed_tasks[i - 1]
        curr = fixed_tasks[i]
        prev_end = get_attr(prev, "fixed_end", 0) or 0
        curr_start = get_attr(curr, "fixed_start", 0) or 0
        if curr_start < prev_end:
            raise ValueError(
                f"Fixed tasks overlap: {get_attr(prev, 'task_name')} and {get_attr(curr, 'task_name')}"
            )

    remaining_tasks = list(tasks)

    # ---------- Schedule tasks sequentially preserving user input order ----------
    while remaining_tasks:
        if current_time >= day_end_time:
            for rem in remaining_tasks:
                rem_name = str(get_attr(rem, "task_name", "Task"))
                warnings.append(f"Task '{rem_name}' could not fit before day end limit")
            break

        task = remaining_tasks.pop(0)
        is_fixed = bool(get_attr(task, "is_fixed", False))
        name = str(get_attr(task, "task_name", "Task")).strip()

        if is_fixed:
            fs = get_attr(task, "fixed_start")
            fe = get_attr(task, "fixed_end")
            if fs is None or fe is None:
                continue

            if current_time < fs:
                current_time = fs
            elif current_time > fs:
                warnings.append(f"Fixed task '{name}' overlaps with preceding schedule at {minutes_to_time(fs)}")

            schedule.append({
                "start": fs,
                "end": fe,
                "title": name,
                "category": "fixed_task",
                "formatted": f"{minutes_to_time(fs)} - {minutes_to_time(fe)} | Task: {name} (Fixed)"
            })
            current_time = max(current_time, fe)
            continue

        # Flexible task
        is_meal = name.lower() in MEALS
        meal_key = name.lower() if is_meal else None
        planned = int(get_attr(task, "duration", 30))

        if is_meal:
            w_start, w_end = MEALS[meal_key]
            if current_time < w_start:
                current_time = w_start
            duration = planned
        else:
            duration = safe_predict_duration(
                task_name=name,
                planned_duration=planned,
                start_time=current_time,
                user_id=user_id,
                db_session=db_session
            )

        # Look ahead for next fixed task among remaining tasks
        next_fixed = None
        for rem in remaining_tasks:
            if bool(get_attr(rem, "is_fixed", False)):
                next_fixed = rem
                break

        if next_fixed is not None:
            nfs = get_attr(next_fixed, "fixed_start")
            nfe = get_attr(next_fixed, "fixed_end")
        else:
            nfs = day_end_time
            nfe = day_end_time

        # Check if flexible task fits before next fixed task
        if current_time + duration > nfs:
            if next_fixed is not None:
                warnings.append(
                    f"Task '{name}' could not fit before fixed task '{get_attr(next_fixed, 'task_name')}' at {minutes_to_time(nfs)}; rescheduling after."
                )
                remaining_tasks.remove(next_fixed)
                if current_time < nfs:
                    current_time = nfs
                schedule.append({
                    "start": nfs,
                    "end": nfe,
                    "title": get_attr(next_fixed, "task_name"),
                    "category": "fixed_task",
                    "formatted": f"{minutes_to_time(nfs)} - {minutes_to_time(nfe)} | Task: {get_attr(next_fixed, 'task_name')} (Fixed)"
                })
                current_time = max(current_time, nfe)
                remaining_tasks.insert(0, task)
                continue
            else:
                warnings.append(f"Task '{name}' could not fit before day end limit")
                continue

        if current_time + duration > day_end_time:
            warnings.append(f"Task '{name}' could not fit before day end limit")
            continue

        start = current_time
        end = start + duration
        title = name.capitalize() if is_meal else name
        cat = "meal" if is_meal else "task"

        schedule.append({
            "start": start,
            "end": end,
            "title": title,
            "category": cat,
            "formatted": f"{minutes_to_time(start)} - {minutes_to_time(end)} | Task: {title}"
        })
        current_time = end

        # Preference management
        matching_prefs = [p for p in user_preferences if activities_match(name, getattr(p, "target_activity", ""))]
        skip_break = False
        force_break = False

        for p in matching_prefs:
            ptype = getattr(p, "preference_type", "")
            pref_act = getattr(p, "preferred_activity", "")

            if ptype == "AVOID_BREAK_AFTER":
                skip_break = True
            elif ptype == "PREFER_BREAK_AFTER":
                force_break = True
            elif ptype in ("REPLACE_BREAK_WITH_TASK", "ORDER_AFTER") and pref_act:
                cand_idx = None
                for idx, rem in enumerate(remaining_tasks):
                    rem_name = str(get_attr(rem, "task_name", "")).strip()
                    if activities_match(rem_name, pref_act):
                        cand_idx = idx
                        break
                if cand_idx is not None:
                    cand_task = remaining_tasks[cand_idx]
                    cand_is_fixed = bool(get_attr(cand_task, "is_fixed", False))
                    if not cand_is_fixed:
                        cand_name = str(get_attr(cand_task, "task_name", "")).strip()
                        cand_is_meal = cand_name.lower() in MEALS
                        cand_planned = int(get_attr(cand_task, "duration", 30))
                        cand_dur = cand_planned if cand_is_meal else safe_predict_duration(
                            task_name=cand_name,
                            planned_duration=cand_planned,
                            start_time=current_time,
                            user_id=user_id,
                            db_session=db_session
                        )
                        fits_space = (current_time + cand_dur <= nfs) and (current_time + cand_dur <= day_end_time)
                        meal_feasible = True
                        if cand_is_meal:
                            w_s, w_e = MEALS[cand_name.lower()]
                            meal_feasible = (current_time >= w_s - 30) and (current_time <= w_e + 30)

                        if fits_space and meal_feasible:
                            task_to_promote = remaining_tasks.pop(cand_idx)
                            remaining_tasks.insert(0, task_to_promote)
                            if ptype == "REPLACE_BREAK_WITH_TASK":
                                skip_break = True

        # Adaptive breaks
        if remaining_tasks:
            next_t = remaining_tasks[0]
            next_is_fixed = bool(get_attr(next_t, "is_fixed", False))
            def_break = LONG_BREAK if is_meal else SHORT_BREAK

            if next_is_fixed:
                next_fs = get_attr(next_t, "fixed_start")
                space = (next_fs - current_time) if next_fs is not None else (day_end_time - current_time)
                if skip_break or space < 5:
                    actual_break = 0
                else:
                    actual_break = min(def_break, space)
                    if force_break and space >= 5:
                        actual_break = max(actual_break, min(SHORT_BREAK, space))
            else:
                next_name = str(get_attr(next_t, "task_name", "")).strip().lower()
                next_is_meal = next_name in MEALS
                next_dur = int(get_attr(next_t, "duration", 30))
                space = nfs - current_time
                max_hard_break = space - next_dur
                if next_is_meal:
                    w_s_next, w_e_next = MEALS[next_name]
                    max_w_break = max(0, w_e_next - current_time)
                    max_allowed = min(max_hard_break, max_w_break)
                else:
                    max_allowed = max_hard_break

                if skip_break or max_allowed < 5:
                    actual_break = 0
                else:
                    actual_break = min(def_break, max_allowed)
                    if force_break and max_allowed >= 5:
                        actual_break = max(actual_break, min(SHORT_BREAK, max_allowed))

            if actual_break > 0 and current_time + actual_break <= day_end_time:
                b_title = "Long Break" if actual_break >= 20 else "Short Break"
                schedule.append({
                    "start": current_time,
                    "end": current_time + actual_break,
                    "title": b_title,
                    "category": "break",
                    "formatted": f"{minutes_to_time(current_time)} - {minutes_to_time(current_time + actual_break)} | {b_title}"
                })
                current_time += actual_break
        else:
            space = day_end_time - current_time
            def_break = LONG_BREAK if is_meal else SHORT_BREAK
            if space >= def_break and current_time + def_break <= day_end_time:
                b_title = "Long Break" if def_break >= 20 else "Short Break"
                schedule.append({
                    "start": current_time,
                    "end": current_time + def_break,
                    "title": b_title,
                    "category": "break",
                    "formatted": f"{minutes_to_time(current_time)} - {minutes_to_time(current_time + def_break)} | {b_title}"
                })
                current_time += def_break

    # Sort final schedule chronologically
    schedule.sort(key=lambda item: item["start"])

    # ---------- Fill remaining free time ----------
    final_blocks = []
    last_end = day_start
    for item in schedule:
        if item["start"] > last_end:
            final_blocks.append({
                "start": last_end,
                "end": item["start"],
                "title": "Free / Buffer Time",
                "category": "free",
                "formatted": f"{minutes_to_time(last_end)} - {minutes_to_time(item['start'])} | Free / Buffer Time"
            })
        final_blocks.append(item)
        last_end = max(last_end, item["end"])

    if last_end < day_end_time:
        final_blocks.append({
            "start": last_end,
            "end": day_end_time,
            "title": "Free / Buffer Time",
            "category": "free",
            "formatted": f"{minutes_to_time(last_end)} - {minutes_to_time(day_end_time)} | Free / Buffer Time"
        })

    return {
        "schedule": final_blocks,
        "warnings": warnings
    }
