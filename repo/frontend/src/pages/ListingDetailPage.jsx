import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { listingService } from "../api/listingService";
import { useAuth } from "../auth/AuthContext";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { EmptyState } from "../components/EmptyState";
import { Skeleton } from "../components/Skeleton";
import { useToast } from "../components/ToastProvider";
import { useCart } from "../hooks/useCart";
import { formatCurrency } from "../utils/formatting";

export default function ListingDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const { showToast } = useToast();
  const cart = useCart();
  const [listing, setListing] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    listingService
      .detail(id)
      .then((data) => setListing(data))
      .catch((error) =>
        showToast(error?.message || "Failed to load listing", "error"),
      )
      .finally(() => setLoading(false));
  }, [id, showToast]);

  if (loading) {
    return <Skeleton className="detail-skeleton" />;
  }

  if (!listing) {
    return (
      <EmptyState
        title="Listing unavailable"
        message="The listing may have been removed."
      />
    );
  }

  return (
    <div className="detail-layout">
      <Card>
        <div className="detail-image">Image Placeholder</div>
        <h1>{listing.title}</h1>
        <p>{listing.description || "No description provided."}</p>
        <p>
          <strong>{formatCurrency(listing.price)}</strong> ·{" "}
          {listing.neighborhood || "Neighborhood n/a"}
        </p>
        <div className="tag-list">
          {(listing.tags || []).map((tag) => (
            <span key={tag} className="tag-chip">
              {tag}
            </span>
          ))}
        </div>
        <Button
          onClick={() => {
            cart.addItem(listing, 1);
            showToast("Added to cart", "success");
          }}
        >
          Add to Cart
        </Button>
      </Card>

      <Card>
        <h3>Seller Info</h3>
        <p>Seller ID: {listing.seller_id}</p>

        <h3>Tier Pricing</h3>
        {user?.role === "MEMBER" && listing.tier_pricing ? (
          <div className="tier-pricing-box">
            <strong>
              {formatCurrency(listing.tier_pricing.exclusive_price)}
            </strong>
            <p>{listing.tier_pricing.applicable_tier}</p>
            <small>{listing.tier_pricing.note}</small>
          </div>
        ) : (
          <p>
            Standard price applies. Membership benefits shown at checkout when
            eligible.
          </p>
        )}

        <h3>Benefit Tips</h3>
        <ul className="plain-list">
          <li>As a Silver member, you save 10% on Crafts.</li>
          <li>Exclusive pricing cannot stack with percentage discounts.</li>
          <li>Free shipping stacks with non-exclusive benefits.</li>
        </ul>
      </Card>

      {Array.isArray(listing.stock_summary) &&
        listing.stock_summary.length > 0 && (
          <Card>
            <h3>Warehouse Stock Summary</h3>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Warehouse</th>
                    <th>Available</th>
                    <th>Reserved</th>
                    <th>Threshold</th>
                  </tr>
                </thead>
                <tbody>
                  {listing.stock_summary.map((row) => (
                    <tr key={row.warehouse_id}>
                      <td>{row.warehouse_name}</td>
                      <td>{row.available_qty}</td>
                      <td>{row.reserved_qty}</td>
                      <td>{row.low_stock_threshold}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}
    </div>
  );
}
