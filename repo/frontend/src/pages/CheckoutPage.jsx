import { useMemo, useState } from "react";
import { orderService } from "../api/orderService";
import { useAuth } from "../auth/AuthContext";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { CountdownTimer } from "../components/CountdownTimer";
import { EmptyState } from "../components/EmptyState";
import { Skeleton } from "../components/Skeleton";
import { useToast } from "../components/ToastProvider";
import { useCart } from "../hooks/useCart";
import { formatCurrency } from "../utils/formatting";
import { generateIdempotencyKey } from "../utils/idempotency";

export default function CheckoutPage() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const cart = useCart();
  const [address, setAddress] = useState({ lat: "", lng: "" });
  const [placing, setPlacing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [order, setOrder] = useState(null);

  const subtotal = useMemo(
    () =>
      cart.items.reduce(
        (sum, item) =>
          sum + Number(item.price || 0) * Number(item.quantity || 0),
        0,
      ),
    [cart.items],
  );

  const placeOrder = async () => {
    if (cart.items.length === 0) {
      return;
    }

    setPlacing(true);
    try {
      const payload = {
        delivery_lat: address.lat ? Number(address.lat) : undefined,
        delivery_lng: address.lng ? Number(address.lng) : undefined,
        items: cart.items.map((item) => ({
          listing_id: item.id,
          quantity: Number(item.quantity),
        })),
      };
      const response = await orderService.create(
        payload,
        generateIdempotencyKey(),
      );
      setOrder(response);
      showToast(
        "Order reserved. Confirm payment within 30 minutes.",
        "success",
      );
    } catch (error) {
      showToast(error?.message || "Failed to place order", "error");
    } finally {
      setPlacing(false);
    }
  };

  const confirmPayment = async () => {
    if (!order?.id) {
      return;
    }

    setConfirming(true);
    try {
      const response = await orderService.confirm(
        order.id,
        generateIdempotencyKey(),
      );
      setOrder(response);
      cart.clear();
      showToast("Payment confirmed successfully", "success");
    } catch (error) {
      showToast(error?.message || "Failed to confirm payment", "error");
    } finally {
      setConfirming(false);
    }
  };

  if (user?.role !== "MEMBER") {
    return (
      <EmptyState
        title="Members only"
        message="Checkout is available for members."
      />
    );
  }

  return (
    <div className="checkout-grid">
      <Card>
        <h2>Cart</h2>
        {cart.items.length === 0 ? (
          <EmptyState
            title="Your cart is empty"
            message="Add listings from Browse to start checkout."
          />
        ) : (
          <div className="stack-md">
            {cart.items.map((item) => (
              <div key={item.id} className="line-item">
                <div>
                  <strong>{item.title}</strong>
                  <small>{formatCurrency(item.price)} each</small>
                </div>
                <input
                  className="qty-input"
                  min={1}
                  type="number"
                  value={item.quantity}
                  onChange={(e) =>
                    cart.updateQuantity(
                      item.id,
                      Math.max(1, Number(e.target.value || 1)),
                    )
                  }
                />
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card>
        <h2>Tier Card</h2>
        <p>
          Current Tier: <strong>{user?.tier_name || "Member"}</strong>
        </p>
        <ul className="plain-list">
          <li>Exclusive pricing applies when available.</li>
          <li>Percentage discounts stack with free shipping only.</li>
          <li>
            Higher-priority benefits override lower-priority ones in conflicts.
          </li>
        </ul>

        <h3>Delivery Coordinates</h3>
        <div className="filters-grid">
          <label>
            Latitude
            <input
              value={address.lat}
              onChange={(e) =>
                setAddress((prev) => ({ ...prev, lat: e.target.value }))
              }
            />
          </label>
          <label>
            Longitude
            <input
              value={address.lng}
              onChange={(e) =>
                setAddress((prev) => ({ ...prev, lng: e.target.value }))
              }
            />
          </label>
        </div>
      </Card>

      <Card>
        <h2>Payment Summary</h2>
        <div className="summary-list">
          <div>
            <span>Subtotal</span>
            <strong>{formatCurrency(subtotal)}</strong>
          </div>
          <div>
            <span>Total Discount</span>
            <strong>{formatCurrency(order?.discount_amount || 0)}</strong>
          </div>
          <div>
            <span>Final Amount</span>
            <strong>{formatCurrency(order?.final_amount || subtotal)}</strong>
          </div>
        </div>

        <h3>Applied Benefits</h3>
        {(order?.applied_benefits || []).length > 0 ? (
          <ul className="plain-list">
            {order.applied_benefits.map((benefit) => (
              <li key={benefit.id}>
                {benefit.name}: -{formatCurrency(benefit.applied_value)}
              </li>
            ))}
          </ul>
        ) : (
          <p>No benefits applied yet.</p>
        )}

        <h3>Blocked Benefits</h3>
        <p className="muted">
          Your 15% discount may be blocked when an Exclusive Price rule is
          active for the same listing.
        </p>

        {(placing || confirming) && (
          <div className="stack-md">
            <Skeleton className="skeleton-block" />
          </div>
        )}

        <div className="actions-row">
          <Button
            disabled={placing || cart.items.length === 0}
            onClick={placeOrder}
          >
            {placing ? "Placing..." : "Place Order"}
          </Button>
          <Button
            variant="secondary"
            disabled={confirming || !order?.id}
            onClick={confirmPayment}
          >
            {confirming ? "Confirming..." : "Confirm Payment"}
          </Button>
        </div>

        {order?.reservation_expires_at && (
          <CountdownTimer
            targetDate={order.reservation_expires_at}
            label="Reservation timer"
          />
        )}
      </Card>
    </div>
  );
}
