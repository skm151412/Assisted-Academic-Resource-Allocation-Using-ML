import apiClient from "./apiClient";

const resourceApi = {
  createResource(payload) {
    return apiClient.post("/resources", payload).then((res) => res.data);
  },
  getResource(id) {
    return apiClient.get(`/resources/${id}`).then((res) => res.data);
  },
  listResources() {
    return apiClient.get("/resources").then((res) => res.data);
  },
  listManagedResources() {
    return apiClient.get("/resources/managed").then((res) => res.data);
  },
  listFreeResources(params) {
    return apiClient.get("/resources/free", { params }).then((res) => res.data);
  },
};

export default resourceApi;
