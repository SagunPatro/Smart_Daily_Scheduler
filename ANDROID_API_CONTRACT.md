# Android REST API Contract Document

**Smart Daily Scheduler — Backend REST API Reference for Android App (Phase 5+)**

---

## 1. Overview & Base URL Guidance

The Smart Daily Scheduler backend is a FastAPI REST application providing endpoints for authentication, task management, schedule generation, persistence, and machine-learning duration feedback.

### Environment Base URLs

| Environment | Base URL | Description |
| :--- | :--- | :--- |
| **Android Emulator (Local)** | `http://10.0.2.2:8000` | Standard Android emulator alias for host `127.0.0.1` |
| **Physical Android Device (LAN)** | `http://<HOST_LAN_IP>:8000` | Host machine's local IP address (e.g. `http://192.168.1.50:8000`) |
| **Production** | Configurable | HTTPS URL set via Retrofit build config |

> [!IMPORTANT]
> Do NOT hardcode the base URL inside ViewModels or UI components. Centralize API configuration inside Retrofit / OkHttp repository modules.

---

## 2. Authentication & Headers

Protected endpoints require a valid JWT bearer token in the HTTP Authorization header:

```http
Authorization: Bearer <JWT_ACCESS_TOKEN>
Content-Type: application/json
```

### Auth Handling Rules
- **Login Token**: Returned upon successful authentication at `POST /login`.
- **Token Storage**: Android client must store the token using secure encrypted storage (e.g. `EncryptedSharedPreferences` / `EncryptedDataStore`).
- **HTTP 401 Unauthorized**: If an API request returns HTTP 401, the Android app must clear the local session and redirect the user to the Login screen.

---

## 3. Data Representation & Time Units

- **Minutes from Midnight**: All time fields (`day_start`, `day_end`, `fixed_start`, `fixed_end`, `planned_start`, `planned_end`, `start`, `end`, `duration`) are represented as integer **minutes from midnight** (`0` to `1439`).
  - Example: `08:00` = `480`
  - Example: `09:30` = `570`
  - Example: `23:00` = `1380`
- **Timestamps**: `created_at` fields use ISO-8601 strings (e.g. `"2026-09-15T14:18:23"`).

---

## 4. REST API Endpoint Reference

### Public Endpoints

#### 1. `GET /health`
- **Purpose**: Lightweight connectivity health check.
- **Auth**: None.
- **Response `200 OK`**:
  ```json
  {
    "status": "ok"
  }
  ```

#### 2. `GET /`
- **Purpose**: Root status check.
- **Auth**: None.
- **Response `200 OK`**:
  ```json
  {
    "status": "Backend running ✅"
  }
  ```

#### 3. `POST /signup`
- **Purpose**: Register a new user account.
- **Auth**: None.
- **Request Body**:
  ```json
  {
    "username": "johndoe",
    "email": "johndoe@example.com",
    "password": "SecurePassword123!"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "message": "User created successfully ✅"
  }
  ```
- **Errors**: `400 Bad Request` if username or email is already registered; `422` if validation fails (e.g., password < 6 chars).

#### 4. `POST /login`
- **Purpose**: Authenticate user and issue JWT token.
- **Auth**: None.
- **Request Body**:
  ```json
  {
    "username": "johndoe",
    "password": "SecurePassword123!"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "bearer",
    "username": "johndoe"
  }
  ```
- **Errors**: `401 Unauthorized` (`"Invalid username or password"`) on wrong credentials.

---

### Protected Endpoints (Requires Bearer Token)

#### 5. `GET /tasks`
- **Purpose**: Retrieve authenticated user's saved tasks.
- **Auth**: Bearer Token.
- **Response `200 OK`**:
  ```json
  [
    {
      "id": 1,
      "user_id": 3,
      "task_name": "Math Homework",
      "duration": 45,
      "is_fixed": false,
      "fixed_start": null,
      "fixed_end": null
    },
    {
      "id": 2,
      "user_id": 3,
      "task_name": "Team Standup",
      "duration": 30,
      "is_fixed": true,
      "fixed_start": 600,
      "fixed_end": 630
    }
  ]
  ```

#### 6. `POST /tasks`
- **Purpose**: Create a new saved task for the authenticated user.
- **Auth**: Bearer Token.
- **Request Body**:
  ```json
  {
    "task_name": "Physics Revision",
    "duration": 60,
    "is_fixed": false
  }
  ```
- **Response `201 Created`**:
  ```json
  {
    "id": 3,
    "user_id": 3,
    "task_name": "Physics Revision",
    "duration": 60,
    "is_fixed": false,
    "fixed_start": null,
    "fixed_end": null
  }
  ```

