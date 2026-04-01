import { useEffect, useMemo, useState } from "react";
import bookingApi from "../services/bookingApi";
import useAuth from "../hooks/useAuth";
import usePolling from "../hooks/usePolling";
import { getErrorMessage } from "../services/apiClient";
import "./FacultyPanel.css";
import "./MyBookingsPage.css";

export default function MyBookingsPage() {
  const { user } = useAuth();
  const [bookings, setBookings] = useState([]);
  const [error, setError] = useState("");

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
    <div className="page faculty-page">
      <div className="faculty-page__header">
        <div>
          <h2>My Requests</h2>
          <p>Track every room request with live status updates.</p>
        </div>
      </div>
      {error ? <p className="message message--error">{error}</p> : null}
      {filteredBookings.length === 0 ? (
        <p className="message message--info">No requests yet</p>
      ) : (
        <div className="bookings-list faculty-card-list">
          {filteredBookings.map((booking) => {
            const statusClass =
              booking.status === "APPROVED"
                ? "badge badge--success"
                : booking.status === "REJECTED"
                ? "badge badge--danger"
                : "badge badge--warning";

            return (
              <div className="booking-item card faculty-card" key={booking.id}>
                <strong>{booking.resource?.resourceCode || booking.resource?.resourceName || "Resource"}</strong>
                <div>{booking.resource?.resourceName || "Resource"}</div>
                <div>{booking.startTime} - {booking.endTime}</div>
                <div>{booking.purpose}</div>
                <div className={statusClass}>{booking.status}</div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
