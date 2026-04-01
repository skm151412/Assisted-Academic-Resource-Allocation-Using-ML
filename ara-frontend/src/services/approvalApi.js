import apiClient from "./apiClient";

const approvalApi = {
  listApprovals() {
    return apiClient.get("/approvals").then((res) => res.data);
  },
  approveBooking(bookingId, payload) {
    return apiClient
      .post(`/approvals/${bookingId}/approve`, payload)
      .then((res) => res.data);
  },
  rejectBooking(bookingId, payload) {
    return apiClient
      .post(`/approvals/${bookingId}/reject`, payload)
      .then((res) => res.data);
  },
};

export default approvalApi;
