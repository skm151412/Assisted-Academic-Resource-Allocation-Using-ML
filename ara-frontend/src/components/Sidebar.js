import { NavLink } from "react-router-dom";
import useAuth from "../hooks/useAuth";
import "./Sidebar.css";

export default function Sidebar() {
  const { isAuthenticated, role } = useAuth();

  if (!isAuthenticated) {
    return null;
  }

  return (
    <nav className="sidebar">
      <ul className="sidebar__list">
        <li>
          <NavLink
            to="/dashboard"
            className={({ isActive }) =>
              isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
            }
          >
            Dashboard
          </NavLink>
        </li>
        {role === "ADMIN" ? (
          <>
            <li>
              <NavLink
                to="/resources"
                className={({ isActive }) =>
                  isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
                }
              >
                Resources
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/booking"
                className={({ isActive }) =>
                  isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
                }
              >
                Booking
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/approvals"
                className={({ isActive }) =>
                  isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
                }
              >
                Approvals
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/auto-allocation"
                className={({ isActive }) =>
                  isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
                }
              >
                Auto Allocation
              </NavLink>
            </li>
          </>
        ) : (
          <>
            <li>
              <NavLink
                to="/my-timetable"
                className={({ isActive }) =>
                  isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
                }
              >
                My Timetable
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/free-rooms"
                className={({ isActive }) =>
                  isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
                }
              >
                Free Rooms
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/request-booking"
                className={({ isActive }) =>
                  isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
                }
              >
                Request Booking
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/my-requests"
                className={({ isActive }) =>
                  isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
                }
              >
                My Requests
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/profile"
                className={({ isActive }) =>
                  isActive ? "sidebar__link sidebar__link--active" : "sidebar__link"
                }
              >
                Profile
              </NavLink>
            </li>
          </>
        )}
      </ul>
    </nav>
  );
}
