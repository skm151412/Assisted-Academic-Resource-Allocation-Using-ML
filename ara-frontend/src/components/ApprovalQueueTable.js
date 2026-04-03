import { formatDateTime } from "../utils/formatDate";
import "./ApprovalQueueTable.css";

export default function ApprovalQueueTable({ bookings, onSelect, selectedId, loading }) {
  if (loading) {
    return (
      <div className="table-wrap">
        <table className="table approval-table">
          <thead>
            <tr>
              <th>Resource</th><th>Requester</th><th>Time</th><th>Status</th>
            </tr>
          </thead>
          <tbody>
            {[...Array(3)].map((_, i) => (
              <tr key={i} className="skeleton-row">
                <td><div className="skeleton skeleton-text" /></td>
                <td><div className="skeleton skeleton-text" /></td>
                <td><div className="skeleton skeleton-text" /></td>
                <td><div className="skeleton skeleton-badge" /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  if (!bookings.length) {
    return (
      <div className="approvals-empty-state card">
        <h3>All caught up!</h3>
        <p>Your approval queue is completely clear. Great job!</p>
      </div>
    );
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
            className="clickable-row"
            tabIndex="0"
            role="button"
            aria-selected={selectedId === booking.id}
            onKeyDown={(e) => e.key === "Enter" && onSelect(booking)}
          >
            <td>{booking.resource?.resourceName || "Resource"}</td>
            <td>{booking.user?.fullName || "User"}</td>
            <td>
              {formatDateTime(booking.startTime)} - {formatDateTime(booking.endTime)}
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
