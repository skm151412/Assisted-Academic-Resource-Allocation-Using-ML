export default function TimeSlotPicker({ startTime, endTime, onChange }) {
  return (
    <div className="form-grid">
      <div className="form-row">
        <label htmlFor="startTime">Start Time</label>
        <input
          id="startTime"
          type="time"
          className="input"
          value={startTime}
          onChange={(event) => onChange(event.target.value, endTime)}
        />
      </div>
      <div className="form-row">
        <label htmlFor="endTime">End Time</label>
        <input
          id="endTime"
          type="time"
          className="input"
          value={endTime}
          onChange={(event) => onChange(startTime, event.target.value)}
        />
      </div>
    </div>
  );
}
