import apiClient from "./apiClient";

const utilizationApi = {
  listUsageLogs() {
    return apiClient.get("/analytics/usage-logs").then((res) => res.data);
  },
};

export default utilizationApi;
