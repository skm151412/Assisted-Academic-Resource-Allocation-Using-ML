import { useMemo, useState } from "react";
import bookingApi from "../services/bookingApi";
import resourceApi from "../services/resourceApi";
import useAuth from "../hooks/useAuth";
import { getErrorMessage } from "../services/apiClient";
import "./FacultyPanel.css";
import "./RequestRoomPage.css";

const DEFAULT_DATE_RANGE_DAYS = 45;
const TIME_PATTERN_12H = /^(0?[1-9]|1[0-2]):([0-5]\d)$/;

function toDateValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function buildDateOptions(days = DEFAULT_DATE_RANGE_DAYS) {
  const formatter = new Intl.DateTimeFormat("en-US", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric",
  });

  return Array.from({ length: days }, (_, index) => {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() + index);

    return {
      value: toDateValue(date),
      label: `${index === 0 ? "Today" : index === 1 ? "Tomorrow" : formatter.format(date)}`,
    };
  });
}

function isValidTime(value) {
  return TIME_PATTERN_12H.test(value);
}

function normalizeTime(value) {
  return value.replace(/\s+/g, "").replace(".", ":");
}

function to24HourTime(value, meridiem) {
  if (!isValidTime(value)) {
    return "";
  }

  const [hourText, minuteText] = value.split(":");
  let hour = Number(hourText);

  if (meridiem === "AM") {
    if (hour === 12) {
      hour = 0;
    }
  } else if (hour !== 12) {
    hour += 12;
  }

  return `${String(hour).padStart(2, "0")}:${minuteText}`;
}

