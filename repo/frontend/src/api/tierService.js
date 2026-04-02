import apiClient from "./apiClient";

export const tierService = {
  listTiers() {
    return apiClient.get("/admin/tiers");
  },
  createTier(payload) {
    return apiClient.post("/admin/tiers", payload);
  },
  updateTier(tierId, payload) {
    return apiClient.put(`/admin/tiers/${tierId}`, payload);
  },
  listBenefits(params) {
    return apiClient.get("/admin/benefits", { params });
  },
  createBenefit(payload) {
    return apiClient.post("/admin/benefits", payload);
  },
  updateBenefit(benefitId, payload) {
    return apiClient.put(`/admin/benefits/${benefitId}`, payload);
  },
};
