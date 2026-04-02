import apiClient from "./apiClient";

export const authService = {
  register(payload) {
    return apiClient.post("/auth/register", payload);
  },
  login(payload) {
    return apiClient.post("/auth/login", payload);
  },
  logout() {
    return apiClient.post("/auth/logout");
  },
  refresh() {
    return apiClient.post("/auth/refresh");
  },
  me() {
    return apiClient.get("/auth/me");
  },
};
