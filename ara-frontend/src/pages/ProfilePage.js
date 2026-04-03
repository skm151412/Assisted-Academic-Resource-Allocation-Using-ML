import { useEffect, useState } from "react";
import useAuth from "../hooks/useAuth";
import userApi from "../services/userApi";
import { getErrorMessage } from "../services/apiClient";
import "./FacultyPanel.css";

export default function ProfilePage() {
  const { user, updateSession } = useAuth();
  const [form, setForm] = useState({
    fullName: "",
    department: "",
    subjectsHandled: "",
    password: "",
  });
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!user?.id) {
      return;
    }

    userApi
      .getUser(user.id)
      .then((data) => {
        setForm({
          fullName: data.fullName || "",
          department: data.department || "",
          subjectsHandled: data.subjectsHandled || "",
          password: "",
        });
      })
      .catch((err) => setError(getErrorMessage(err, "Failed to load profile")));
  }, [user]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!user?.id) {
      setError("User is not available");
      return;
    }

    try {
      const updated = await userApi.updateProfile(user.id, form);
      updateSession({
        fullName: updated.fullName,
        department: updated.department,
        subjectsHandled: updated.subjectsHandled,
      });
      setMessage("Profile updated successfully.");
      setError("");
      setForm((current) => ({ ...current, password: "" }));
    } catch (err) {
      setError(getErrorMessage(err, "Profile update failed"));
      setMessage("");
    }
  };

  return (
    <div className="page faculty-page faculty-page--profile">
      <div className="faculty-page__header">
        <div>
          <h2>Profile</h2>
          <p>Keep your faculty details and subjects handled up to date.</p>
        </div>
      </div>

      {error ? <p className="message message--error">{error}</p> : null}
      {message ? <p className="message message--info">{message}</p> : null}

      <div className="profile-layout">
        <form className="card faculty-card form-grid profile-form" onSubmit={handleSubmit}>
          <div className="form-row">
            <label htmlFor="profileName">Full Name</label>
            <input
              id="profileName"
              className="input"
              value={form.fullName}
              onChange={(event) => setForm((current) => ({ ...current, fullName: event.target.value }))}
            />
          </div>
          <div className="form-row">
            <label htmlFor="profileDepartment">Department</label>
            <input
              id="profileDepartment"
              className="input"
              value={form.department}
              onChange={(event) => setForm((current) => ({ ...current, department: event.target.value }))}
            />
          </div>
          <div className="form-row">
            <label htmlFor="profileSubjects">Subjects Handled</label>
            <input
              id="profileSubjects"
              className="input"
              value={form.subjectsHandled}
              onChange={(event) => setForm((current) => ({ ...current, subjectsHandled: event.target.value }))}
            />
          </div>
          <div className="form-row">
            <label htmlFor="profilePassword">New Password</label>
            <input
              id="profilePassword"
              type="password"
              className="input"
              value={form.password}
              onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
              placeholder="Optional"
            />
          </div>
          <div className="faculty-page__actions">
            <button type="submit" className="btn btn--primary">
              Save Profile
            </button>
          </div>
        </form>

        <aside className="card profile-side-card">
          <h3>Account Notes</h3>
          <p>Keep profile details updated so approvals and timetables map correctly.</p>
          <p>Password update is optional and only needed when changing credentials.</p>
        </aside>
      </div>
    </div>
  );
}
