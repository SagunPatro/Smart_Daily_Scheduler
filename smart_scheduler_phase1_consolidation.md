# Smart Scheduler — Phase 1: Codebase Consolidation

## Role

Act as the implementation engineer for the existing Smart Scheduler project.

The project has already been audited. This task is **Phase 1 only: Codebase Consolidation & Structural Cleanup**.

The goal is to create one clean, working baseline by carefully merging the useful functionality from the `-KIIT2786` variants into the primary root files.

---

# CRITICAL SAFETY RULES

Before making changes:

1. Do NOT delete any `-KIIT2786` files.
2. Do NOT delete or reset `smart_scheduler.db`.
3. Do NOT overwrite, retrain, or regenerate `duration_model.pkl`.
4. Do NOT discard existing user data.
5. Do NOT perform destructive database migrations.
6. Preserve a clear rollback path.
7. Do not expose any actual passwords, API keys, tokens, or secrets in output.
8. Do not make unrelated changes outside Phase 1.
9. Do not implement JWT authentication yet.
10. Do not implement deployment configuration yet.

If you discover a conflict that cannot be safely resolved, stop and report it rather than guessing.

---

# EXISTING AUDIT FINDINGS

The current project has:

- `main.py` — currently active FastAPI entry point
- `main-KIIT2786.py` — more advanced but currently broken variant
- `schemas.py` — active but incomplete
- `schemas-KIIT2786.py` — more complete schemas
- `models.py` — active but missing Task model
- `models-KIIT2786.py` — contains User + Task models
- `scheduler.py` — active but has a day_start unit bug and limited functionality
- `scheduler-KIIT2786.py` — more advanced scheduler with fixed tasks and structured output
- `logger.py` — active 8-column logging format
- `logger-KIIT2786.py` — incompatible 9-column format
- `ml_duration_model.py` — expects the existing 8-column `user_logs.csv`
- `duration_model.pkl` — existing trained model that must not be overwritten
- `index.html`, `style.css`, `script.js` — active but basic frontend
- `index-KIIT2786.html`, `style-KIIT2786.css`, `script-KIIT2786.js` — more advanced frontend variants
- `smart_scheduler.db` — active database
- `smart_scheduler-KIIT2786.db` — variant database
- `user_logs.csv` — active ML dataset
- `user_logs-KIIT2786.csv` — variant dataset

The audit found that the `-KIIT2786` files are generally a previous, more feature-rich refactoring branch, but they are not consistently wired together.

---

# PHASE 1 OBJECTIVE

Create a single coherent root codebase using:

- `main.py`
- `schemas.py`
- `models.py`
- `scheduler.py`
- `logger.py`
- `ml_duration_model.py`
- `retrain_model.py`
- `index.html`
- `style.css`
- `script.js`

The root files should contain the best compatible functionality from the variants.

The resulting root project must be internally consistent and runnable.

---

# STEP 1 — CREATE A SAFE BACKUP

Before editing any existing source file:

Create a non-destructive backup of the current root source files.

Do not delete anything.

Report exactly what was backed up.

Do not backup secrets into a new publicly shareable file.

---

# STEP 2 — CONSOLIDATE PYDANTIC SCHEMAS

Update `schemas.py` using the useful definitions from `schemas-KIIT2786.py`.

Ensure the root `schemas.py` contains the necessary schemas for:

- User signup
- User login
- Task creation/input
- Schedule requests
- Feedback

Ensure field names/types/defaults are internally consistent with the scheduler and `main.py`.

Do not blindly copy code. Resolve conflicts based on actual usage.

Then update imports throughout the project so root files use `schemas.py`.

---

# STEP 3 — CONSOLIDATE DATABASE MODELS

Update `models.py` to include the useful `Task` model from `models-KIIT2786.py`, while preserving the existing `User` model and database compatibility.

The Task model should support the existing scheduler requirements, including:

- task name
- duration
- fixed/non-fixed status
- fixed start
- fixed end
- user association

Do not destroy or reset the existing database.

Use SQLAlchemy in a way compatible with the current `database.py`.

If adding the model does not automatically create the table in the existing database, determine the safest non-destructive way to make the table available.

Do not delete existing rows.

---

# STEP 4 — CONSOLIDATE THE SCHEDULER

Use `scheduler-KIIT2786.py` as the primary reference for enhanced scheduling functionality, while preserving working behavior from `scheduler.py`.

The consolidated `scheduler.py` should support:

- flexible tasks
- fixed-time tasks
- blocked/fixed periods
- conflict detection
- meal windows where already supported
- short breaks
- long break/lunch handling where appropriate
- buffer/free time
- ML duration prediction
- structured schedule output
- useful warnings/errors

### IMPORTANT: FIX THE UNIT BUG

The audit identified this problem in the old scheduler:

`current_time = day_start * 60`

Determine the actual unit expected by the API/frontend and standardize it.

