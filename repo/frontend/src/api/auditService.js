import apiClient from "./apiClient";

export const auditService = {
  list(params) {
    return apiClient.get("/admin/audit-logs", { params });
  },
};
