import apiClient from "./apiClient";

const userApi = {
  getUser(id) {
    return apiClient.get(`/users/${id}`).then((res) => res.data);
  },
  updateProfile(id, payload) {
    return apiClient.put(`/users/${id}/profile`, payload).then((res) => res.data);
  },
};

export default userApi;
