import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import allocationApi from "../services/allocationApi";
import bookingApi from "../services/bookingApi";
import useAuth from "../hooks/useAuth";
import usePolling from "../hooks/usePolling";
import { getErrorMessage } from "../services/apiClient";
import "./FacultyPanel.css";

export default function FacultyDashboardPage() {
  const { user } = useAuth();
  const [allocations, setAllocations] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [error, setError] = useState("");

  const loadDashboard = async () => {
    if (!user?.id) {
      return;
    }
    try {
      const [allocationData, bookingData] = await Promise.all([
        allocationApi.listFacultyAllocations(user.id),
        bookingApi.listUserBookings(user.id),
      ]);
      setAllocations(allocationData);
      setBookings(bookingData);
      setError("");
    } catch (err) {
      setError(getErrorMessage(err, "Failed to load faculty dashboard"));
    }
  };

  usePolling(loadDashboard, 5000);

  const todayLabel = new Intl.DateTimeFormat("en-US", { weekday: "long" }).format(new Date());

  const stats = useMemo(() => {
    const pending = bookings.filter((booking) => booking.status === "PENDING").length;
    const approved = bookings.filter((booking) => booking.status === "APPROVED").length;
    const todayClasses = allocations.filter((allocation) => allocation.day === todayLabel).length;
    const nextClass = allocations.find((allocation) => allocation.day === todayLabel) || null;

    return { pending, approved, todayClasses, nextClass };
  }, [allocations, bookings, todayLabel]);

  const notifications = useMemo(() => {
    const items = [];
    if (stats.pending) {
      items.push(`${stats.pending} room request(s) are still pending approval.`);
    }
    if (stats.approved) {
      items.push(`${stats.approved} request(s) have been approved.`);
    }
    if (stats.nextClass) {
      items.push(`Next class today: ${stats.nextClass.subject} for ${stats.nextClass.section} at ${stats.nextClass.time}.`);
    }
    if (!items.length) {
      items.push("No new alerts right now.");
    }
    return items;
  }, [stats]);

  return (
    <div className="page faculty-page faculty-page--dashboard">
      <div className="faculty-page__header">
        <div>
          <h2>Faculty Dashboard</h2>
          <p>See your classes, room requests, and quick alerts in one place.</p>
        </div>
      </div>

      {error ? <p className="message message--error">{error}</p> : null}

      <div className="faculty-summary-grid">
        <div className="card faculty-summary-card">
          <span className="faculty-summary-card__label">Today&apos;s Classes</span>
          <strong>{stats.todayClasses}</strong>
        </div>
        <div className="card faculty-summary-card">
          <span className="faculty-summary-card__label">Pending Requests</span>
          <strong>{stats.pending}</strong>
        </div>
        <div className="card faculty-summary-card">
          <span className="faculty-summary-card__label">Approved Requests</span>
          <strong>{stats.approved}</strong>
        </div>
      </div>

      <div className="faculty-dashboard-layout">
        <div className="faculty-dashboard-main">
          <div className="card faculty-card">
            <h3>Notifications</h3>
            <div className="faculty-notification-list">
              {notifications.map((item) => (
                <div className="faculty-notification-item" key={item}>{item}</div>
              ))}
            </div>
          </div>

          <div className="card faculty-card">
            <h3>Profile Snapshot</h3>
            <div className="faculty-profile-summary">
              <p><strong>Name:</strong> {user?.fullName || "Faculty"}</p>
              <p><strong>Department:</strong> {user?.department || "Not set"}</p>
              <p><strong>Subjects:</strong> {user?.subjectsHandled || "Not set"}</p>
            </div>
          </div>
        </div>

        <aside className="faculty-dashboard-side">
          <h3>Today Overview</h3>
          <div className="faculty-side-panel">
            {stats.nextClass ? (
              <>
                <p><strong>Upcoming Class</strong></p>
                <p>{stats.nextClass.subject}</p>
                <p>{stats.nextClass.section} | {stats.nextClass.time}</p>
              </>
            ) : (
              <p>No class scheduled for today.</p>
            )}
          </div>

          <h3>Quick Actions</h3>
          <div className="faculty-side-actions">
            <Link className="btn btn--primary" to="/request-booking">Request Room</Link>
            <Link className="btn btn--ghost" to="/my-timetable">Open Timetable</Link>
            <Link className="btn btn--ghost" to="/my-requests">Track Requests</Link>
          </div>
        </aside>
      </div>
    </div>
  );
}
