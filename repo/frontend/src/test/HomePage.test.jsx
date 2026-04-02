import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ isAuthenticated: false, user: null }),
}));

vi.mock("../components/ToastProvider", () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

vi.mock("../api/listingService", () => ({
  listingService: {
    search: vi.fn().mockResolvedValue([]),
    trending: vi.fn().mockResolvedValue([
      { id: "t1", title: "Trending Item", price: 50 },
    ]),
    recentSearches: vi.fn().mockResolvedValue([]),
  },
}));

import HomePage from "../pages/HomePage";

function renderHomePage() {
  return render(
    <MemoryRouter>
      <HomePage />
    </MemoryRouter>,
  );
}

describe("HomePage", () => {
  it("renders search keyword input", () => {
    renderHomePage();
    expect(
      screen.getByPlaceholderText("Search by keyword..."),
    ).toBeInTheDocument();
  });

  it("renders Show Filters button", () => {
    renderHomePage();
    expect(screen.getByText("Show Filters")).toBeInTheDocument();
  });

  it("renders trending section heading", () => {
    renderHomePage();
    expect(screen.getByText("Trending This Week")).toBeInTheDocument();
  });

  it("renders results section heading", () => {
    renderHomePage();
    expect(screen.getByText("Results")).toBeInTheDocument();
  });
});
