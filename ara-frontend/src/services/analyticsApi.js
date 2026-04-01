import apiClient from "./apiClient";

const analyticsApi = {
  listUsageLogs() {
    return apiClient.get("/analytics/usage-logs").then((res) => res.data);
  },
};

export default analyticsApi;
