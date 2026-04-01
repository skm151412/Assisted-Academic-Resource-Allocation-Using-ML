export default function CalendarView({ value, onChange }) {
  return (
    <div className="form-row">
      <label htmlFor="bookingDate">Date</label>
      <input
        id="bookingDate"
        type="date"
        className="input"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}
