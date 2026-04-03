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

  const bookingSummary = useMemo(() => {
    const pending = visibleBookings.filter((booking) => booking.status === "PENDING").length;
    const approved = visibleBookings.filter((booking) => booking.status === "APPROVED").length;
    const rejected = visibleBookings.filter((booking) => booking.status === "REJECTED").length;
    return { pending, approved, rejected, total: visibleBookings.length };
  }, [visibleBookings]);

  return (
    <div className="page admin-page admin-page--booking booking-page">
      <h2>{role === "ADMIN" ? "Booking Monitor" : "Booking"}</h2>
      {error ? <p className="message message--error">{error}</p> : null}
      {success ? <p className="message message--success">{success}</p> : null}

      {role === "FACULTY" ? (
        <div className="card booking-page__request-card">
          <BookingForm
            resources={resources}
            onSubmit={handleSubmit}
            disabled={false}
          />
        </div>
      ) : (
        <div className="booking-page__monitor-note">
          <h3>Monitoring Mode Active</h3>
          <p>
            Admin users cannot create booking requests from this page. Use <strong>Approvals</strong> to review pending requests.
          </p>
        </div>
      )}

      <div className="booking-layout">
        <div className="card booking-main-card">
          <h3 className="booking-page__list-title">{role === "ADMIN" ? "All Booking Requests" : "My Recent Requests"}</h3>
          {visibleBookings.length === 0 ? (
            <p className="message message--info">
              {role === "ADMIN" ? "No booking requests available right now." : "No booking requests found yet."}
            </p>
          ) : (
            <div className="bookings-list">
              {visibleBookings.map((booking) => (
                  <div
                    className={`booking-item ${
                      booking.status === "APPROVED"
                        ? "booking-item--approved"
                        : booking.status === "REJECTED"
                        ? "booking-item--rejected"
                        : "booking-item--pending"
                    }`}
                    key={booking.id}
                  >
                  <strong className="booking-item__resource">{booking.resource?.resourceName || booking.resource?.resourceCode || "Resource"}</strong>
                  <div className="booking-item__requester">{booking.user?.fullName || booking.user?.username || "User"}</div>
                  <div className="booking-item__time">
                    {booking.startTime} - {booking.endTime}
                  </div>
                  <div className={
                    booking.status === "APPROVED"
                      ? "badge badge--success"
                      : booking.status === "REJECTED"
                      ? "badge badge--danger"
                      : "badge badge--warning"
                  }>
                    {booking.status}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <aside className="card booking-side-card">
          <h3>Summary</h3>
          <div className="booking-side-list">
            <p><strong>Total:</strong> {bookingSummary.total}</p>
            <p><strong>Pending:</strong> {bookingSummary.pending}</p>
            <p><strong>Approved:</strong> {bookingSummary.approved}</p>
            <p><strong>Rejected:</strong> {bookingSummary.rejected}</p>
          </div>
        </aside>
      </div>
    </div>
  );
}