#### 7. `PUT /tasks/{task_id}`
- **Purpose**: Update an existing saved task owned by the authenticated user.
- **Auth**: Bearer Token.
- **Request Body**:
  ```json
  {
    "duration": 75
  }
  ```
- **Response `200 OK`**: Updated `TaskResponse` JSON object.
- **Errors**: `404 Not Found` if task ID does not exist or belongs to another user.

#### 8. `DELETE /tasks/{task_id}`
- **Purpose**: Delete a saved task owned by the authenticated user.
- **Auth**: Bearer Token.
- **Response `200 OK`**: `{"message": "Task deleted successfully"}`.
- **Errors**: `404 Not Found` if task ID does not exist or belongs to another user.

#### 9. `POST /schedule`
- **Purpose**: Generate an optimized daily schedule and automatically persist snapshot.
- **Auth**: Bearer Token.
- **Request Body**:
  ```json
  {
    "day_start": 540,
    "day_end": 1380,
    "title": "Daily Focus Plan",
    "save_to_history": true,
    "tasks": [
      {
        "task_name": "Physics Revision",
        "duration": 60,
        "is_fixed": false
      },
      {
        "task_name": "Team Standup",
        "duration": 30,
        "is_fixed": true,
        "fixed_start": 600,
        "fixed_end": 630
      }
    ]
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "schedule_id": 15,
    "schedule": [
      {
        "start": 540,
        "end": 600,
        "title": "Physics Revision",
        "category": "task",
        "formatted": "09:00 - 10:00 | Task: Physics Revision"
      },
      {
        "start": 600,
        "end": 630,
        "title": "Team Standup",
        "category": "fixed_task",
        "formatted": "10:00 - 10:30 | Task: Team Standup (Fixed)"
      },
      {
        "start": 630,
        "end": 640,
        "title": "Short Break",
        "category": "break",
        "formatted": "10:30 - 10:40 | Short Break"
      },
      {
        "start": 640,
        "end": 1380,
        "title": "Free / Buffer Time",
        "category": "free",
        "formatted": "10:40 - 23:00 | Free / Buffer Time"
      }
    ],
    "warnings": []
  }
  ```
- **Errors**: `400 Bad Request` if fixed tasks overlap (e.g. `{"detail": "Fixed tasks overlap: Meeting A and Meeting B"}`); `422` if validation fails.

#### 10. `GET /schedules`
- **Purpose**: Retrieve list of saved daily schedule histories for authenticated user.
- **Auth**: Bearer Token.
- **Response `200 OK`**: Array of `SavedScheduleResponse` objects.

#### 11. `GET /schedules/{schedule_id}`
- **Purpose**: Retrieve specific saved schedule history by ID.
- **Auth**: Bearer Token.
- **Response `200 OK`**: Single `SavedScheduleResponse` object.
- **Errors**: `404 Not Found` if schedule does not exist or belongs to another user.

#### 12. `DELETE /schedules/{schedule_id}`
- **Purpose**: Delete a saved schedule snapshot.
- **Auth**: Bearer Token.
- **Response `200 OK`**: `{"message": "Schedule deleted successfully"}`.

#### 13. `POST /feedback`
- **Purpose**: Submit actual task completion feedback to train the ML model server-side.
- **Auth**: Bearer Token.
- **Request Body**:
  ```json
  {
    "task_name": "Physics Revision",
    "planned_start": 540,
    "planned_end": 600,
    "planned_duration": 60,
    "actual_duration": 55,
    "completed": "yes",
    "feedback": "completed slightly early"
  }
  ```
- **Response `200 OK`**: `{"message": "Feedback saved ✅"}`.

---

## 5. HTTP Status Codes

| Code | Meaning | Client Handling Strategy |
| :--- | :--- | :--- |
| **`200`** | OK | Parse response JSON |
| **`201`** | Created | Task creation successful |
| **`400`** | Bad Request | Display error message in UI (e.g. fixed task conflict) |
| **`401`** | Unauthorized | Clear stored JWT token and redirect user to Login |
| **`404`** | Not Found | Resource not found or belongs to another user |
| **`422`** | Validation Error | Input invalid; highlight error field |
| **`500`** | Internal Error | Server error; show generic error message |

---

## 6. Backend API Status Statement

> [!NOTE]
> **API CONTRACT FROZEN**: The backend REST API contract defined in this document is complete, hardened, and **FROZEN** for native Android development in Phase 5 onward.
