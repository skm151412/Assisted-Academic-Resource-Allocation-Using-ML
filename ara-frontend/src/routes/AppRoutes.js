import { useEffect, useState } from "react";
import { Navigate, Route, Routes, useNavigate } from "react-router-dom";
import ProtectedRoute from "./ProtectedRoute";
import ApprovalsPage from "../pages/ApprovalsPage";
import AnalyticsPage from "../pages/AnalyticsPage";
import BookingPage from "../pages/BookingPage";
import MyBookingsPage from "../pages/MyBookingsPage";
import UtilizationPage from "../pages/UtilizationPage";
import AutoAllocationPage from "../pages/AutoAllocationPage";
import FacultyDashboardPage from "../pages/FacultyDashboardPage";
import FacultyTimetablePage from "../pages/FacultyTimetablePage";
import FreeRoomsPage from "../pages/FreeRoomsPage";
import RequestRoomPage from "../pages/RequestRoomPage";
import ProfilePage from "../pages/ProfilePage";
import Navbar from "../components/Navbar";
import Sidebar from "../components/Sidebar";
import useAuth from "../hooks/useAuth";
import usePolling from "../hooks/usePolling";
import resourceApi from "../services/resourceApi";
import allocationApi from "../services/allocationApi";
import { getErrorMessage } from "../services/apiClient";
import "../pages/LoginPage.css";
import "../pages/DashboardPage.css";

const MANAGED_RESOURCE_ORDER = ["H1-01", "H1-02", "H1-03", "H1-04", "H1-17", "H1-18", "H1-19", "H1-22", "H1-23", "H1-25", "H1-26"];

function AdminDashboardPage() {
  const { user, role } = useAuth();
  const [stats, setStats] = useState({ 
    totalAllocations: 0, 
    roomUsagePercent: '0.0', 
    freeRooms: 0,
    managedRooms: 0,
  });
  const [statsError, setStatsError] = useState("");

  const loadStats = async () => {
    try {
      const data = await allocationApi.getStats();
      setStats(data);
      setStatsError("");
    } catch (err) {
      setStatsError(getErrorMessage(err, "Failed to load dashboard stats"));
    }
  };

  usePolling(loadStats, 5000);

  return (
    <div className="page">
      <h2>Dashboard</h2>
      <div className="card dashboard-card" style={{ marginBottom: "1rem" }}>
        <p>
          {user ? (
            <span>
              Welcome <span className="dashboard-highlight">{user.username || "User"}</span> ({role})
            </span>
          ) : (
            "Welcome"
          )}
        </p>
      </div>
      {statsError ? <p className="message message--error">{statsError}</p> : null}
      
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
        <div className="card">
          <h3 style={{ margin: '0 0 0.5rem 0', color: '#475569', fontSize: '1rem' }}>Total Allocations</h3>
          <div style={{ fontSize: '2rem', fontWeight: 'bold', color: '#0f172a' }}>{stats.totalAllocations}</div>
        </div>
        <div className="card">
          <h3 style={{ margin: '0 0 0.5rem 0', color: '#475569', fontSize: '1rem' }}>Room Usage</h3>
          <div style={{ fontSize: '2rem', fontWeight: 'bold', color: '#0f172a' }}>{stats.roomUsagePercent}%</div>
        </div>
        <div className="card">
          <h3 style={{ margin: '0 0 0.5rem 0', color: '#475569', fontSize: '1rem' }}>Free Rooms</h3>
          <div style={{ fontSize: '2rem', fontWeight: 'bold', color: '#0f172a' }}>{stats.freeRooms}</div>
          <div style={{ color: '#64748b' }}>Available out of {stats.managedRooms} managed H1 rooms</div>
        </div>
      </div>
    </div>
  );
}

function DashboardPage() {
  const { role } = useAuth();
  return role === "FACULTY" ? <FacultyDashboardPage /> : <AdminDashboardPage />;
}

function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const { login, isAuthenticated } = useAuth();

  const handleSubmit = async (event) => {
    event.preventDefault();
    try {
      await login({ username, password });
      setError("");
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, "Login failed"));
    }
  };

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="login-page">
      <div className="card login-card">
        <h2 className="login-title">Login</h2>
        <form onSubmit={handleSubmit} className="form-grid">
          <div className="form-row">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              type="text"
              className="input"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </div>
          <div className="form-row">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              className="input"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          <button type="submit" className="btn btn--primary">
            Login
          </button>
          {error ? <p className="message message--error">{error}</p> : null}
        </form>
      </div>
    </div>
  );
}

