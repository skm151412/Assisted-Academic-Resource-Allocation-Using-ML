import "./ApprovalDetailPanel.css";

export default function ApprovalDetailPanel({ booking, onApprove, onReject }) {
  if (!booking) {
    return <p className="message message--info">Select a booking to review</p>;
  }

  return (
    <div className="approval-detail">
      <h3>Approval Detail</h3>
      <p>Resource: {booking.resource?.resourceName || "Resource"}</p>
      <p>Requester: {booking.user?.fullName || "User"}</p>
      <p>
        Time: {booking.startTime} - {booking.endTime}
      </p>
      <p>Purpose: {booking.purpose}</p>
      <div className="approval-detail__actions">
        <button type="button" className="btn btn--primary" onClick={() => onApprove(booking.id)}>
          Approve
        </button>
        <button type="button" className="btn btn--danger" onClick={() => onReject(booking.id)}>
          Reject
        </button>
      </div>
    </div>
  );
}
