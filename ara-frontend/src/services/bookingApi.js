import apiClient from "./apiClient";

const bookingApi = {
  createBooking(payload) {
    return apiClient.post("/bookings", payload).then((res) => res.data);
  },
  listBookings() {
    return apiClient.get("/bookings").then((res) => res.data);
  },
  listUserBookings(userId) {
    return apiClient.get(`/bookings/user/${userId}`).then((res) => res.data);
  },
  listPendingBookings() {
    return apiClient.get("/bookings/pending").then((res) => res.data);
  },
  pendingCount() {
    return apiClient.get("/bookings/pending/count").then((res) => res.data);
  },
};

export default bookingApi;
