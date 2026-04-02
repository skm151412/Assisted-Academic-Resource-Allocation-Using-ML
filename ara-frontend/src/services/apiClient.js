import axios from "axios";

export const AUTH_STORAGE_KEY = "ara-auth";
const LEGACY_AUTH_STORAGE_KEY = "ara-user";

const isBrowser = typeof window !== "undefined";
const isLocalhost =
  isBrowser &&
  (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1");
const defaultApiBaseUrl = isLocalhost ? "http://localhost:8080/api" : "/api";
const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || defaultApiBaseUrl;

function readStorage(key) {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return window.localStorage.getItem(key);
  } catch (_) {
    return null;
  }
}

export function getStoredAuth() {
  const raw = readStorage(AUTH_STORAGE_KEY) || readStorage(LEGACY_AUTH_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw);
  } catch (_) {
    return null;
  }
}

export function storeAuth(session) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
  window.localStorage.removeItem(LEGACY_AUTH_STORAGE_KEY);
}

export function clearStoredAuth() {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(AUTH_STORAGE_KEY);
  window.localStorage.removeItem(LEGACY_AUTH_STORAGE_KEY);
}

export function getErrorMessage(error, fallback = "Request failed") {
  if (error?.displayMessage) {
    return error.displayMessage;
  }

  if (error?.response) {
    const payload = error.response.data;
    const message =
      (typeof payload === "string" && payload) ||
      payload?.message ||
      payload?.error;

    if (message) {
      return message;
    }

    return `${fallback} (${error.response.status})`;
  }

  if (error?.request) {
    return "Unable to reach the server. Make sure the backend is running on http://localhost:8080.";
  }

  return error?.message || fallback;
}

const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
});

apiClient.interceptors.request.use((config) => {
  const token = getStoredAuth()?.token;
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    error.displayMessage = getErrorMessage(error, "Request failed");
    return Promise.reject(error);
  }
);

export default apiClient;
