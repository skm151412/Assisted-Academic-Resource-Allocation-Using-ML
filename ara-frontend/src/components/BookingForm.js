import { useState } from "react";
import CalendarView from "./CalendarView";
import TimeSlotPicker from "./TimeSlotPicker";
import "./BookingForm.css";

export default function BookingForm({ resources, onSubmit, disabled }) {
  const [resourceId, setResourceId] = useState("");
  const [date, setDate] = useState("");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [purpose, setPurpose] = useState("");
  const [error, setError] = useState("");

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (disabled) {
      setError("Only faculty can create bookings");
      return;
    }
    if (!resourceId || !date || !startTime || !endTime || !purpose.trim()) {
      setError("All fields are required");
      return;
    }

    const startIso = `${date}T${startTime}`;
    const endIso = `${date}T${endTime}`;
    const start = new Date(startIso);
    const end = new Date(endIso);

    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
      setError("Invalid time selection");
      return;
    }
    if (start >= end) {
      setError("End time must be after start time");
      return;
    }

    setError("");
    await onSubmit({
      resourceId: Number(resourceId),
      startTime: startIso,
      endTime: endIso,
      purpose: purpose.trim(),
    });
  };

  return (
    <form onSubmit={handleSubmit} className="form-grid booking-form">
      <div className="form-row">
        <label htmlFor="resource">Resource</label>
        <select
          id="resource"
          className="select"
          value={resourceId}
          onChange={(event) => setResourceId(event.target.value)}
        >
          <option value="">Select</option>
          {resources.map((resource) => (
            <option key={resource.id} value={resource.id}>
              {resource.resourceName || resource.resourceCode}
            </option>
          ))}
        </select>
      </div>

      <CalendarView value={date} onChange={setDate} />

      <TimeSlotPicker
        startTime={startTime}
        endTime={endTime}
        onChange={(nextStart, nextEnd) => {
          setStartTime(nextStart);
          setEndTime(nextEnd);
        }}
      />

      <div className="form-row">
        <label htmlFor="purpose">Purpose</label>
        <input
          id="purpose"
          type="text"
          className="input"
          value={purpose}
          onChange={(event) => setPurpose(event.target.value)}
        />
      </div>

      <div className="booking-form__actions">
        <button type="submit" className="btn btn--primary" disabled={disabled}>
          Request Booking
        </button>
        {error ? <p className="message message--error">{error}</p> : null}
      </div>
    </form>
  );
}
