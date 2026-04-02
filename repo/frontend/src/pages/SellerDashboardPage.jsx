import { useEffect, useMemo, useState } from "react";
import { inventoryService, listingService } from "../api";
import { useAuth } from "../auth/AuthContext";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { EmptyState } from "../components/EmptyState";
import { useToast } from "../components/ToastProvider";
import { formatCurrency } from "../utils/formatting";

export default function SellerDashboardPage() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [listings, setListings] = useState([]);
  const [inventoryRows, setInventoryRows] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [newListing, setNewListing] = useState({
    title: "",
    category: "",
    price: "",
  });
  const [docForm, setDocForm] = useState({
    type: "INBOUND",
    warehouse_id: "",
    listing_id: "",
    quantity: 1,
  });

  const sellerListings = useMemo(
    () =>
      listings.filter(
        (item) => item.seller_id === user?.id || user?.role === "ADMIN",
      ),
    [listings, user],
  );

  useEffect(() => {
    listingService
      .search({ page: 0, pageSize: 100, sort: "newest" })
      .then(setListings)
      .catch(() => setListings([]));
    inventoryService
      .inventory({})
      .then(setInventoryRows)
      .catch(() => setInventoryRows([]));
    inventoryService
      .warehouses()
      .then(setWarehouses)
      .catch(() => setWarehouses([]));
  }, []);

  const createListing = async (event) => {
    event.preventDefault();
    try {
      const payload = {
        title: newListing.title,
        category: newListing.category,
        price: Number(newListing.price),
        description: "Seller listing",
        tags: [],
      };
      const created = await listingService.create(payload);
      setListings((prev) => [created, ...prev]);
      setNewListing({ title: "", category: "", price: "" });
      showToast("Listing created", "success");
    } catch (error) {
      showToast(error?.message || "Failed to create listing", "error");
    }
  };

  const deleteListing = async (id) => {
    if (!window.confirm("Remove this listing?")) {
      return;
    }
    try {
      await listingService.remove(id);
      setListings((prev) => prev.filter((row) => row.id !== id));
      showToast("Listing removed", "success");
    } catch (error) {
      showToast(error?.message || "Failed to remove listing", "error");
    }
  };

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
            notes: "Seller dashboard",
          },
        ],
      });
      const refreshed = await inventoryService.inventory({});
      setInventoryRows(refreshed || []);
      showToast("Inventory document submitted", "success");
    } catch (error) {
      showToast(error?.message || "Document failed", "error");
    }
  };

  return (
    <div className="page-grid">
      <Card>
        <h2>Listing Management</h2>
        <form className="filters-grid" onSubmit={createListing}>
          <label>
            Title
            <input
              required
              value={newListing.title}
              onChange={(e) =>
                setNewListing((prev) => ({ ...prev, title: e.target.value }))
              }
            />
          </label>
          <label>
            Category
            <input
              required
              value={newListing.category}
              onChange={(e) =>
                setNewListing((prev) => ({ ...prev, category: e.target.value }))
              }
            />
          </label>
          <label>
            Price
            <input
              required
              type="number"
              min="0.01"
              step="0.01"
              value={newListing.price}
              onChange={(e) =>
                setNewListing((prev) => ({ ...prev, price: e.target.value }))
              }
            />
          </label>
          <Button type="submit">Create Listing</Button>
        </form>

        {sellerListings.length === 0 ? (
          <EmptyState
            title="No listings"
            message="Create your first listing above."
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Category</th>
                  <th>Price</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {sellerListings.map((listing) => (
                  <tr key={listing.id}>
                    <td>{listing.title}</td>
                    <td>{listing.category}</td>
                    <td>{formatCurrency(listing.price)}</td>
                    <td>
                      <Button
                        variant="danger"
                        onClick={() => deleteListing(listing.id)}
                      >
                        Remove
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <h2>Inventory Overview</h2>
        <div className="inventory-grid">
          {inventoryRows.map((row) => (
            <article
              key={row.id}
              className={`inventory-card ${row.is_low_stock ? "inventory-low" : "inventory-ok"}`}
            >
              <h4>{row.warehouse_name}</h4>
              <p>Listing: {row.listing_id}</p>
              <p>Available: {row.available_qty}</p>
              <p>Reserved: {row.reserved_qty}</p>
              <p>Threshold: {row.low_stock_threshold}</p>
              {row.is_low_stock ? (
                <span className="pill-danger">Low Stock</span>
              ) : (
                <span className="pill-ok">In Stock</span>
              )}
            </article>
          ))}
        </div>
      </Card>

      <Card>
        <h2>Inbound / Outbound Document</h2>
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
              {sellerListings.map((listing) => (
                <option key={listing.id} value={listing.id}>
                  {listing.title}
                </option>
              ))}
            </select>
          </label>
          <label>
            Quantity
            <input
              type="number"
              min={1}
              value={docForm.quantity}
              onChange={(e) =>
                setDocForm((prev) => ({ ...prev, quantity: e.target.value }))
              }
            />
          </label>
          <Button type="submit">Submit Document</Button>
        </form>
      </Card>
    </div>
  );
}
