import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

vi.mock("../auth/AuthContext", () => ({
  useAuth: vi.fn(),
}));

vi.mock("../hooks/useCart", () => ({
  useCart: vi.fn(),
}));

vi.mock("../components/ToastProvider", () => ({
  useToast: () => ({ showToast: vi.fn() }),
}));

vi.mock("../api/orderService", () => ({
  orderService: {
    create: vi.fn(),
    confirm: vi.fn(),
  },
}));

import { useAuth } from "../auth/AuthContext";
import { useCart } from "../hooks/useCart";
import CheckoutPage from "../pages/CheckoutPage";

function renderCheckout() {
  return render(
    <MemoryRouter>
      <CheckoutPage />
    </MemoryRouter>,
  );
}

describe("CheckoutPage", () => {
  it("shows empty cart state when cart is empty", () => {
    useAuth.mockReturnValue({ user: { role: "MEMBER", tier_name: "Bronze" } });
    useCart.mockReturnValue({
      items: [],
      addItem: vi.fn(),
      updateQuantity: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
    });

    renderCheckout();
    expect(screen.getByText("Your cart is empty")).toBeInTheDocument();
  });

  it("shows tier benefits section for MEMBER role", () => {
    useAuth.mockReturnValue({ user: { role: "MEMBER", tier_name: "Silver" } });
    useCart.mockReturnValue({
      items: [{ id: "L1", title: "Desk", price: 100, quantity: 1 }],
      addItem: vi.fn(),
      updateQuantity: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
    });

    renderCheckout();
    expect(screen.getByText("Tier Card")).toBeInTheDocument();
    expect(screen.getByText(/Silver/)).toBeInTheDocument();
    expect(
      screen.getByText(/Exclusive pricing applies when available/),
    ).toBeInTheDocument();
  });

  it("shows members only message for non-MEMBER role", () => {
    useAuth.mockReturnValue({ user: { role: "SELLER" } });
    useCart.mockReturnValue({
      items: [],
      addItem: vi.fn(),
      updateQuantity: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
    });

    renderCheckout();
    expect(screen.getByText("Members only")).toBeInTheDocument();
  });
});