function ResourcesPage() {
  const { role } = useAuth();
  const [resources, setResources] = useState([]);
  const [form, setForm] = useState({
    resourceCode: "",
    resourceName: "",
    resourceType: "CLASSROOM",
    capacity: 0,
    location: "",
  });
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const loadResources = async () => {
    try {
      const data = await resourceApi.listResources();
      setResources(
        data
          .filter((resource) => resource.active)
          .sort(
            (left, right) =>
              MANAGED_RESOURCE_ORDER.indexOf(left.resourceCode) -
              MANAGED_RESOURCE_ORDER.indexOf(right.resourceCode)
          )
      );
      setError("");
    } catch (err) {
      setError(getErrorMessage(err, "Failed to load resources"));
    }
  };

  usePolling(loadResources, 5000);

  const onChange = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (role !== "ADMIN") {
      setError("Only admins can add resources");
      return;
    }
    try {
      await resourceApi.createResource({
        resourceCode: form.resourceCode.trim(),
        resourceName: form.resourceName.trim(),
        resourceType: form.resourceType,
        capacity: Number(form.capacity) || 0,
        location: form.location.trim(),
        active: true,
      });
      setMessage("Resource added");
      setError("");
      setForm({
        resourceCode: "",
        resourceName: "",
        resourceType: "CLASSROOM",
        capacity: 0,
        location: "",
      });
      await loadResources();
    } catch (err) {
      setError(getErrorMessage(err, "Failed to add resource"));
      setMessage("");
    }
  };

  return (
    <div className="page">
      <h2>Resources</h2>
      {error ? <p className="message message--error">{error}</p> : null}
      {message ? <p className="message message--success">{message}</p> : null}
      {role === "ADMIN" ? (
        <div className="card">
          <h3>Add Resource</h3>
          <form className="form-grid" onSubmit={handleSubmit}>
            <div className="form-row">
              <label htmlFor="resourceCode">Code</label>
              <input
                id="resourceCode"
                className="input"
                value={form.resourceCode}
                onChange={(e) => onChange("resourceCode", e.target.value)}
                required
              />
            </div>
            <div className="form-row">
              <label htmlFor="resourceName">Name</label>
              <input
                id="resourceName"
                className="input"
                value={form.resourceName}
                onChange={(e) => onChange("resourceName", e.target.value)}
                required
              />
            </div>
            <div className="form-row">
              <label htmlFor="resourceType">Type</label>
              <select
                id="resourceType"
                className="input"
                value={form.resourceType}
                onChange={(e) => onChange("resourceType", e.target.value)}
              >
                <option value="CLASSROOM">Classroom</option>
                <option value="LAB">Lab</option>
                <option value="EQUIPMENT">Equipment</option>
              </select>
            </div>
            <div className="form-row">
              <label htmlFor="capacity">Capacity</label>
              <input
                id="capacity"
                type="number"
                min="0"
                className="input"
                value={form.capacity}
                onChange={(e) => onChange("capacity", e.target.value)}
                required
              />
            </div>
            <div className="form-row">
              <label htmlFor="location">Location</label>
              <input
                id="location"
                className="input"
                value={form.location}
                onChange={(e) => onChange("location", e.target.value)}
                required
              />
            </div>
            <button type="submit" className="btn btn--primary">
              Add Resource
            </button>
          </form>
        </div>
      ) : null}

      <div className="card">
        <h3>Resource List</h3>
        {resources.length === 0 ? (
          <p className="message message--info">No resources yet.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Type</th>
                <th>Capacity</th>
                <th>Location</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {resources.map((res) => (
                <tr key={res.id}>
                  <td>{res.resourceCode}</td>
                  <td>{res.resourceName}</td>
                  <td>{res.resourceType}</td>
                  <td>{res.capacity}</td>
                  <td>{res.location}</td>
                  <td>{res.active ? "Active" : "Inactive"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default function AppRoutes() {
  return (
    <div className="app-shell">
      <Navbar />
      <div className="app-body">
        <Sidebar />
        <div className="app-content">
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/resources"
              element={
                <ProtectedRoute roles={["ADMIN"]}>
                  <ResourcesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/booking"
              element={
                <ProtectedRoute roles={["ADMIN"]}>
                  <BookingPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/my-bookings"
              element={
                <ProtectedRoute>
                  <MyBookingsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/my-requests"
              element={
                <ProtectedRoute roles={["FACULTY"]}>
                  <MyBookingsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/my-timetable"
              element={
                <ProtectedRoute roles={["FACULTY"]}>
                  <FacultyTimetablePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/free-rooms"
              element={
                <ProtectedRoute roles={["FACULTY"]}>
                  <FreeRoomsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/request-booking"
              element={
                <ProtectedRoute roles={["FACULTY"]}>
                  <RequestRoomPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/profile"
              element={
                <ProtectedRoute roles={["FACULTY", "ADMIN"]}>
                  <ProfilePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/utilization"
              element={
                <ProtectedRoute>
                  <UtilizationPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/approvals"
              element={
                <ProtectedRoute roles={["ADMIN"]}>
                  <ApprovalsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/analytics"
              element={
                <ProtectedRoute roles={["ADMIN"]}>
                  <AnalyticsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/auto-allocation"
              element={
                <ProtectedRoute roles={["ADMIN"]}>
                  <AutoAllocationPage />
                </ProtectedRoute>
              }
            />
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </div>
      </div>
    </div>
  );
}
