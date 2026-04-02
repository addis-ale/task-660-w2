import apiClient from "./apiClient";

export const orderService = {
  create(payload, idempotencyKey) {
    return apiClient.post("/orders", payload, {
      headers: idempotencyKey
        ? { "X-Idempotency-Key": idempotencyKey }
        : undefined,
    });
  },
  confirm(orderId, idempotencyKey) {
    return apiClient.post(
      `/orders/${orderId}/confirm`,
      {},
      {
        headers: idempotencyKey
          ? { "X-Idempotency-Key": idempotencyKey }
          : undefined,
      },
    );
  },
  cancel(orderId) {
    return apiClient.post(`/orders/${orderId}/cancel`);
  },
  myOrders(params) {
    return apiClient.get("/orders/me", { params });
  },
  detail(orderId) {
    return apiClient.get(`/orders/${orderId}`);
  },
  fulfill(orderId) {
    return apiClient.post(`/orders/${orderId}/fulfill`);
  },
};
