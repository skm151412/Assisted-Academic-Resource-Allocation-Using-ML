import apiClient from "./apiClient";

const authApi = {
  login(payload) {
    return apiClient.post("/auth/login", payload).then((res) => res.data);
  },
};

export default authApi;
