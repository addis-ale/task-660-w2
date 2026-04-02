import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

vi.mock("../auth/AuthContext", () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from "../auth/AuthContext";
import ProtectedRoute from "../auth/ProtectedRoute";

function renderWithRouter(initialEntry, roles = []) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route
          path="/protected"
          element={
            <ProtectedRoute roles={roles}>
              <div>Protected Content</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div>Login Page</div>} />
        <Route path="/403" element={<div>Forbidden Page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("redirects unauthenticated users to /login", () => {
    useAuth.mockReturnValue({
      isAuthenticated: false,
      user: null,
      loading: false,
    });

    renderWithRouter("/protected", ["MEMBER"]);
    expect(screen.getByText("Login Page")).toBeInTheDocument();
  });

  it("redirects unauthorized roles to /403", () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      user: { role: "MEMBER" },
      loading: false,
    });

    renderWithRouter("/protected", ["ADMIN"]);
    expect(screen.getByText("Forbidden Page")).toBeInTheDocument();
  });

  it("renders children for authorized roles", () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      user: { role: "ADMIN" },
      loading: false,
    });

    renderWithRouter("/protected", ["ADMIN"]);
    expect(screen.getByText("Protected Content")).toBeInTheDocument();
  });

  it("shows loading state while auth is loading", () => {
    useAuth.mockReturnValue({
      isAuthenticated: false,
      user: null,
      loading: true,
    });

    renderWithRouter("/protected", ["MEMBER"]);
    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("renders children when no roles specified (any authenticated user)", () => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      user: { role: "SELLER" },
      loading: false,
    });

    renderWithRouter("/protected", []);
    expect(screen.getByText("Protected Content")).toBeInTheDocument();
  });
});
