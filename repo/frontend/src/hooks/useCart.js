import { useEffect, useMemo, useState } from "react";

const CART_KEY = "hm_cart";

export function useCart() {
  const [items, setItems] = useState(() => {
    const raw = localStorage.getItem(CART_KEY);
    return raw ? JSON.parse(raw) : [];
  });

  useEffect(() => {
    localStorage.setItem(CART_KEY, JSON.stringify(items));
  }, [items]);

  const api = useMemo(
    () => ({
      items,
      addItem(listing, quantity = 1) {
        setItems((prev) => {
          const existing = prev.find((item) => item.id === listing.id);
          if (existing) {
            return prev.map((item) =>
              item.id === listing.id
                ? { ...item, quantity: item.quantity + quantity }
                : item,
            );
          }
          return [
            ...prev,
            {
              id: listing.id,
              title: listing.title,
              price: listing.price,
              listing,
              quantity,
            },
          ];
        });
      },
      updateQuantity(id, quantity) {
        setItems((prev) =>
          prev.map((item) => (item.id === id ? { ...item, quantity } : item)),
        );
      },
      removeItem(id) {
        setItems((prev) => prev.filter((item) => item.id !== id));
      },
      clear() {
        setItems([]);
      },
    }),
    [items],
  );

  return api;
}
