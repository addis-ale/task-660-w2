Business Logic Questions Log (questions.md)
1. Tier Upgrade Timing

Question: When does a Member tier upgrade take effect after crossing a spend threshold?
My Understanding: Upgrade should apply immediately after qualifying purchase is confirmed.
Solution: Trigger tier recalculation on order completion event and update tier in real time.

2. Tier Downgrade Rules

Question: Are tiers permanent or do they downgrade after inactivity or time period?
My Understanding: Likely time-based (e.g., yearly reset).
Solution: Add tier_valid_until and recalculate tiers periodically (e.g., yearly job).

3. Benefit Stacking Priority

Question: If multiple benefits apply but cannot stack, which one takes priority?
My Understanding: Highest monetary value benefit should apply.
Solution: Implement benefit ranking logic based on discount value or priority flag.

4. Exclusive Pricing Scope

Question: Does exclusive pricing override all other discounts or combine partially?
My Understanding: Exclusive pricing replaces base price and blocks other discounts.
Solution: Apply exclusive price first, disable other discount rules when active.

5. Trending Listings Calculation

Question: How is “Trending This Week” defined? Views, purchases, or searches?
My Understanding: Combination of views + orders within last 7 days.
Solution: Weighted score = (views * 0.4 + orders * 0.6) over 7 days.

6. Distance Calculation Without Maps

Question: How is distance sorting handled without map APIs?
My Understanding: Manual address input converted to coordinates locally.
Solution: Store lat/long per listing and use Haversine formula for distance sorting.

7. Inventory Reservation Conflict

Question: What happens if multiple users reserve the last item simultaneously?
My Understanding: First valid transaction gets reservation; others fail.
Solution: Use DB row-level locking or atomic decrement with validation.

8. Reservation Expiry Handling

Question: What happens if user abandons checkout during 30-minute hold?
My Understanding: Reservation expires automatically.
Solution: Background job clears expired holds and restores stock.

9. Multi-Warehouse Fulfillment Logic

Question: How does the system choose which warehouse fulfills an order?
My Understanding: Nearest warehouse with available stock.
Solution: Sort warehouses by distance + stock availability, select optimal one.

10. Incident Severity Levels

Question: Are all incidents treated equally or do they have severity levels?
My Understanding: Should have severity (low, medium, high).
Solution: Add severity field to tickets and adjust SLA rules accordingly.

11. SLA Escalation Ownership

Question: When escalated, does Moderator take full ownership or share with original handler?
My Understanding: Moderator overrides and becomes primary handler.
Solution: Reassign ticket ownership on escalation event.

12. Appeal Review Authority

Question: Who reviews appeals, Moderator or Administrator?
My Understanding: Moderator handles first-level, Admin handles final decisions.
Solution: Multi-stage appeal workflow with escalation path.

13. Evidence Validation Rules

Question: Are uploaded files validated beyond size and type?
My Understanding: Basic validation only (size, format).
Solution: Restrict MIME types and enforce max 5 files, 10MB each.

14. Low Stock Threshold Customization

Question: Is the “Low Stock < 5 units” threshold fixed or configurable per product?
My Understanding: Should be configurable per listing.
Solution: Add low_stock_threshold field per product.

15. Risk Analytics Thresholds

Question: What defines “repeat incidents” for risk flags?
My Understanding: More than 3 incidents in 30 days triggers flag.
Solution: Query ticket history and apply threshold-based flagging.

16. Data Deletion Scope

Question: Does account deletion remove transaction history or anonymize it?
My Understanding: Data retained for audit but anonymized.
Solution: Replace personal identifiers with hashed values while keeping records.