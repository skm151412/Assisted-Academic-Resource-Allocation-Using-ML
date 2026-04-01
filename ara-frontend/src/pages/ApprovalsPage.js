import { useEffect, useMemo, useState } from "react";
import approvalApi from "../services/approvalApi";
import useAuth from "../hooks/useAuth";
import usePolling from "../hooks/usePolling";
import ApprovalQueueTable from "../components/ApprovalQueueTable";
import ApprovalDetailPanel from "../components/ApprovalDetailPanel";
import { getErrorMessage } from "../services/apiClient";
import "./ApprovalsPage.css";

export default function ApprovalsPage() {
  const { user } = useAuth();
  const [bookings, setBookings] = useState([]);
  const [selectedBooking, setSelectedBooking] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const fetchBookings = async () => {
    try {
      const data = await approvalApi.listApprovals();
      setBookings(data);
      setError("");
    } catch (err) {
      setError(getErrorMessage(err, "Failed to load approvals"));
      setBookings([]);
    }
  };

  usePolling(fetchBookings, 5000);

  useEffect(() => {
    if (!selectedBooking && bookings.length > 0) {
      setSelectedBooking(bookings[0]);
      return;
    }

    if (selectedBooking) {
      const refreshedBooking = bookings.find((booking) => booking.id === selectedBooking.id) || null;
      setSelectedBooking(refreshedBooking);
    }
  }, [bookings, selectedBooking]);

  const pendingBookings = useMemo(
    () => bookings.filter((booking) => booking.status === "PENDING"),
    [bookings]
  );

  const handleApprove = async (bookingId) => {
    if (!user?.id) {
      setError("Approver not available");
      return;
    }
    try {
      await approvalApi.approveBooking(bookingId, {
        approverId: user.id,
        remarks: "Approved",
      });
      setMessage("Booking approved");
      setError("");
      await fetchBookings();
    } catch (err) {
      setError(getErrorMessage(err, "Approve failed"));
      setMessage("");
    }
  };

  const handleReject = async (bookingId) => {
    if (!user?.id) {
      setError("Approver not available");
      return;
    }
    try {
      await approvalApi.rejectBooking(bookingId, {
        approverId: user.id,
        remarks: "Rejected",
      });
      setMessage("Booking rejected");
      setError("");
      await fetchBookings();
    } catch (err) {
      setError(getErrorMessage(err, "Reject failed"));
      setMessage("");
    }
  };

  return (
    <div className="page">
      <h2>Approvals</h2>
      {error ? <p className="message message--error">{error}</p> : null}
      {message ? <p className="message message--success">{message}</p> : null}
      <div className="approvals-layout">
        <div className="card">
          <ApprovalQueueTable
            bookings={pendingBookings}
            onSelect={setSelectedBooking}
            selectedId={selectedBooking?.id || null}
          />
        </div>
        <div className="card">
          <ApprovalDetailPanel
            booking={selectedBooking}
            onApprove={handleApprove}
            onReject={handleReject}
          />
        </div>
      </div>
    </div>
  );
}
