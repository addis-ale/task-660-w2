import { act, renderHook } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/authService", () => ({
  authService: {
    login: vi.fn(),
    me: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
  },
}));

vi.mock("../api/apiClient", () => ({
  setUnauthorizedHandler: vi.fn(),
  setRateLimitHandler: vi.fn(),
}));

vi.mock("../components/ToastProvider", () => ({
  useToast: () => ({ showToast: vi.fn() }),
  ToastProvider: ({ children }) => children,
}));

import { authService } from "../api/authService";
import { AuthProvider, useAuth } from "../auth/AuthContext";

function wrapper({ children }) {
  return (
    <MemoryRouter>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>
  );
}

describe("AuthContext", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it("login sets token and user in localStorage", async () => {
    authService.login.mockResolvedValue({ access_token: "test-token-123" });
    authService.me.mockResolvedValue({
      id: "u1",
      role: "MEMBER",
      email: "test@example.com",
    });

    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(async () => {
      await result.current.login({
        email: "test@example.com",
        password: "pass",
      });
    });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user).toEqual({
      id: "u1",
      role: "MEMBER",
      email: "test@example.com",
    });
    expect(localStorage.getItem("hm_user")).toContain("test@example.com");
    expect(localStorage.getItem("hm_token")).toBe("test-token-123");
  });

  it("logout clears token, user, and cart from localStorage", async () => {
    localStorage.setItem("hm_token", "old-token");
    localStorage.setItem("hm_user", JSON.stringify({ role: "MEMBER" }));
    localStorage.setItem("hm_cart", JSON.stringify([{ id: "item1" }]));

    authService.logout.mockResolvedValue({});

    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(async () => {
      await result.current.logout();
    });

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
    expect(localStorage.getItem("hm_user")).toBeNull();
    expect(localStorage.getItem("hm_cart")).toBeNull();
  });
});
