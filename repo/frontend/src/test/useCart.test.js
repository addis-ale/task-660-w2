import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { useCart } from "../hooks/useCart";

describe("useCart", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it("starts with empty cart", () => {
    const { result } = renderHook(() => useCart());
    expect(result.current.items).toEqual([]);
  });

  it("adds an item", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem({ id: "L1", title: "Desk", price: 100 }, 2);
    });

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0]).toMatchObject({
      id: "L1",
      title: "Desk",
      price: 100,
      quantity: 2,
    });
  });

  it("increments quantity when adding existing item", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem({ id: "L1", title: "Desk", price: 100 }, 1);
    });

    act(() => {
      result.current.addItem({ id: "L1", title: "Desk", price: 100 }, 3);
    });

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].quantity).toBe(4);
  });

  it("removes an item", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem({ id: "L1", title: "Desk", price: 100 }, 1);
      result.current.addItem({ id: "L2", title: "Chair", price: 50 }, 1);
    });

    act(() => {
      result.current.removeItem("L1");
    });

    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].id).toBe("L2");
  });

  it("clears the cart", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem({ id: "L1", title: "Desk", price: 100 }, 1);
      result.current.addItem({ id: "L2", title: "Chair", price: 50 }, 1);
    });

    act(() => {
      result.current.clear();
    });

    expect(result.current.items).toEqual([]);
  });

  it("persists cart to localStorage", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem({ id: "L1", title: "Desk", price: 100 }, 2);
    });

    const stored = JSON.parse(localStorage.getItem("hm_cart"));
    expect(stored).toHaveLength(1);
    expect(stored[0].id).toBe("L1");
  });

  it("loads cart from localStorage on init", () => {
    localStorage.setItem(
      "hm_cart",
      JSON.stringify([{ id: "L1", title: "Desk", price: 100, quantity: 3 }]),
    );

    const { result } = renderHook(() => useCart());
    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].quantity).toBe(3);
  });

  it("updates quantity for a specific item", () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.addItem({ id: "L1", title: "Desk", price: 100 }, 1);
    });

    act(() => {
      result.current.updateQuantity("L1", 5);
    });

    expect(result.current.items[0].quantity).toBe(5);
  });
});
