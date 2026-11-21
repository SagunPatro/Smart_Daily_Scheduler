const API_BASE = "http://127.0.0.1:8000";

let currentSchedule = [];

// ---------- TOKEN STORAGE ----------
function getToken() {
  return sessionStorage.getItem("access_token");
}

function setToken(token) {
  sessionStorage.setItem("access_token", token);
}

function clearToken() {
  sessionStorage.removeItem("access_token");
}

// Convert "HH:MM" -> minutes from midnight
function timeToMinutes(t) {
  if (!t) return 0;
  const parts = t.split(":");
  if (parts.length < 2) return 0;
  const h = Number(parts[0]);
  const m = Number(parts[1]);
  return h * 60 + m;
}

// Convert minutes from midnight -> "HH:MM"
function minutesToTime(m) {
  const h = Math.floor((m / 60) % 24);
  const mm = m % 60;
  return `${String(h).padStart(2, "0")}:${String(mm).padStart(2, "0")}`;
}

// Check session on load
window.addEventListener("DOMContentLoaded", () => {
  if (getToken()) {
    document.getElementById("landingPage").classList.add("hidden");
    document.getElementById("authSection").classList.add("hidden");
    document.getElementById("schedulerSection").classList.remove("hidden");
  }
});

// ---------- LOGOUT ----------
function logout() {
  clearToken();
  document.getElementById("schedulerSection").classList.add("hidden");
  document.getElementById("landingPage").classList.remove("hidden");
}

// ---------- ADD TASK ROW ----------
function addTask() {
  const container = document.getElementById("tasks");

  const row = document.createElement("div");
  row.className = "task-row";

  row.innerHTML = `
    <div class="task-inputs">
      <input placeholder="Task name (e.g. study)" class="task-name" />
      <input placeholder="Duration (min)" type="number" class="task-duration" style="width: 40%;" />
    </div>

    <div class="fixed-options">
      <label class="fixed-toggle-label">
        <input type="checkbox" class="fixed-toggle" onchange="toggleFixed(this)" /> Fixed Time
      </label>
      <input type="time" class="fixed-start hidden" title="Start Time" />
      <input type="time" class="fixed-end hidden" title="End Time" />
    </div>
  `;

  container.appendChild(row);
}

function toggleFixed(checkbox) {
  const row = checkbox.closest(".task-row");
  const start = row.querySelector(".fixed-start");
  const end = row.querySelector(".fixed-end");

  if (checkbox.checked) {
    start.classList.remove("hidden");
    end.classList.remove("hidden");
  } else {
    start.classList.add("hidden");
    end.classList.add("hidden");
  }
}

// ---------- SIGNUP ----------
async function signup() {
  const username = document.getElementById("suUsername").value.trim();
  const email = document.getElementById("suEmail").value.trim();
  const password = document.getElementById("suPassword").value.trim();

  if (!username || !email || !password) {
    alert("All signup fields are required!");
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/signup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, email, password })
    });

    const data = await res.json();
    if (!res.ok) {
      document.getElementById("authStatus").textContent = "❌ " + (data.detail || "Signup failed");
      return;
    }

    document.getElementById("authStatus").textContent = "✅ " + data.message;
    // Perform auto-login or prompt user to log in
    document.getElementById("liUsername").value = username;
    document.getElementById("liPassword").value = password;
    login();
  } catch (err) {
    alert("❌ Could not connect to backend");
  }
}

// ---------- LOGIN ----------
async function login() {
  const username = document.getElementById("liUsername").value.trim();
  const password = document.getElementById("liPassword").value.trim();

  if (!username || !password) {
    alert("Username and password required!");
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });

    const data = await res.json();
    if (!res.ok) {
      document.getElementById("authStatus").textContent = "❌ " + (data.detail || "Login failed");
      return;
    }

    if (data.access_token) {
      setToken(data.access_token);
      document.getElementById("authStatus").textContent = "✅ Welcome " + (data.username || username);
      document.getElementById("authSection").classList.add("hidden");
      document.getElementById("landingPage").classList.add("hidden");
      document.getElementById("schedulerSection").classList.remove("hidden");
    }
  } catch (err) {
    alert("❌ Could not connect to backend");
  }
}