Use one clear convention throughout the application:

**minutes from midnight** for internal scheduling calculations.

For example:

- 08:00 = 480
- 09:00 = 540
- 13:30 = 810

Do not multiply an already-minute-based value by 60.

Ensure frontend conversion, schema validation, scheduler input, and output all agree.

---

# STEP 5 — CONSOLIDATE MAIN API

Keep `main.py` as the primary application entry point.

Merge the useful scheduling/API behavior from `main-KIIT2786.py` into `main.py`.

Ensure:

- FastAPI starts successfully
- imports resolve correctly
- `/` health endpoint works
- `/signup` works
- `/login` works
- `/schedule` works
- `/feedback` works
- request/response schemas are consistent
- scheduler errors are returned cleanly

### DO NOT IMPLEMENT JWT YET

Authentication security improvements are Phase 2.

For Phase 1, retain the existing login behavior unless a minimal compatibility change is required.

Do not introduce a new authentication library in this phase.

---

# STEP 6 — STANDARDIZE LOGGING

Keep the existing ML-compatible `user_logs.csv` schema unless there is a strong technical reason to change it.

The current ML model expects 8 columns:

1. date
2. task_name
3. planned_start
4. planned_end
5. planned_duration
6. actual_duration
7. completed
8. feedback

Do NOT switch to the incompatible 9-column `logger-KIIT2786.py` format during this phase.

Consolidate `logger.py` so its output remains compatible with `ml_duration_model.py`.

Do not rewrite existing historical rows unnecessarily.

---

# STEP 7 — PRESERVE AND HARDEN ML COMPATIBILITY

Do NOT retrain the model.

Do NOT overwrite `duration_model.pkl`.

Make only the minimum code changes required for the consolidated scheduler to call the existing model correctly.

If you find that the existing model cannot safely support a change, preserve compatibility and report the limitation rather than retraining it.

Do not fully implement unseen-label fallback in this phase unless it is required to prevent the consolidated application from crashing. ML hardening is primarily Phase 4.

---

# STEP 8 — CONSOLIDATE THE FRONTEND

Use the advanced `-KIIT2786` frontend as the design/functionality reference.

Bring the useful UI functionality into the root:

- `index.html`
- `style.css`
- `script.js`

The root frontend should support the currently available backend functionality, including:

- signup
- login
- task entry
- fixed-task toggle/time inputs
- schedule generation
- schedule display
- feedback submission

Do not implement JWT localStorage/session persistence yet. That belongs to Phase 2.

Make sure JavaScript API payloads exactly match the consolidated Pydantic schemas.

Ensure time conversion matches the backend's minutes-from-midnight convention.

---

# STEP 9 — DUPLICATE FILES

Do NOT delete the `-KIIT2786` files.

After consolidation, document:

- which functionality was merged from each variant
- which variants are now backups/reference copies
- whether any variant remains required at runtime

The final application should use the root files rather than importing from `-KIIT2786` files.

---

# STEP 10 — TESTING

After implementation, run non-destructive tests.

At minimum:

### Python syntax

Compile all relevant root Python files.

### Import test

Verify `main.py` and its dependencies can import successfully.

### FastAPI startup

Start the application and verify it starts without import/runtime errors.

### Health endpoint

Verify:

`GET /`

### Signup/login

Test with a temporary test account if needed.

Do not delete or overwrite the existing user's data.

### Schedule test

Test at least:

1. simple flexible tasks
2. multiple tasks
3. fixed-time task
4. fixed task + flexible tasks
5. a schedule beginning at 09:00 / 540 minutes
6. a schedule containing a meal/break window if supported

### Feedback

Verify feedback can be submitted and is written using the 8-column ML-compatible format.

### Frontend

Verify the root frontend can communicate with the backend.

---

# DATA SAFETY

Before and after testing:

- Do not reset the database.
- Do not delete users.
- Do not replace historical CSV data.
- Do not retrain the model.
- Do not overwrite `duration_model.pkl`.

If a test account is created, clearly identify it in the report. Do not remove existing user data.

---

# FINAL REPORT

After completing Phase 1, report:

## 1. CHANGES MADE

List every modified file and what changed.

## 2. FILES LEFT UNTOUCHED

Especially mention:

- database files
- ML model
- duplicate variants

## 3. CONSOLIDATION DECISIONS

Explain what was taken from each `-KIIT2786` variant.

## 4. TEST RESULTS

Give the actual results of each test.

## 5. REMAINING ISSUES

List issues intentionally left for later phases.

## 6. PHASE 2 READINESS

State whether the project is ready for:

**Phase 2 — JWT Authentication & Security**

Do not begin Phase 2 automatically.

---

# STOP CONDITION

After completing Phase 1 and its tests:

**STOP.**

Do not start Phase 2.

Do not add deployment configuration.

Do not make the GitHub repository public.

Wait for further instructions.
