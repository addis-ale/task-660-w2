import { useEffect, useState } from "react";
import { inventoryService, listingService } from "../api";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { EmptyState } from "../components/EmptyState";
import { Skeleton } from "../components/Skeleton";
import { StatusBadge } from "../components/StatusBadge";
import { useToast } from "../components/ToastProvider";
import { formatDateTime } from "../utils/formatting";

export default function WarehouseConsolePage() {
  const { showToast } = useToast();
  const [warehouses, setWarehouses] = useState([]);
  const [listings, setListings] = useState([]);
  const [inventory, setInventory] = useState([]);
  const [movements, setMovements] = useState([]);
  const [docForm, setDocForm] = useState({
    type: "INBOUND",
    warehouse_id: "",
    listing_id: "",
    quantity: 1,
  });
  const [stocktake, setStocktake] = useState({
    warehouse_id: "",
    listing_id: "",
    actual_count: 0,
  });
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      inventoryService.warehouses(),
      listingService.search({ page: 0, pageSize: 100, sort: "newest" }),
      inventoryService.inventory({}),
      inventoryService.lowStockAlerts({}),
    ])
      .then(([warehouseRows, listingRows, inventoryRows, alertRows]) => {
        setWarehouses(warehouseRows || []);
        setListings(listingRows || []);
        setInventory(inventoryRows || []);
        setAlerts(alertRows || []);
      })
      .catch((error) =>
        showToast(
          error?.message || "Failed loading warehouse console",
          "error",
        ),
      )
      .finally(() => setLoading(false));
  }, [showToast]);

  const submitDocument = async (event) => {
    event.preventDefault();
    try {
      await inventoryService.createDocument({
        type: docForm.type,
        warehouse_id: docForm.warehouse_id,
        items: [
          {
            listing_id: docForm.listing_id,
            quantity: Number(docForm.quantity),
            notes: "Warehouse console",
          },
        ],
      });
      showToast("Document submitted", "success");
      const [inventoryRows, alertRows] = await Promise.all([
        inventoryService.inventory({}),
        inventoryService.lowStockAlerts({}),
      ]);
      setInventory(inventoryRows || []);
      setAlerts(alertRows || []);
    } catch (error) {
      showToast(error?.message || "Document submission failed", "error");
    }
  };

  const submitStocktake = async (event) => {
    event.preventDefault();
    try {
      await inventoryService.stocktake({
        warehouse_id: stocktake.warehouse_id,
        items: [
          {
            listing_id: stocktake.listing_id,
            actual_count: Number(stocktake.actual_count),
            notes: "Cycle count",
          },
        ],
      });
      showToast("Stocktake completed", "success");
      const inventoryRows = await inventoryService.inventory({});
      setInventory(inventoryRows || []);
    } catch (error) {
      showToast(error?.message || "Stocktake failed", "error");
    }
  };

  const fetchMovementHistory = async (inventoryId) => {
    try {
      const rows = await inventoryService.movements(inventoryId, {
        page: 0,
        pageSize: 30,
      });
      setMovements(rows || []);
    } catch (error) {
      showToast(error?.message || "Movement history failed", "error");
    }
  };

  if (loading) {
    return (
      <div className="page-grid">
        <Card>
          <Skeleton className="skeleton-heading" />
          <Skeleton className="skeleton-block" />
        </Card>
        <Card>
          <Skeleton className="skeleton-heading" />
          <Skeleton className="skeleton-block" />
        </Card>
        <Card>
          <Skeleton className="skeleton-heading" />
          <Skeleton className="skeleton-table" />
          <Skeleton className="skeleton-table" />
        </Card>
        <Card>
          <Skeleton className="skeleton-heading" />
          <Skeleton className="skeleton-block" />
        </Card>
      </div>
    );
  }

  return (
    <div className="page-grid">
      <Card>
        <h2>Inbound / Outbound</h2>
        <form className="filters-grid" onSubmit={submitDocument}>
          <label>
            Type
            <select
              value={docForm.type}
              onChange={(e) =>
                setDocForm((prev) => ({ ...prev, type: e.target.value }))
              }
            >
              <option value="INBOUND">INBOUND</option>
              <option value="OUTBOUND">OUTBOUND</option>
            </select>
          </label>
          <label>
            Warehouse
            <select
              required
              value={docForm.warehouse_id}
              onChange={(e) =>
                setDocForm((prev) => ({
                  ...prev,
                  warehouse_id: e.target.value,
                }))
              }
            >
              <option value="">Select</option>
              {warehouses.map((warehouse) => (
                <option key={warehouse.id} value={warehouse.id}>
                  {warehouse.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Listing
            <select
              required
              value={docForm.listing_id}
              onChange={(e) =>
                setDocForm((prev) => ({ ...prev, listing_id: e.target.value }))
              }
            >
              <option value="">Select</option>
              {listings.map((listing) => (
                <option key={listing.id} value={listing.id}>
                  {listing.title}
                </option>
              ))}
            </select>
          </label>
          <label>
            Quantity
            <input
              min={1}
              type="number"
              value={docForm.quantity}
              onChange={(e) =>
                setDocForm((prev) => ({ ...prev, quantity: e.target.value }))
              }
            />
          </label>
          <Button type="submit">Apply</Button>
        </form>
      </Card>

      <Card>
        <h2>Stocktake</h2>
        <form className="filters-grid" onSubmit={submitStocktake}>
          <label>
            Warehouse
            <select
              required
              value={stocktake.warehouse_id}
              onChange={(e) =>
                setStocktake((prev) => ({
                  ...prev,
                  warehouse_id: e.target.value,
                }))
              }
            >
              <option value="">Select</option>
              {warehouses.map((warehouse) => (
                <option key={warehouse.id} value={warehouse.id}>
                  {warehouse.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Listing
            <select
              required
              value={stocktake.listing_id}
              onChange={(e) =>
                setStocktake((prev) => ({
                  ...prev,
                  listing_id: e.target.value,
                }))
              }
            >
              <option value="">Select</option>
              {listings.map((listing) => (
                <option key={listing.id} value={listing.id}>
                  {listing.title}
                </option>
              ))}
            </select>
          </label>
          <label>
            Actual Count
            <input
              min={0}
              type="number"
              value={stocktake.actual_count}
              onChange={(e) =>
                setStocktake((prev) => ({
                  ...prev,
                  actual_count: e.target.value,
                }))
              }
            />
          </label>
          <Button type="submit" variant="secondary">
            Submit Stocktake
          </Button>
        </form>
      </Card>

      <Card>
        <h2>Movement Log</h2>
        {inventory.length === 0 ? (
          <EmptyState
            title="No inventory records"
            message="Inventory records will appear here once documents are processed."
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Inventory</th>
                  <th>Warehouse</th>
                  <th>Qty</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {inventory.map((row) => (
                  <tr key={row.id}>
                    <td>{row.id.slice(0, 8)}...</td>
                    <td>{row.warehouse_name}</td>
                    <td>{row.available_qty}</td>
                    <td>
                      {row.is_low_stock ? (
                        <StatusBadge value="ESCALATED" />
                      ) : (
                        <StatusBadge value="RESOLVED" />
                      )}
                    </td>
                    <td>
                      <Button
                        variant="ghost"
                        onClick={() => fetchMovementHistory(row.id)}
                      >
                        View Movements
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {(movements || []).length > 0 && (
          <div className="table-wrap">
            <table className="data-table compact">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Doc Ref</th>
                  <th>Qty</th>
                  <th>Notes</th>
                  <th>Time</th>
                </tr>
              </thead>
              <tbody>
                {movements.map((row) => (
                  <tr key={row.id}>
                    <td>{row.document_type}</td>
                    <td>{row.document_ref}</td>
                    <td>{row.quantity_change}</td>
                    <td>{row.notes}</td>
                    <td>{formatDateTime(row.created_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <h2>Low Stock Alerts</h2>
        {alerts.length === 0 ? (
          <EmptyState
            title="No low stock alerts"
            message="All inventory levels are above their thresholds."
          />
        ) : (
          <div className="alert-list">
            {alerts.map((alert) => (
              <article key={alert.inventory_id} className="alert-item">
                <strong>{alert.severity} Alert</strong>
                <p>
                  Inventory {alert.inventory_id.slice(0, 8)}... in warehouse{" "}
                  {alert.warehouse_id.slice(0, 8)}...
                </p>
                <p>
                  Available {alert.available_qty} / Threshold{" "}
                  {alert.low_stock_threshold}
                </p>
              </article>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
