import apiClient from "./apiClient";

export const riskService = {
  dashboard() {
    return apiClient.get("/risk/dashboard");
  },
  flags(params) {
    return apiClient.get("/risk/flags", { params });
  },
  entity(entityId, entityType) {
    return apiClient.get(`/risk/entity/${entityId}`, {
      params: { entityType },
    });
  },
};
