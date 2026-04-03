import { useEffect, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import ProtectedRoute from "./ProtectedRoute";
import ApprovalsPage from "../pages/ApprovalsPage";
import AnalyticsPage from "../pages/AnalyticsPage";
import BookingPage from "../pages/BookingPage";
import MyBookingsPage from "../pages/MyBookingsPage";
import UtilizationPage from "../pages/UtilizationPage";
import AutoAllocationPage from "../pages/AutoAllocationPage";
import FacultyDashboardPage from "../pages/FacultyDashboardPage";
import FacultyTimetablePage from "../pages/FacultyTimetablePage";
import RequestRoomPage from "../pages/RequestRoomPage";
import ProfilePage from "../pages/ProfilePage";
import Navbar from "../components/Navbar";
import Sidebar from "../components/Sidebar";
import ChatWidget from "../components/ChatWidget";
import useAuth from "../hooks/useAuth";
import usePolling from "../hooks/usePolling";
import resourceApi from "../services/resourceApi";
import allocationApi from "../services/allocationApi";
import { getErrorMessage } from "../services/apiClient";
import "../pages/LoginPage.css";
import "../pages/DashboardPage.css";
import "../pages/ResourcesPage.css";

const MANAGED_RESOURCE_ORDER = ["H1-01", "H1-02", "H1-03", "H1-04", "H1-17", "H1-18", "H1-19", "H1-22", "H1-23", "H1-25", "H1-26"];

function toLocalDateTimeValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

function buildLiveWindow() {
  const start = new Date();
  start.setSeconds(0, 0);
  const end = new Date(start.getTime() + 50 * 60 * 1000);

  const labelFormatter = new Intl.DateTimeFormat("en-US", {
    hour: "2-digit",
    minute: "2-digit",
  });

  return {
    startTime: toLocalDateTimeValue(start),
    endTime: toLocalDateTimeValue(end),
    label: `${labelFormatter.format(start)} - ${labelFormatter.format(end)}`,
  };
}

function AdminDashboardPage() {
  const { user, role } = useAuth();
  const [stats, setStats] = useState({ 
    totalAllocations: 0, 
    roomUsagePercent: '0.0', 
    managedRooms: 0,
  });
  const [liveFreeRooms, setLiveFreeRooms] = useState(0);
  const [liveWindowLabel, setLiveWindowLabel] = useState("");
  const [statsError, setStatsError] = useState("");

  const loadStats = async () => {
    try {
      const window = buildLiveWindow();
      const [data, freeRooms] = await Promise.all([
        allocationApi.getStats(),
        resourceApi.listFreeResources({
          startTime: window.startTime,
          endTime: window.endTime,
        }),
      ]);

      setStats(data);
      setLiveFreeRooms(freeRooms.length);
      setLiveWindowLabel(window.label);
      setStatsError("");
    } catch (err) {
      setStatsError(getErrorMessage(err, "Failed to load dashboard stats"));
    }
  };

  usePolling(loadStats, 5000);

  return (
    <div className="page admin-page admin-page--dashboard admin-dashboard-page">
      <h2>Dashboard</h2>
      <div className="card dashboard-card dashboard-welcome">
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

      <div className="admin-dashboard-layout">
        <div className="admin-dashboard-main">
          <div className="dashboard-metric-grid">
            <div className="card dashboard-metric-card dashboard-metric-card--allocations">
              <h3 className="dashboard-metric-label">Total Allocations</h3>
              <div className="dashboard-metric-value">{stats.totalAllocations}</div>
            </div>
            <div className="card dashboard-metric-card dashboard-metric-card--usage">
              <h3 className="dashboard-metric-label">Weekly Room Usage</h3>
              <div className="dashboard-metric-value">{stats.roomUsagePercent}%</div>
              <div className="dashboard-metric-meta">Distinct managed rooms used in timetable</div>
            </div>
            <div className="card dashboard-metric-card dashboard-metric-card--free">
              <h3 className="dashboard-metric-label">Live Free Rooms</h3>
              <div className="dashboard-metric-value">{liveFreeRooms}</div>
              <div className="dashboard-metric-meta">
                For current 50-min slot ({liveWindowLabel || "--"}) out of {stats.managedRooms} managed rooms
              </div>
            </div>
          </div>

          <div className="card dashboard-card dashboard-health-card">
            <h3>Operational Snapshot</h3>
            <div className="dashboard-health-grid">
              <div>
                <span className="dashboard-health-label">Managed Rooms</span>
                <strong>{stats.managedRooms}</strong>
              </div>
              <div>
                <span className="dashboard-health-label">Live Window</span>
                <strong>{liveWindowLabel || "--"}</strong>
              </div>
            </div>
          </div>
        </div>

        <aside className="card dashboard-card dashboard-actions-card">
          <h3>Quick Actions</h3>
          <div className="dashboard-actions-list">
            <Link className="btn btn--primary" to="/auto-allocation">Generate Allocation</Link>
            <Link className="btn btn--ghost" to="/approvals">Review Approvals</Link>
            <Link className="btn btn--ghost" to="/resources">Manage Resources</Link>
          </div>
          <p className="dashboard-metric-meta">Use these shortcuts to handle daily admin tasks faster.</p>
        </aside>
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
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  const { login, isAuthenticated } = useAuth();

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!username.trim() || !password.trim()) {
      setError("Please enter both username and password.");
      return;
    }
    
    setIsLoading(true);
    try {
      await login({ username, password });
      setError("");
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, "Login failed. Please check your credentials."));
    } finally {
      setIsLoading(false);
    }
  };

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="login-page">
      <div className="login-container">
        <div className="login-branding">
          <div className="login-logo-box">
            <svg viewBox="0 0 24 24" width="48" height="48" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" className="login-logo"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="3" y1="9" x2="21" y2="9"></line><line x1="9" y1="21" x2="9" y2="9"></line></svg>
          </div>
          <h1>ARA System</h1>
          <p>Academic Resource Allocation</p>
          <div className="login-features">
            <div className="login-feature">
              <span className="login-feature-icon">
                <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>
              </span>
              <span>Secure Access</span>
            </div>
            <div className="login-feature">
              <span className="login-feature-icon">
                <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
              </span>
              <span>Real-time Bookings</span>
            </div>
            <div className="login-feature">
              <span className="login-feature-icon">
                <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
              </span>
              <span>Smart Allocation</span>
            </div>
          </div>
        </div>
        
        <div className="login-card-wrapper">
          <form onSubmit={handleSubmit} className="card login-card" noValidate>
            <div className="login-card-header">
              <h2>Welcome back</h2>
              <p>Enter your credentials to access your account</p>
            </div>
            
            <div className="login-form-body">
              {error ? (
                <div className="login-error-box">
                  <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
                  <span>{error}</span>
                </div>
              ) : null}

              <div className="form-row">
                <label htmlFor="username">Username</label>
                <div className="input-with-icon">
                  <svg className="input-icon" viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
                  <input
                    id="username"
                    type="text"
                    className={`input ${error && !username.trim() ? "input--error" : ""}`}
                    placeholder="Enter your username"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    disabled={isLoading}
                  />
                </div>
              </div>
              
              <div className="form-row">
                <label htmlFor="password">Password</label>
                <div className="input-with-icon">
                  <svg className="input-icon" viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                  <input
                    id="password"
                    type="password"
                    className={`input ${error && !password.trim() ? "input--error" : ""}`}
                    placeholder="Enter your password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    disabled={isLoading}
                  />
                </div>
              </div>
            </div>
            
            <div className="login-card-footer">
              <button type="submit" className="btn btn--primary btn--loading login-submit-btn" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <span className="spinner-icon"></span> Signing in...
                  </>
                ) : (
                  "Sign in securely"
                )}
              </button>
            </div>
          </form>
        </div>
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
    <div className="page admin-page admin-page--resources resources-page">
      <h2>Resources</h2>
      {error ? <p className="message message--error">{error}</p> : null}
      {message ? <p className="message message--success">{message}</p> : null}
      {role === "ADMIN" ? (
        <div className="card resources-page__form-card">
          <h3>Add Resource</h3>
          <form className="form-grid resources-page__form-grid" onSubmit={handleSubmit}>
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

      <div className="card resources-page__table-card">
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
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  const showChatWidget = isAuthenticated && location.pathname !== "/login";

  return (
    <div className="app-shell">
      <Navbar />
      <div className="app-body">
        <Sidebar />
        <div className="app-content">
          <div className="app-content__inner">
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
                    <Navigate to="/request-booking" replace />
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
      {showChatWidget ? <ChatWidget /> : null}
    </div>
  );
}
