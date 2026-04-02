import axios from "axios";
import { getToken } from "../auth/tokenStore";
import { generateIdempotencyKey } from "../utils/idempotency";

let unauthorizedHandler = null;
let rateLimitHandler = null;

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

export function setRateLimitHandler(handler) {
  rateLimitHandler = handler;
}

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1",
  timeout: 20000,
});

apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  const url = config.url || "";
  const method = (config.method || "get").toLowerCase();
  if (
    method === "post" &&
    (url.startsWith("/orders") || url.includes("/orders/")) &&
    !config.headers["X-Idempotency-Key"]
  ) {
    config.headers["X-Idempotency-Key"] = generateIdempotencyKey();
  }

  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    if (response.config?.skipEnvelope) {
      return response;
    }

    const payload = response.data;
    if (payload && typeof payload === "object" && "success" in payload) {
      if (!payload.success) {
        return Promise.reject(payload.error || { message: "Request failed" });
      }
      return payload.data;
    }
    return payload;
  },
  (error) => {
    const status = error?.response?.status;
    if (status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }

    if (status === 429 && rateLimitHandler) {
      const retryAfter = error?.response?.headers?.["retry-after"];
      rateLimitHandler(retryAfter || "60");
    }

    const payload = error?.response?.data;
    if (payload?.error) {
      return Promise.reject(payload.error);
    }

    return Promise.reject(error);
  },
);

export default apiClient;
