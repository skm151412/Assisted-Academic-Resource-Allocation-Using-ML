import "./ApprovalQueueTable.css";

export default function ApprovalQueueTable({ bookings, onSelect, selectedId }) {
  if (!bookings.length) {
    return <p className="message message--info">No pending approvals</p>;
  }

  return (
    <div className="table-wrap">
      <table className="table approval-table">
      <thead>
        <tr>
          <th>Resource</th>
          <th>Requester</th>
          <th>Time</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {bookings.map((booking) => (
          <tr
            key={booking.id}
            onClick={() => onSelect(booking)}
            aria-selected={selectedId === booking.id}
          >
            <td>{booking.resource?.resourceName || "Resource"}</td>
            <td>{booking.user?.fullName || "User"}</td>
            <td>
              {booking.startTime} - {booking.endTime}
            </td>
            <td>
              <span className="badge badge--warning">{booking.status}</span>
            </td>
          </tr>
        ))}
      </tbody>
      </table>
    </div>
  );
}