// ---------- GENERATE SCHEDULE ----------
async function generateSchedule() {
  const token = getToken();
  if (!token) {
    alert("❌ Session expired. Please log in.");
    logout();
    return;
  }

  const dayStartVal = document.getElementById("dayStart").value;
  const dayEndVal = document.getElementById("dayEnd").value;

  const dayStart = timeToMinutes(dayStartVal);
  const dayEnd = timeToMinutes(dayEndVal);

  const taskRows = document.querySelectorAll(".task-row");
  const tasks = [];

  taskRows.forEach(row => {
    const name = row.querySelector(".task-name").value.trim();
    const duration = parseInt(row.querySelector(".task-duration").value);
    const isFixed = row.querySelector(".fixed-toggle").checked;

    if (!name || isNaN(duration)) return;

    if (isFixed) {
      const fixedStart = timeToMinutes(row.querySelector(".fixed-start").value);
      const fixedEnd = timeToMinutes(row.querySelector(".fixed-end").value);
      tasks.push({
        task_name: name,
        duration: duration,
        is_fixed: true,
        fixed_start: fixedStart,
        fixed_end: fixedEnd
      });
    } else {
      tasks.push({
        task_name: name,
        duration: duration,
        is_fixed: false
      });
    }
  });

  try {
    const res = await fetch(`${API_BASE}/schedule`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      body: JSON.stringify({
        day_start: dayStart,
        day_end: dayEnd,
        tasks: tasks
      })
    });

    if (res.status === 401) {
      alert("❌ Session expired or unauthorized. Please log in again.");
      logout();
      return;
    }

    const data = await res.json();

    if (!res.ok) {
      alert("❌ Schedule Error: " + (data.detail || "Failed to generate schedule"));
      return;
    }

    renderSchedule(data.schedule || []);
    renderWarnings(data.warnings || []);

  } catch (err) {
    alert("❌ Could not connect to backend");
  }
}

// ---------- RENDER SCHEDULE ----------
function renderSchedule(schedule) {
  currentSchedule = schedule;
  const output = document.getElementById("output");
  output.innerHTML = "";

  if (schedule.length === 0) {
    output.innerHTML = "<p>No items in schedule.</p>";
    return;
  }

  schedule.forEach(item => {
    const card = document.createElement("div");
    card.className = `schedule-card ${item.category || ''}`;
    card.innerHTML = `<strong>${item.formatted || (minutesToTime(item.start) + ' - ' + minutesToTime(item.end) + ' | ' + item.title)}</strong>`;
    output.appendChild(card);
  });

  renderFeedbackForms(schedule);
}

function renderWarnings(warnings) {
  const box = document.getElementById("warnings");
  if (!warnings || warnings.length === 0) {
    box.classList.add("hidden");
    box.innerHTML = "";
    return;
  }

  box.classList.remove("hidden");
  box.innerHTML = "<strong>⚠️ Warnings:</strong><br>" + warnings.join("<br>");
}

// ---------- FEEDBACK FORMS ----------
function renderFeedbackForms(schedule) {
  const container = document.getElementById("feedbackContainer");
  container.innerHTML = "";

  const taskItems = schedule.filter(item => item.category === "task" || item.category === "fixed_task");

  if (taskItems.length === 0) {
    container.innerHTML = "<p style='color: #666;'>No scheduled tasks available for feedback.</p>";
    return;
  }

  taskItems.forEach((item, idx) => {
    const plannedDuration = item.end - item.start;

    const div = document.createElement("div");
    div.className = "feedback-card";
    div.innerHTML = `
      <strong>${item.title}</strong> (${minutesToTime(item.start)} - ${minutesToTime(item.end)})<br>
      <div style="display:flex; gap:8px; margin-top:8px;">
        <input type="number" id="actualDur-${idx}" placeholder="Actual duration (min)" value="${plannedDuration}" />
        <select id="comp-${idx}">
          <option value="yes">Completed</option>
          <option value="no">Not Completed</option>
        </select>
      </div>
      <input id="fbText-${idx}" placeholder="Feedback (e.g. ok / needed more time)" />
      <button onclick="submitTaskFeedback(${idx})" style="padding:8px; font-size:12px; margin-top:4px;">Send Feedback</button>
    `;

    container.appendChild(div);
  });
}

// ---------- SUBMIT FEEDBACK ----------
async function submitTaskFeedback(index) {
  const token = getToken();
  if (!token) {
    alert("❌ Session expired. Please log in.");
    logout();
    return;
  }

  const taskItems = currentSchedule.filter(item => item.category === "task" || item.category === "fixed_task");
  const item = taskItems[index];

  const actualDuration = parseInt(document.getElementById(`actualDur-${index}`).value);
  const completed = document.getElementById(`comp-${index}`).value;
  const feedback = document.getElementById(`fbText-${index}`).value;

  if (isNaN(actualDuration)) {
    alert("Please enter actual duration in minutes");
    return;
  }

  const payload = {
    task_name: item.title,
    planned_start: item.start,
    planned_end: item.end,
    planned_duration: item.end - item.start,
    actual_duration: actualDuration,
    completed: completed,
    feedback: feedback
  };

  try {
    const res = await fetch(`${API_BASE}/feedback`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      body: JSON.stringify(payload)
    });

    if (res.status === 401) {
      alert("❌ Session expired or unauthorized. Please log in again.");
      logout();
      return;
    }

    const data = await res.json();
    if (res.ok) {
      alert(`✅ Feedback saved for "${item.title}"`);
    } else {
      alert("❌ " + (data.detail || "Feedback submission failed"));
    }
  } catch (err) {
    alert("❌ Feedback submission failed");
  }
}