export default function RequestRoomPage() {
  const { user } = useAuth();
  const dateOptions = useMemo(() => buildDateOptions(), []);
  const [form, setForm] = useState({
    date: "",
    startTime: "",
    startMeridiem: "AM",
    endTime: "",
    endMeridiem: "AM",
    resourceType: "",
    resourceId: "",
    purpose: "",
  });
  const [freeRooms, setFreeRooms] = useState([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const requestParams = useMemo(() => {
    if (!form.date || !form.startTime || !form.endTime || !isValidTime(form.startTime) || !isValidTime(form.endTime)) {
      return null;
    }

    const start24Hour = to24HourTime(form.startTime, form.startMeridiem);
    const end24Hour = to24HourTime(form.endTime, form.endMeridiem);

    if (!start24Hour || !end24Hour) {
      return null;
    }

    return {
      startTime: `${form.date}T${start24Hour}`,
      endTime: `${form.date}T${end24Hour}`,
      resourceType: form.resourceType || undefined,
    };
  }, [form]);

  const hasInvalidTimeRange = useMemo(() => {
    if (!isValidTime(form.startTime) || !isValidTime(form.endTime)) {
      return false;
    }

    const start24Hour = to24HourTime(form.startTime, form.startMeridiem);
    const end24Hour = to24HourTime(form.endTime, form.endMeridiem);
    if (!start24Hour || !end24Hour) {
      return false;
    }

    return start24Hour >= end24Hour;
  }, [form.startTime, form.endTime, form.startMeridiem, form.endMeridiem]);

  const hasInvalidTimeFormat = useMemo(() => {
    const hasValue = form.startTime || form.endTime;
    if (!hasValue) {
      return false;
    }
    if (form.startTime && !isValidTime(form.startTime)) {
      return true;
    }
    if (form.endTime && !isValidTime(form.endTime)) {
      return true;
    }
    return false;
  }, [form.startTime, form.endTime]);

  const findFreeRooms = async () => {
    if (!requestParams) {
      setError("Select date and enter valid time in HH:MM format");
      return;
    }
    if (hasInvalidTimeRange) {
      setError("End time must be after start time");
      return;
    }

    try {
      const data = await resourceApi.listFreeResources(requestParams);
      setFreeRooms(data);
      setForm((current) => ({ ...current, resourceId: data[0]?.id ? String(data[0].id) : "" }));
      setError("");
      setMessage(`${data.length} room(s) are available. Choose one and send the request.`);
    } catch (err) {
      setFreeRooms([]);
      setForm((current) => ({ ...current, resourceId: "" }));
      setError(getErrorMessage(err, "Unable to find free rooms"));
      setMessage("");
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!user?.id) {
      setError("Faculty user is not available");
      return;
    }
    if (!form.resourceId || !requestParams || !form.purpose.trim()) {
      setError("Find a room and fill the purpose before submitting");
      return;
    }
    if (hasInvalidTimeRange) {
      setError("End time must be after start time");
      return;
    }

    setLoading(true);
    try {
      await bookingApi.createBooking({
        userId: user.id,
        resourceId: Number(form.resourceId),
        startTime: requestParams.startTime,
        endTime: requestParams.endTime,
        purpose: form.purpose.trim(),
      });
      setMessage("Room request submitted successfully.");
      setError("");
      setForm({
        date: "",
        startTime: "",
        startMeridiem: "AM",
        endTime: "",
        endMeridiem: "AM",
        resourceType: "",
        resourceId: "",
        purpose: "",
      });
      setFreeRooms([]);
      setTimeout(() => setMessage(""), 5000); // Auto-hide toast after 5s
    } catch (err) {
      setError(getErrorMessage(err, "Room request failed"));
      setMessage("");
      setTimeout(() => setError(""), 5000);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page faculty-page faculty-page--request">
      <div className="faculty-page__header">
        <div>
          <h2>Request Booking</h2>
          <p>Pick a date and time, load only the free rooms, then submit your request.</p>
        </div>
      </div>

      <div className={`toast toast--error ${error ? "toast--show" : ""}`}>
        <span className="toast__icon">
          <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
        </span> 
        {error}
      </div>
      <div className={`toast toast--success ${message ? "toast--show" : ""}`}>
        <span className="toast__icon">
          <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>
        </span> 
        {message}
      </div>

      <div className="request-layout">
        <form className="card faculty-card form-grid" onSubmit={handleSubmit} noValidate>
          <div className="request-booking-grid">
            <div className="form-row">
              <label htmlFor="requestDate">Date</label>
              <select
                id="requestDate"
                className={`select ${!form.date && error ? "input--error" : ""}`}
                value={form.date}
                onChange={(event) => setForm((current) => ({ ...current, date: event.target.value }))}
              >
                <option value="">Select date</option>
                {dateOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <label htmlFor="requestStart">Start Time</label>
              <div className="request-time-field">
                <input
                  id="requestStart"
                  type="text"
                  className={`input ${!isValidTime(form.startTime) && error ? "input--error" : ""}`}
                  inputMode="numeric"
                  placeholder="HH:MM"
                  maxLength={5}
                  value={form.startTime}
                  onChange={(event) => setForm((current) => ({ ...current, startTime: normalizeTime(event.target.value) }))}
                />
                <select
                  className="select request-time-field__ampm"
                  value={form.startMeridiem}
                  onChange={(event) => setForm((current) => ({ ...current, startMeridiem: event.target.value }))}
                >
                  <option value="AM">AM</option>
                  <option value="PM">PM</option>
                </select>
              </div>
            </div>
            <div className="form-row">
              <label htmlFor="requestEnd">End Time</label>
              <div className="request-time-field">
                <input
                  id="requestEnd"
                  type="text"
                  className={`input ${!isValidTime(form.endTime) && error ? "input--error" : ""}`}
                  inputMode="numeric"
                  placeholder="HH:MM"
                  maxLength={5}
                  value={form.endTime}
                  onChange={(event) => setForm((current) => ({ ...current, endTime: normalizeTime(event.target.value) }))}
                />
                <select
                  className="select request-time-field__ampm"
                  value={form.endMeridiem}
                  onChange={(event) => setForm((current) => ({ ...current, endMeridiem: event.target.value }))}
                >
                  <option value="AM">AM</option>
                  <option value="PM">PM</option>
                </select>
              </div>
            </div>
          </div>

          {hasInvalidTimeFormat ? <p className="message message--error">Use 12-hour format HH:MM with AM/PM (example: 09:30 AM or 02:15 PM)</p> : null}
          {hasInvalidTimeRange ? <p className="message message--error">End time must be after start time</p> : null}
          <div className="form-row">
            <label htmlFor="requestType">Room Type</label>
            <select
              id="requestType"
              className="select"
              value={form.resourceType}
              onChange={(event) => setForm((current) => ({ ...current, resourceType: event.target.value }))}
            >
              <option value="">All</option>
              <option value="CLASSROOM">Classroom</option>
              <option value="LAB">Lab</option>
            </select>
          </div>
          <div className="form-row">
            <label htmlFor="requestRoom">Free Room</label>
            <select
              id="requestRoom"
              className={`select ${!form.resourceId && error ? "input--error" : ""}`}
              value={form.resourceId}
              onFocus={() => {
                if (!freeRooms.length && requestParams) {
                  findFreeRooms();
                }
              }}
              onChange={(event) => setForm((current) => ({ ...current, resourceId: event.target.value }))}
            >
              <option value="">Select a room</option>
              {freeRooms.map((room) => (
                <option key={room.id} value={room.id}>
                  {room.resourceCode} - {room.resourceName}
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <label htmlFor="requestPurpose">Purpose</label>
            <input
              id="requestPurpose"
              type="text"
              className={`input ${!form.purpose.trim() && error ? "input--error" : ""}`}
              value={form.purpose}
              onChange={(event) => setForm((current) => ({ ...current, purpose: event.target.value }))}
              placeholder="Seminar, extra class, lab session, exam..."
            />
          </div>
          <div className="faculty-page__actions">
            <button type="button" className="btn btn--secondary" onClick={findFreeRooms}>
              Find Free Rooms
            </button>
            <button type="submit" className="btn btn--primary btn--loading" disabled={loading}>
              {loading ? (
                <>
                  <span className="spinner-icon"></span> Submitting...
                </>
              ) : (
                "Submit Request"
              )}
            </button>
          </div>
        </form>

        <aside className="card request-side-card">
          <h3>Request Tips</h3>
          <ul className="request-side-list">
            <li>Choose date and time first.</li>
            <li>Click Find Free Rooms to load available options.</li>
            <li>Use short, specific purpose text.</li>
            <li>Submit and track status from My Requests.</li>
          </ul>
        </aside>
      </div>
    </div>
  );
}
