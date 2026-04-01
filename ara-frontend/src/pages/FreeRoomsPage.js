import { useMemo, useState } from "react";
import resourceApi from "../services/resourceApi";
import { getErrorMessage } from "../services/apiClient";
import "./FacultyPanel.css";

export default function FreeRoomsPage() {
  const [filters, setFilters] = useState({
    date: "",
    startTime: "",
    endTime: "",
    resourceType: "",
  });
  const [rooms, setRooms] = useState([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const requestParams = useMemo(() => {
    if (!filters.date || !filters.startTime || !filters.endTime) {
      return null;
    }

    return {
      startTime: `${filters.date}T${filters.startTime}`,
      endTime: `${filters.date}T${filters.endTime}`,
      resourceType: filters.resourceType || undefined,
    };
  }, [filters]);

  const searchFreeRooms = async () => {
    if (!requestParams) {
      setError("Select date, start time, and end time first");
      return;
    }
    setLoading(true);
    try {
      const data = await resourceApi.listFreeResources(requestParams);
      setRooms(data);
      setError("");
      setMessage(`${data.length} room(s) are free for the selected slot.`);
    } catch (err) {
      setRooms([]);
      setError(getErrorMessage(err, "Failed to load free rooms"));
      setMessage("");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page faculty-page">
      <div className="faculty-page__header">
        <div>
          <h2>Free Rooms</h2>
          <p>Check exactly which rooms are free before sending an extra-class or lab request.</p>
        </div>
        <button type="button" className="btn btn--primary" onClick={searchFreeRooms} disabled={loading}>
          {loading ? "Checking..." : "Check Free Rooms"}
        </button>
      </div>

      <div className="card faculty-card">
        <div className="form-grid">
          <div className="form-row">
            <label htmlFor="freeRoomDate">Date</label>
            <input
              id="freeRoomDate"
              type="date"
              className="input"
              value={filters.date}
              onChange={(event) => setFilters((current) => ({ ...current, date: event.target.value }))}
            />
          </div>
          <div className="form-row">
            <label htmlFor="freeRoomStart">Start Time</label>
            <input
              id="freeRoomStart"
              type="time"
              className="input"
              value={filters.startTime}
              onChange={(event) => setFilters((current) => ({ ...current, startTime: event.target.value }))}
            />
          </div>
          <div className="form-row">
            <label htmlFor="freeRoomEnd">End Time</label>
            <input
              id="freeRoomEnd"
              type="time"
              className="input"
              value={filters.endTime}
              onChange={(event) => setFilters((current) => ({ ...current, endTime: event.target.value }))}
            />
          </div>
          <div className="form-row">
            <label htmlFor="freeRoomType">Room Type</label>
            <select
              id="freeRoomType"
              className="select"
              value={filters.resourceType}
              onChange={(event) => setFilters((current) => ({ ...current, resourceType: event.target.value }))}
            >
              <option value="">All</option>
              <option value="CLASSROOM">Classroom</option>
              <option value="LAB">Lab</option>
            </select>
          </div>
        </div>
      </div>

      {error ? <p className="message message--error">{error}</p> : null}
      {message ? <p className="message message--info">{message}</p> : null}

      <div className="faculty-card-list">
        {rooms.length ? (
          rooms.map((room) => (
            <div className="card faculty-card" key={room.id}>
              <div className="faculty-card__header">
                <h3>{room.resourceCode}</h3>
                <span>{room.resourceType}</span>
              </div>
              <p><strong>Name:</strong> {room.resourceName}</p>
              <p><strong>Capacity:</strong> {room.capacity}</p>
              <p><strong>Location:</strong> {room.location}</p>
            </div>
          ))
        ) : (
          <p className="message message--info">Search with a date and time to see free rooms.</p>
        )}
      </div>
    </div>
  );
}
