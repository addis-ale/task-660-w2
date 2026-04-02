import apiClient from "./apiClient";

export const inventoryService = {
  warehouses() {
    return apiClient.get("/warehouses");
  },
  createWarehouse(payload) {
    return apiClient.post("/warehouses", payload);
  },
  updateWarehouse(id, payload) {
    return apiClient.put(`/warehouses/${id}`, payload);
  },
  inventory(params) {
    return apiClient.get("/inventory", { params });
  },
  updateThreshold(inventoryId, payload) {
    return apiClient.patch(`/inventory/${inventoryId}/threshold`, payload);
  },
  createDocument(payload) {
    return apiClient.post("/inventory/documents", payload);
  },
  stocktake(payload) {
    return apiClient.post("/inventory/stocktake", payload);
  },
  movements(inventoryId, params) {
    return apiClient.get(`/inventory/${inventoryId}/movements`, { params });
  },
  lowStockAlerts(params) {
    return apiClient.get("/inventory/alerts/low-stock", { params });
  },
};
