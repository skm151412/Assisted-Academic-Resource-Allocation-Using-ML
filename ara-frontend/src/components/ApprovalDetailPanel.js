import { formatDateTime } from "../utils/formatDate";
import "./ApprovalDetailPanel.css";

export default function ApprovalDetailPanel({ booking, onApprove, onReject, isApproving, isRejecting }) {
  if (!booking) {
    return (
      <div className="card approval-detail-empty">
        <h4>Select a request</h4>
        <p>Click on any pending request in the list to review details here.</p>
      </div>
    );
  }

  return (
    <div className="card approval-detail">
      <div className="approval-detail__header">
        <h3>Review Request</h3>
        <span className="badge badge--warning">{booking.status}</span>
      </div>

      <div className="approval-detail__content">
        <div className="detail-box">
          <span className="detail-label">Resource</span>
          <span className="detail-value">{booking.resource?.resourceName || "Resource"}</span>
        </div>
        <div className="detail-box">
          <span className="detail-label">Requester</span>
          <span className="detail-value">{booking.user?.fullName || "User"}</span>
        </div>
        <div className="detail-box">
          <span className="detail-label">Start Time</span>
          <span className="detail-value">{formatDateTime(booking.startTime)}</span>
        </div>
        <div className="detail-box">
          <span className="detail-label">End Time</span>
          <span className="detail-value">{formatDateTime(booking.endTime)}</span>
        </div>
        <div className="detail-box detail-box--full">
          <span className="detail-label">Purpose</span>
          <div className="detail-text-box">{booking.purpose}</div>
        </div>
      </div>

      <div className="approval-detail__actions">
        <button 
          type="button" 
          className="btn btn--primary" 
          onClick={() => onApprove(booking.id)}
          disabled={isApproving || isRejecting}
        >
          {isApproving ? "Approving..." : "Approve Request"}
        </button>
        <button 
          type="button" 
          className="btn btn--danger" 
          onClick={() => onReject(booking.id)}
          disabled={isApproving || isRejecting}
        >
          {isRejecting ? "Rejecting..." : "Reject"}
        </button>
      </div>
    </div>
  );
}
