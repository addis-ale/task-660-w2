import apiClient from "./apiClient";

export const listingService = {
  search(params) {
    return apiClient.get("/listings", { params });
  },
  searchWithMeta(params) {
    return apiClient
      .get("/listings", { params, skipEnvelope: true })
      .then((res) => {
        const payload = res.data || res;
        return {
          data: payload.data || [],
          meta: payload.meta || { page: 0, pageSize: 20, totalItems: 0, totalPages: 0 },
        };
      });
  },
  trending(limit = 10) {
    return apiClient.get("/listings/trending", { params: { limit } });
  },
  detail(id) {
    return apiClient.get(`/listings/${id}`);
  },
  create(payload) {
    return apiClient.post("/listings", payload);
  },
  update(id, payload) {
    return apiClient.put(`/listings/${id}`, payload);
  },
  remove(id) {
    return apiClient.delete(`/listings/${id}`);
  },
  recentSearches() {
    return apiClient.get("/listings/recent-searches");
  },
  clearRecentSearches() {
    return apiClient.delete("/listings/recent-searches");
  },
};
