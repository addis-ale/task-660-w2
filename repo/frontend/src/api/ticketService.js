import apiClient from "./apiClient";

export const ticketService = {
  create(payload) {
    return apiClient.post("/tickets", payload);
  },
  list(params) {
    return apiClient.get("/tickets", { params });
  },
  detail(ticketId) {
    return apiClient.get(`/tickets/${ticketId}`);
  },
  acknowledge(ticketId, payload) {
    return apiClient.post(`/tickets/${ticketId}/acknowledge`, payload || {});
  },
  updateStatus(ticketId, payload) {
    return apiClient.patch(`/tickets/${ticketId}/status`, payload);
  },
  resolve(ticketId, payload) {
    return apiClient.post(`/tickets/${ticketId}/resolve`, payload);
  },
  addFollowUp(ticketId, payload) {
    return apiClient.post(`/tickets/${ticketId}/follow-ups`, payload);
  },
  followUps(ticketId) {
    return apiClient.get(`/tickets/${ticketId}/follow-ups`);
  },
};
