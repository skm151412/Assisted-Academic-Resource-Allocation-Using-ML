import { NavLink } from "react-router-dom";
import useAuth from "../hooks/useAuth";
import "./Sidebar.css";

export default function Sidebar() {
  const { isAuthenticated, role } = useAuth();

  const adminLinks = [
    { to: "/dashboard", label: "Dashboard", icon: "DB" },
    { to: "/resources", label: "Resources", icon: "RS" },
    { to: "/booking", label: "Booking", icon: "BK" },
    { to: "/approvals", label: "Approvals", icon: "AP" },
    { to: "/auto-allocation", label: "Auto Allocation", icon: "AA" },
  ];

  const facultyLinks = [
    { to: "/dashboard", label: "Dashboard", icon: "DB" },
    { to: "/my-timetable", label: "My Timetable", icon: "TT" },
    { to: "/request-booking", label: "Request Booking", icon: "RB" },
    { to: "/my-requests", label: "My Requests", icon: "MR" },
    { to: "/profile", label: "Profile", icon: "PR" },
  ];

  const links = role === "ADMIN" ? adminLinks : facultyLinks;

  if (!isAuthenticated) {
    return null;
  }

  return (
    <nav className="sidebar">
      <ul className="sidebar__list">
        {links.map((link) => (
          <li key={link.to}>
            <NavLink
              to={link.to}
              className={({ isActive }) =>
                isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
              }
            >
              <span className="sidebar__icon" aria-hidden="true">{link.icon}</span>
              <span>{link.label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
