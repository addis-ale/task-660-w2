import apiClient from "./apiClient";

export const appealService = {
  create({ ticketId, reason, evidence }) {
    const form = new FormData();
    form.append("ticket_id", ticketId);
    form.append("reason", reason);
    (evidence || []).forEach((file) => form.append("evidence", file));
    return apiClient.post("/appeals", form, {
      headers: { "Content-Type": "multipart/form-data" },
    });
  },
  list(params) {
    return apiClient.get("/appeals", { params });
  },
  detail(appealId) {
    return apiClient.get(`/appeals/${appealId}`);
  },
  review(appealId, payload) {
    return apiClient.post(`/appeals/${appealId}/review`, payload);
  },
  finalReview(appealId, payload) {
    return apiClient.post(`/appeals/${appealId}/final-review`, payload);
  },
  async download(appealId, evidenceId) {
    const response = await apiClient.get(
      `/appeals/${appealId}/evidence/${evidenceId}/download`,
      {
        responseType: "blob",
        skipEnvelope: true,
      },
    );
    return response;
  },
};
