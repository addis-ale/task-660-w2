import { useEffect, useState } from "react";
import { orderService } from "../api/orderService";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { EmptyState } from "../components/EmptyState";
import { StatusBadge } from "../components/StatusBadge";
import { useToast } from "../components/ToastProvider";
import { formatCurrency, formatDateTime } from "../utils/formatting";

export default function OrdersPage() {
  const { showToast } = useToast();
  const [status, setStatus] = useState("");
  const [orders, setOrders] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    orderService
      .myOrders({ status: status || undefined, page: 0, pageSize: 50 })
      .then((rows) => setOrders(rows || []))
      .catch((error) =>
        showToast(error?.message || "Failed to load orders", "error"),
      )
      .finally(() => setLoading(false));
  }, [status, showToast]);

  const cancelOrder = async (orderId) => {
    if (!window.confirm("Cancel this order?")) {
      return;
    }
    try {
      const updated = await orderService.cancel(orderId);
      setOrders((prev) =>
        prev.map((order) => (order.id === orderId ? updated : order)),
      );
      if (selected?.id === orderId) {
        setSelected(updated);
      }
      showToast("Order cancelled", "success");
    } catch (error) {
      showToast(error?.message || "Cancellation failed", "error");
    }
  };

  return (
    <div className="page-grid">
      <Card>
        <div className="section-heading">
          <h2>My Orders</h2>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All statuses</option>
            <option value="RESERVED">Reserved</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="FULFILLED">Fulfilled</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>

        {loading ? (
          <p>Loading orders...</p>
        ) : orders.length === 0 ? (
          <EmptyState
            title="No orders yet"
            message="Orders you place will appear here."
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Status</th>
                  <th>Final Amount</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((order) => (
                  <tr key={order.id}>
                    <td>{order.id.slice(0, 8)}...</td>
                    <td>
                      <StatusBadge value={order.status} />
                    </td>
                    <td>{formatCurrency(order.final_amount)}</td>
                    <td>{formatDateTime(order.created_at)}</td>
                    <td className="actions-row">
                      <Button
                        variant="ghost"
                        onClick={() => setSelected(order)}
                      >
                        Details
                      </Button>
                      {(order.status === "RESERVED" ||
                        order.status === "CONFIRMED") && (
                        <Button
                          variant="danger"
                          onClick={() => cancelOrder(order.id)}
                        >
                          Cancel
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {selected && (
        <Card>
          <h3>Order Detail</h3>
          <p>
            <strong>Status:</strong> <StatusBadge value={selected.status} />
          </p>
          <p>
            <strong>Warehouse:</strong>{" "}
            {selected.fulfillment_warehouse_id || "-"}
          </p>
          <p>
            <strong>Reservation Expires:</strong>{" "}
            {formatDateTime(selected.reservation_expires_at)}
          </p>

          <h4>Items</h4>
          <ul className="plain-list">
            {(selected.items || []).map((item) => (
              <li key={item.listing_id}>
                {item.listing_title} × {item.quantity} —{" "}
                {formatCurrency(item.line_total)}
              </li>
            ))}
          </ul>

          <h4>Benefit Issuances</h4>
          <ul className="plain-list">
            {(selected.applied_benefits || []).map((benefit) => (
              <li key={benefit.id}>
                {benefit.name} ({benefit.type}) —{" "}
                {formatCurrency(benefit.applied_value)}
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
