import apiClient from "./apiClient";

export const userService = {
  list(params) {
    return apiClient.get("/admin/users", { params });
  },
  updateRole(userId, payload) {
    return apiClient.patch(`/admin/users/${userId}/role`, payload);
  },
  updateStatus(userId, payload) {
    return apiClient.patch(`/admin/users/${userId}/status`, payload);
  },
};
