import { useMemo, useState } from "react";
import bookingApi from "../services/bookingApi";
import resourceApi from "../services/resourceApi";
import useAuth from "../hooks/useAuth";
import usePolling from "../hooks/usePolling";
import BookingForm from "../components/BookingForm";
import { getErrorMessage } from "../services/apiClient";
import "./BookingPage.css";

export default function BookingPage() {
  const { user, role } = useAuth();
  const [resources, setResources] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const loadPageData = async () => {
    try {
      const [resourceData, bookingData] = await Promise.all([
        resourceApi.listResources(),
        bookingApi.listBookings(),
      ]);
      setResources(resourceData);
      setBookings(bookingData);
      setError("");
    } catch (err) {
      setError(getErrorMessage(err, "Failed to load booking data"));
    }
  };

  usePolling(loadPageData, 5000);

  const handleSubmit = async (payload) => {
    if (!user?.id) {
      setError("User not available");
      return;
    }
    try {
      await bookingApi.createBooking({
        userId: user.id,
        resourceId: payload.resourceId,
        startTime: payload.startTime,
        endTime: payload.endTime,
        purpose: payload.purpose,
      });
      setSuccess("Booking requested successfully");
      setError("");
      await loadPageData();
    } catch (err) {
      setError(getErrorMessage(err, "Booking request failed"));
      setSuccess("");
    }
  };

  const visibleBookings = useMemo(() => {
    if (role === "ADMIN") {
      return bookings;
    }

    return bookings.filter((booking) => booking.user?.id === user?.id);
  }, [bookings, role, user]);

  return (
    <div className="page booking-page">
      <h2>Booking</h2>
      {error ? <p className="message message--error">{error}</p> : null}
      {success ? <p className="message message--success">{success}</p> : null}
      <div className="card">
        <BookingForm
          resources={resources}
          onSubmit={handleSubmit}
          disabled={role !== "FACULTY"}
        />
      </div>
      <div className="card">
        <h3 style={{ marginTop: 0 }}>Recent Bookings</h3>
        {visibleBookings.length === 0 ? (
          <p className="message message--info">No bookings available right now.</p>
        ) : (
          <div className="bookings-list">
            {visibleBookings.map((booking) => (
              <div className="booking-item" key={booking.id}>
                <strong>{booking.resource?.resourceName || booking.resource?.resourceCode || "Resource"}</strong>
                <div>{booking.user?.fullName || booking.user?.username || "User"}</div>
                <div>
                  {booking.startTime} - {booking.endTime}
                </div>
                <div className="badge badge--primary">{booking.status}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
