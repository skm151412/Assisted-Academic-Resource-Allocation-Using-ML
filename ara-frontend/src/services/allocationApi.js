import apiClient from "./apiClient";

const allocationApi = {
  generateAllocations(payload = {}) {
    return apiClient.post("/allocations/generate", payload).then((res) => res.data);
  },
  listAllocations() {
    return apiClient.get("/allocations").then((res) => res.data);
  },
  listFacultyAllocations(userId) {
    return apiClient.get(`/allocations/faculty/${userId}`).then((res) => res.data);
  },
  getStats() {
    return apiClient.get("/allocations/stats").then((res) => res.data);
  }
};

export default allocationApi;
