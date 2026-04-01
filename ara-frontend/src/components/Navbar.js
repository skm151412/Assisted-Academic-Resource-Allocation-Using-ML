import { Link } from "react-router-dom";
import useAuth from "../hooks/useAuth";
import "./Navbar.css";

export default function Navbar() {
  const { isAuthenticated, user, role, logout } = useAuth();

  return (
    <div className="navbar">
      <Link to="/dashboard" className="navbar__brand">
        ARA
      </Link>
      {isAuthenticated ? (
        <span className="navbar__actions">
          <span className="navbar__user">
            {user?.username || "User"} ({role})
          </span>
          <button type="button" className="btn btn--ghost" onClick={logout}>
            Logout
          </button>
        </span>
      ) : (
        <Link to="/login">Login</Link>
      )}
    </div>
  );
}
