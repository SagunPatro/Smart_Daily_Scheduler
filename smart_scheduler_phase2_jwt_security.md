# Smart Scheduler — Phase 2: JWT Authentication & Security

## Objective
Implement **Phase 2 only**. Phase 1 is complete. Add proper JWT authentication and authorization without breaking existing scheduling, database, CSV logging, or ML behavior.

## Critical safety rules
- Do NOT delete `-KIIT2786` files.
- Do NOT reset/drop/recreate `smart_scheduler.db`.
- Do NOT delete existing users or data.
- Do NOT overwrite/retrain `duration_model.pkl`.
- Do NOT replace historical `user_logs.csv`.
- Do NOT make the GitHub repository public.
- Never expose passwords, API keys, JWT secrets, or tokens.
- Do not start Phase 3.
- If a conflict is unsafe to resolve, STOP and report it.

## Step 1 — Backup
Before modifying anything, create `backup_phase2/` and back up every file that will be modified, including at minimum:
`auth.py`, `main.py`, `schemas.py`, `models.py`, `database.py`, `script.js`, `index.html`, `style.css`.
Report the backup location.

## Step 2 — Inspect current authentication
Inspect the actual current implementations of `auth.py`, `main.py`, `models.py`, `schemas.py`, and the frontend before editing.
Determine:
- how passwords are currently stored
- whether they are already hashed
- how login currently works
- whether any secret is hardcoded
- how `Task.user_id` relates to the authenticated user

Do not blindly migrate existing password records.

## Step 3 — Password hashing
Ensure passwords are never stored in plaintext.
Implement secure `hash_password()` and `verify_password()` using the existing compatible hashing dependency where possible.
- Signup hashes passwords before storage.
- Login verifies hashes.
- Never return password hashes.
- Never log passwords.
- Authentication failures must not reveal whether a username/email exists.

If existing passwords are plaintext, devise a safe migration strategy and document it rather than silently breaking existing users.

## Step 4 — JWT
Implement JWT access tokens using a maintained compatible library.
Use environment variables:
- `JWT_SECRET_KEY`
- `JWT_ALGORITHM`
- `JWT_ACCESS_TOKEN_EXPIRE_MINUTES`

Do NOT hardcode the JWT secret in Python.
Create/update `.env.example` with placeholder values only. If `.env` is needed locally, ensure it is ignored by Git.

JWT should contain only minimum identity information, preferably a stable user ID in `sub`, plus expiration. Never put passwords or password hashes in the token.

Validate signature, algorithm, expiration, and subject.

## Step 5 — Current-user dependency
Create a reusable FastAPI dependency such as `get_current_user()` that:
1. extracts `Authorization: Bearer <token>`
2. validates the JWT
3. extracts the user ID
4. loads that user from the database
5. rejects missing/invalid/expired tokens
6. returns the authenticated user

Do not duplicate JWT validation logic across endpoints.

## Step 6 — Endpoint protection

Public:
- `GET /`
- `POST /signup`
- `POST /login`

Authenticated:
- `POST /schedule`
- `POST /feedback`

Use the authenticated user identity on the backend. Never trust a client-supplied user ID for authorization.

If task CRUD endpoints do not currently exist, do not invent unnecessary endpoints.

## Step 7 — Login response
Successful `/login` should return a structure such as:
```json
{"access_token":"...","token_type":"bearer"}
```
Do not return passwords or password hashes.

## Step 8 — Frontend
Update `script.js` to:
- receive the JWT after login
- store it for the current application session
- send `Authorization: Bearer <token>` on `/schedule` and `/feedback`
- provide logout that clears the token
- handle 401 by clearing the token and returning the user to login

Do not put tokens in URLs, print them to the console, hardcode them, or send passwords after login.

If `localStorage` is used, do not falsely claim it is XSS-proof; document the tradeoff.

## Step 9 — Authorization
Use `Task.user_id` correctly where tasks are persisted.
A user must not be able to access/modify another user's data by changing a user ID in a request.
Authorization must be enforced server-side.

## Step 10 — CORS and schemas
Inspect CORS. Avoid unrestricted credentialed CORS. Keep local development working.
Review schemas so clients cannot submit password hashes or impersonate another user.

## Step 11 — Database safety
JWT should not require storing tokens in the database.
Verify existing `users` data before/after changes.
Use only non-destructive table creation if actually needed. Never drop or recreate existing tables.

## Step 12 — Dependencies
Check installed packages first. Install only genuinely required dependencies. Do not introduce duplicate authentication libraries. Report any dependency changes.

## Step 13 — Testing
Run all of these non-destructive tests:

1. Python syntax check.
2. `main.py` import.
3. `GET /` → 200.
4. Signup test account → password stored as hash, no password/hash in response.
5. Correct login → 200 and bearer access token.
6. Wrong password → authentication failure without information leakage.
7. `/schedule` without token → 401.
8. `/schedule` with invalid token → 401.
9. `/schedule` with valid token → 200 and normal scheduling.
10. `/feedback` without/invalid/valid token → only valid token succeeds.
11. Expired JWT → 401.
12. Database integrity → existing user count preserved; `users` and `tasks` tables remain intact.
13. Phase 1 regressions: normal scheduling, fixed-task conflict, ML unseen-label fallback, 8-column CSV logging, and 09:00 (`540`) to 23:00 (`1380`) regression.
14. Verify `duration_model.pkl` was not modified/retrained.

## Step 14 — Secret scan
Search the project for hardcoded passwords, API keys, JWT secrets, tokens, and password/JWT logging.
Never print discovered secrets.
If a real secret exists, move it to environment configuration and tell the user a secret was found without revealing its value. State whether it should be rotated before public GitHub release.

## Step 15 — Git safety
Ensure `.gitignore` covers:
- `.env`
- `__pycache__/`
- `*.pyc`
- `.ipynb_checkpoints/`

Do not make the repository public.

## Final report
Report:
1. modified files and exact changes
2. authentication design
3. security changes
4. database safety and existing user count before/after
5. actual test results
6. dependency changes
7. remaining security limitations
8. whether the project is ready for the next phase

Do not invent the next-phase scope if it has not been defined.

## STOP
After Phase 2 and all tests, **STOP**. Do not begin the next phase, deploy, or make GitHub public. Wait for further instructions.
