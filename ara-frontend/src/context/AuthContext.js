import { createContext, useCallback, useEffect, useMemo, useState } from "react";
import authApi from "../services/authApi";
import { clearStoredAuth, getStoredAuth, storeAuth } from "../services/apiClient";

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => getStoredAuth());

  useEffect(() => {
    if (session) {
      storeAuth(session);
    } else {
      clearStoredAuth();
    }
  }, [session]);

  const login = useCallback(async (payload) => {
    const username = payload?.username?.trim();
    const password = (payload?.password || "").trim();
    if (!username || !password) {
      throw new Error("Username and password are required");
    }
    const data = await authApi.login({ username, password });
    setSession(data);
    return data;
  }, []);

  const logout = useCallback(() => {
    setSession(null);
  }, []);

  const updateSession = useCallback((nextFields) => {
    setSession((current) => (current ? { ...current, ...nextFields } : current));
  }, []);

  const user = session
      ? {
          id: session.id,
          username: session.username,
          role: session.role,
          fullName: session.fullName,
          department: session.department,
          subjectsHandled: session.subjectsHandled,
        }
      : null;

  const value = useMemo(
    () => ({
      user,
      token: session?.token || null,
      role: session?.role || null,
      isAuthenticated: Boolean(session?.token && session?.id),
      loggedIn: Boolean(session?.token && session?.id),
      login,
      logout,
      updateSession,
    }),
    [user, session, login, logout, updateSession]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
