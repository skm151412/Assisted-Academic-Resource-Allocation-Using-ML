import { useEffect, useMemo, useState } from "react";
import bookingApi from "../services/bookingApi";
import useAuth from "../hooks/useAuth";
import usePolling from "../hooks/usePolling";
import { getErrorMessage } from "../services/apiClient";
import { formatDateTime } from "../utils/formatDate";
import "./FacultyPanel.css";
import "./MyBookingsPage.css";

export default function MyBookingsPage() {
  const { user } = useAuth();
  const [bookings, setBookings] = useState([]);
  const [error, setError] = useState("");
  const [initialLoading, setInitialLoading] = useState(true);

  const fetchBookings = async () => {
    if (!user?.id) {
      return;
    }
    try {
      const data = await bookingApi.listUserBookings(user.id);
      setBookings(data);
      setError("");
    } catch (err) {
      setError(getErrorMessage(err, "Failed to load your requests"));
      setBookings([]);
    } finally {
      if (initialLoading) setInitialLoading(false);
    }
  };

  useEffect(() => {
    fetchBookings();
  }, [user]);

  usePolling(fetchBookings, 5000, { immediate: false });

  const filteredBookings = useMemo(() => {
    if (!user?.id) {
      return [];
    }
    return bookings;
  }, [bookings, user]);

  return (
    <div className="page faculty-page faculty-page--my-requests">
      <div className="faculty-page__header">
        <div>
          <h2>My Requests</h2>
          <p>Track every room request with live status updates.</p>
        </div>
      </div>
      {error ? <p className="message message--error">{error}</p> : null}
      
      {initialLoading ? (
        <div className="bookings-list loading-skeleton-wrapper">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="card faculty-card skeleton-box">
              <div className="skeleton skeleton-title" />
              <div className="skeleton skeleton-text" />
              <div className="skeleton skeleton-badge-lg" />
            </div>
          ))}
        </div>
      ) : filteredBookings.length === 0 ? (
        <div className="card empty-state-box">
          <h3>No requests yet</h3>
          <p>You haven't made any resource booking requests yet. When you do, they'll appear here.</p>
        </div>
      ) : (
        <div className="bookings-list faculty-card-list">
          {filteredBookings.map((booking) => {
            const statusClass =
              booking.status === "APPROVED"
                ? "badge badge--success"
                : booking.status === "REJECTED"
                ? "badge badge--danger"
                : "badge badge--warning";

            const statusCardClass =
              booking.status === "APPROVED"
                ? "booking-item booking-item--approved"
                : booking.status === "REJECTED"
                ? "booking-item booking-item--rejected"
                : "booking-item booking-item--pending";

            return (
              <div className={`${statusCardClass} card faculty-card`} key={booking.id}>
                <div className="booking-card-header">
                  <div>
                    <h3 className="booking-item__resource-code">
                      {booking.resource?.resourceCode || booking.resource?.resourceName || "Resource"}
                    </h3>
                    <div className="booking-item__resource-name">{booking.resource?.resourceName || "Resource"}</div>
                  </div>
                  <div className={`${statusClass} booking-item__status`}>{booking.status}</div>
                </div>
                
                <div className="booking-card-body">
                  <div className="booking-detail-row">
                    <span className="booking-detail-icon">
                      <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                    </span>
                    <span className="booking-item__slot">
                      {formatDateTime(booking.startTime)} - {formatDateTime(booking.endTime)}
                    </span>
                  </div>
                  <div className="booking-detail-row">
                    <span className="booking-detail-icon">
                      <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><line x1="17" y1="3" x2="21" y2="7"></line><path d="M21 7L7 21l-4 1 1-4L18 4z"></path></svg>
                    </span>
                    <span className="booking-item__purpose">{booking.purpose}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
