import { useMemo, useState } from "react";
import bookingApi from "../services/bookingApi";
import resourceApi from "../services/resourceApi";
import useAuth from "../hooks/useAuth";
import { getErrorMessage } from "../services/apiClient";
import "./FacultyPanel.css";

export default function RequestRoomPage() {
  const { user } = useAuth();
  const [form, setForm] = useState({
    date: "",
    startTime: "",
    endTime: "",
    resourceType: "",
    resourceId: "",
    purpose: "",
  });
  const [freeRooms, setFreeRooms] = useState([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const requestParams = useMemo(() => {
    if (!form.date || !form.startTime || !form.endTime) {
      return null;
    }

    return {
      startTime: `${form.date}T${form.startTime}`,
      endTime: `${form.date}T${form.endTime}`,
      resourceType: form.resourceType || undefined,
    };
  }, [form]);

  const findFreeRooms = async () => {
    if (!requestParams) {
      setError("Select date, start time, and end time first");
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
        endTime: "",
        resourceType: "",
        resourceId: "",
        purpose: "",
      });
      setFreeRooms([]);
    } catch (err) {
      setError(getErrorMessage(err, "Room request failed"));
      setMessage("");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page faculty-page">
      <div className="faculty-page__header">
        <div>
          <h2>Request Booking</h2>
          <p>Pick a date and time, load only the free rooms, then submit your request.</p>
        </div>
      </div>

      {error ? <p className="message message--error">{error}</p> : null}
      {message ? <p className="message message--info">{message}</p> : null}

      <form className="card faculty-card form-grid" onSubmit={handleSubmit}>
        <div className="form-row">
          <label htmlFor="requestDate">Date</label>
          <input
            id="requestDate"
            type="date"
            className="input"
            value={form.date}
            onChange={(event) => setForm((current) => ({ ...current, date: event.target.value }))}
          />
        </div>
        <div className="form-row">
          <label htmlFor="requestStart">Start Time</label>
          <input
            id="requestStart"
            type="time"
            className="input"
            value={form.startTime}
            onChange={(event) => setForm((current) => ({ ...current, startTime: event.target.value }))}
          />
        </div>
        <div className="form-row">
          <label htmlFor="requestEnd">End Time</label>
          <input
            id="requestEnd"
            type="time"
            className="input"
            value={form.endTime}
            onChange={(event) => setForm((current) => ({ ...current, endTime: event.target.value }))}
          />
        </div>
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
            className="select"
            value={form.resourceId}
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
            className="input"
            value={form.purpose}
            onChange={(event) => setForm((current) => ({ ...current, purpose: event.target.value }))}
            placeholder="Seminar, extra class, lab session, exam..."
          />
        </div>
        <div className="faculty-page__actions">
          <button type="button" className="btn btn--ghost" onClick={findFreeRooms}>
            Find Free Rooms
          </button>
          <button type="submit" className="btn btn--primary" disabled={loading}>
            {loading ? "Submitting..." : "Submit Request"}
          </button>
        </div>
      </form>
    </div>
  );
}
