package com.heritage.marketplace.order;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.common.service.AuditLogService;
import com.heritage.marketplace.common.util.GeoDistanceUtil;
import com.heritage.marketplace.inventory.DocumentRefGenerator;
import com.heritage.marketplace.inventory.InventoryDocumentType;
import com.heritage.marketplace.inventory.InventoryRecord;
import com.heritage.marketplace.inventory.InventoryService;
import com.heritage.marketplace.inventory.Warehouse;
import com.heritage.marketplace.listing.Listing;
import com.heritage.marketplace.listing.ListingRepository;
import com.heritage.marketplace.listing.ListingStatus;
import com.heritage.marketplace.order.dto.CreateOrderItemRequest;
import com.heritage.marketplace.order.dto.CreateOrderRequest;
import com.heritage.marketplace.order.dto.OrderBenefitResponse;
import com.heritage.marketplace.order.dto.OrderItemResponse;
import com.heritage.marketplace.order.dto.OrderResponse;
import com.heritage.marketplace.tier.BenefitEvaluationService;
import com.heritage.marketplace.tier.BenefitPackage;
import com.heritage.marketplace.tier.BenefitPackageRepository;
import com.heritage.marketplace.tier.BenefitType;
import com.heritage.marketplace.tier.Membership;
import com.heritage.marketplace.tier.MembershipRepository;
import com.heritage.marketplace.tier.TierConfig;
import com.heritage.marketplace.tier.TierConfigRepository;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InternalTenderRecordRepository internalTenderRecordRepository;
    private final BenefitIssuanceRepository benefitIssuanceRepository;
    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final InventoryService inventoryService;
    private final MembershipRepository membershipRepository;
    private final TierConfigRepository tierConfigRepository;
    private final BenefitPackageRepository benefitPackageRepository;
    private final BenefitEvaluationService benefitEvaluationService;
    private final DocumentRefGenerator documentRefGenerator;
    private final AuditLogService auditLogService;
    private final GeoDistanceUtil geoDistanceUtil;

    public OrderService(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        InternalTenderRecordRepository internalTenderRecordRepository,
        BenefitIssuanceRepository benefitIssuanceRepository,
        UserRepository userRepository,
        ListingRepository listingRepository,
        InventoryService inventoryService,
        MembershipRepository membershipRepository,
        TierConfigRepository tierConfigRepository,
        BenefitPackageRepository benefitPackageRepository,
        BenefitEvaluationService benefitEvaluationService,
        DocumentRefGenerator documentRefGenerator,
        AuditLogService auditLogService,
        GeoDistanceUtil geoDistanceUtil
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.internalTenderRecordRepository = internalTenderRecordRepository;
        this.benefitIssuanceRepository = benefitIssuanceRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.inventoryService = inventoryService;
        this.membershipRepository = membershipRepository;
        this.tierConfigRepository = tierConfigRepository;
        this.benefitPackageRepository = benefitPackageRepository;
        this.benefitEvaluationService = benefitEvaluationService;
        this.documentRefGenerator = documentRefGenerator;
        this.auditLogService = auditLogService;
        this.geoDistanceUtil = geoDistanceUtil;
    }

    @Transactional
    public OrderResponse createOrder(UUID memberId, CreateOrderRequest request, String idempotencyKey, String ipAddress) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "X-Idempotency-Key header is required");
        }

        Optional<Order> existingByIdempotency = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByIdempotency.isPresent()) {
            return toOrderResponse(existingByIdempotency.get());
        }

        User member = userRepository.findById(memberId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Member was not found"));

        Map<UUID, Integer> quantitiesByListing = collapseQuantities(request.items());
        Map<UUID, Listing> listings = loadListings(quantitiesByListing.keySet());

        Warehouse warehouse = chooseFulfillmentWarehouse(quantitiesByListing, request);
        List<InventoryRecord> lockedInventories = inventoryService.lockInventoriesByWarehouseAndListings(
            warehouse.getId(),
            new ArrayList<>(quantitiesByListing.keySet())
        );

        Map<UUID, InventoryRecord> inventoryByListing = lockedInventories.stream()
            .collect(Collectors.toMap(ir -> ir.getListing().getId(), Function.identity()));

        List<Map<String, Object>> unavailableItems = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : quantitiesByListing.entrySet()) {
            InventoryRecord inventory = inventoryByListing.get(entry.getKey());
            int available = inventory == null ? 0 : inventory.getAvailableQty();
            if (available < entry.getValue()) {
                unavailableItems.add(Map.of(
                    "listingId", entry.getKey(),
                    "requestedQty", entry.getValue(),
                    "availableQty", available
                ));
            }
        }
        if (!unavailableItems.isEmpty()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "STOCK_UNAVAILABLE",
                "Insufficient stock for one or more items",
                Map.of("unavailableItems", unavailableItems)
            );
        }

        Membership membership = membershipRepository.findByUser_Id(memberId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "MEMBERSHIP_NOT_FOUND", "Membership record not found for member"));
        List<BenefitPackage> tierBenefits = benefitPackageRepository.findByTier_Id(membership.getTier().getId());

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal discountAmount = BigDecimal.ZERO;
        Map<UUID, BigDecimal> appliedBenefitTotals = new LinkedHashMap<>();
        List<OrderItemDraft> drafts = new ArrayList<>();

        for (Map.Entry<UUID, Integer> entry : quantitiesByListing.entrySet()) {
            Listing listing = listings.get(entry.getKey());
            int quantity = entry.getValue();
            BigDecimal lineBase = listing.getPrice().multiply(BigDecimal.valueOf(quantity));
            totalAmount = totalAmount.add(lineBase);

            List<BenefitPackage> applicable = tierBenefits.stream()
                .filter(bp -> isBenefitApplicable(bp, listing))
                .toList();
            List<BenefitPackage> selected = benefitEvaluationService.selectApplicableBenefits(applicable);

            BenefitPackage appliedPriceBenefit = selected.stream()
                .filter(bp -> bp.getType() == BenefitType.EXCLUSIVE_PRICE || bp.getType() == BenefitType.PERCENTAGE_DISCOUNT)
                .findFirst()
                .orElse(null);

            BigDecimal lineDiscount = BigDecimal.ZERO;
            if (appliedPriceBenefit != null) {
                if (appliedPriceBenefit.getType() == BenefitType.EXCLUSIVE_PRICE) {
                    BigDecimal lineExclusive = appliedPriceBenefit.getValue().multiply(BigDecimal.valueOf(quantity));
                    lineDiscount = lineBase.subtract(lineExclusive).max(BigDecimal.ZERO);
                } else if (appliedPriceBenefit.getType() == BenefitType.PERCENTAGE_DISCOUNT) {
                    lineDiscount = lineBase.multiply(appliedPriceBenefit.getValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                }
                appliedBenefitTotals.merge(appliedPriceBenefit.getId(), lineDiscount, BigDecimal::add);
            }

            discountAmount = discountAmount.add(lineDiscount);
            drafts.add(new OrderItemDraft(listing, quantity, appliedPriceBenefit));
        }

        BigDecimal finalAmount = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        for (Map.Entry<UUID, Integer> entry : quantitiesByListing.entrySet()) {
            InventoryRecord inventory = inventoryByListing.get(entry.getKey());
            int qty = entry.getValue();
            inventory.setAvailableQty(inventory.getAvailableQty() - qty);
            inventory.setReservedQty(inventory.getReservedQty() + qty);
            inventoryService.saveInventory(inventory);
        }

        String reservationRef = documentRefGenerator.next(InventoryDocumentType.RESERVATION);
        for (Map.Entry<UUID, Integer> entry : quantitiesByListing.entrySet()) {
            InventoryRecord inventory = inventoryByListing.get(entry.getKey());
            int qty = entry.getValue();
            inventoryService.createSystemMovement(
                inventory,
                InventoryDocumentType.RESERVATION,
                reservationRef,
                -qty,
                memberId,
                warehouse.getId(),
                "Stock reserved for order checkout"
            );
        }

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setMember(member);
        order.setStatus(OrderStatus.RESERVED);
        order.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        order.setDiscountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP));
        order.setFinalAmount(finalAmount);
        order.setFulfillmentWarehouse(warehouse);
        order.setReservationExpiresAt(LocalDateTime.now().plusMinutes(30));
        order.setIdempotencyKey(idempotencyKey);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        List<OrderItem> items = new ArrayList<>();
        for (OrderItemDraft draft : drafts) {
            OrderItem item = new OrderItem();
            item.setId(UUID.randomUUID());
            item.setOrder(order);
            item.setListing(draft.listing());
            item.setQuantity(draft.quantity());
            item.setUnitPrice(draft.listing().getPrice());
            item.setAppliedBenefit(draft.appliedBenefit());
            items.add(item);
        }
        orderItemRepository.saveAll(items);

        auditLogService.log(
            "ORDER",
            order.getId(),
            "CREATE_RESERVED",
            memberId,
            Map.of(
                "status", order.getStatus().name(),
                "totalAmount", order.getTotalAmount(),
                "discountAmount", order.getDiscountAmount(),
                "finalAmount", order.getFinalAmount(),
                "warehouseId", warehouse.getId()
            ),
            ipAddress
        );

        return toOrderResponse(order, items, appliedBenefitTotals);
    }

    @Transactional
    public OrderResponse confirmOrder(UUID orderId, UUID memberId, String idempotencyKey, String ipAddress) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "X-Idempotency-Key header is required");
        }

        Optional<InternalTenderRecord> existingTender = internalTenderRecordRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTender.isPresent()) {
            return toOrderResponse(existingTender.get().getOrder());
        }

        Order order = orderRepository.lockById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found"));
        if (!order.getMember().getId().equals(memberId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You can only confirm your own order");
        }
        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATE", "Only RESERVED orders can be confirmed");
        }
        if (order.getReservationExpiresAt() != null && order.getReservationExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_EXPIRED", "Order reservation has expired");
        }

        List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
        List<UUID> listingIds = items.stream().map(oi -> oi.getListing().getId()).toList();

        List<InventoryRecord> lockedInventories = inventoryService.lockInventoriesByWarehouseAndListings(
            order.getFulfillmentWarehouse().getId(),
            listingIds
        );
        Map<UUID, InventoryRecord> inventoryByListing = lockedInventories.stream()
            .collect(Collectors.toMap(ir -> ir.getListing().getId(), Function.identity()));

        String deductionRef = documentRefGenerator.next(InventoryDocumentType.ORDER_DEDUCTION);
        for (OrderItem item : items) {
            InventoryRecord inventory = inventoryByListing.get(item.getListing().getId());
            if (inventory == null || inventory.getReservedQty() < item.getQuantity()) {
                throw new ApiException(HttpStatus.CONFLICT, "STOCK_UNAVAILABLE", "Reserved stock no longer available for confirmation");
            }

            inventory.setReservedQty(inventory.getReservedQty() - item.getQuantity());
            inventoryService.saveInventory(inventory);

            inventoryService.createSystemMovement(
                inventory,
                InventoryDocumentType.ORDER_DEDUCTION,
                deductionRef,
                -item.getQuantity(),
                memberId,
                order.getFulfillmentWarehouse().getId(),
                "Order confirmed and stock deducted"
            );
        }

        InternalTenderRecord tender = new InternalTenderRecord();
        tender.setId(UUID.randomUUID());
        tender.setOrder(order);
        tender.setType(TenderType.PAYMENT);
        tender.setAmount(order.getFinalAmount());
        tender.setStatus(TenderStatus.COMPLETED);
        tender.setIdempotencyKey(idempotencyKey);
        tender.setReconciliationRef("PAY-" + order.getId().toString().substring(0, 8));
        tender.setCreatedAt(LocalDateTime.now());
        internalTenderRecordRepository.save(tender);

        order.setStatus(OrderStatus.CONFIRMED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        Membership membership = membershipRepository.findByUser_Id(memberId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "MEMBERSHIP_NOT_FOUND", "Membership record not found"));
        BigDecimal newTotalSpend = membership.getTotalSpend().add(order.getFinalAmount());
        membership.setTotalSpend(newTotalSpend);

        TierConfig tierForSpend = tierConfigRepository.findBestTierForSpend(newTotalSpend).orElse(membership.getTier());
        if (tierForSpend.getRank() > membership.getTier().getRank()) {
            membership.setTier(tierForSpend);
            membership.setUpgradedAt(LocalDateTime.now());
            membership.setTierValidUntil(LocalDate.now().plusYears(1));
        }
        membershipRepository.save(membership);

        Map<UUID, BigDecimal> benefitTotals = new LinkedHashMap<>();
        for (OrderItem item : items) {
            if (item.getAppliedBenefit() == null) {
                continue;
            }

            BigDecimal appliedValue = calculateAppliedValue(item);
            benefitTotals.merge(item.getAppliedBenefit().getId(), appliedValue, BigDecimal::add);

            BenefitIssuance issuance = new BenefitIssuance();
            issuance.setId(UUID.randomUUID());
            issuance.setMembership(membership);
            issuance.setBenefit(item.getAppliedBenefit());
            issuance.setOrder(order);
            issuance.setAppliedValue(appliedValue);
            issuance.setIssuedAt(LocalDateTime.now());
            benefitIssuanceRepository.save(issuance);
        }

        for (OrderItem item : items) {
            Listing listing = item.getListing();
            listing.setOrderCount7d(listing.getOrderCount7d() + item.getQuantity());
            listingRepository.save(listing);
        }

        auditLogService.log(
            "ORDER",
            order.getId(),
            "CONFIRM",
            memberId,
            Map.of("status", order.getStatus().name(), "idempotencyKey", idempotencyKey),
            ipAddress
        );

        return toOrderResponse(order, items, benefitTotals);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, JwtUserPrincipal principal, String ipAddress) {
        Order order = orderRepository.lockById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found"));

        boolean isOwner = order.getMember().getId().equals(principal.userId());
        if (!(isOwner || principal.role() == UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You cannot cancel this order");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toOrderResponse(order);
        }

        List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
        List<UUID> listingIds = items.stream().map(oi -> oi.getListing().getId()).toList();
        List<InventoryRecord> lockedInventories = inventoryService.lockInventoriesByWarehouseAndListings(
            order.getFulfillmentWarehouse().getId(),
            listingIds
        );
        Map<UUID, InventoryRecord> inventoryByListing = lockedInventories.stream()
            .collect(Collectors.toMap(ir -> ir.getListing().getId(), Function.identity()));

        if (order.getStatus() == OrderStatus.RESERVED) {
            String releaseRef = documentRefGenerator.next(InventoryDocumentType.RESERVATION_RELEASE);
            String rollbackRef = documentRefGenerator.next(InventoryDocumentType.CANCELLATION_ROLLBACK);

            for (OrderItem item : items) {
                InventoryRecord inventory = inventoryByListing.get(item.getListing().getId());
                inventory.setAvailableQty(inventory.getAvailableQty() + item.getQuantity());
                inventory.setReservedQty(Math.max(0, inventory.getReservedQty() - item.getQuantity()));
                inventoryService.saveInventory(inventory);

                inventoryService.createSystemMovement(
                    inventory,
                    InventoryDocumentType.RESERVATION_RELEASE,
                    releaseRef,
                    item.getQuantity(),
                    principal.userId(),
                    order.getFulfillmentWarehouse().getId(),
                    "Reservation released due to cancellation"
                );

                inventoryService.createSystemMovement(
                    inventory,
                    InventoryDocumentType.CANCELLATION_ROLLBACK,
                    rollbackRef,
                    item.getQuantity(),
                    principal.userId(),
                    order.getFulfillmentWarehouse().getId(),
                    "Cancellation rollback"
                );
            }
        } else if (order.getStatus() == OrderStatus.CONFIRMED) {
            String rollbackRef = documentRefGenerator.next(InventoryDocumentType.CANCELLATION_ROLLBACK);
            for (OrderItem item : items) {
                InventoryRecord inventory = inventoryByListing.get(item.getListing().getId());
                inventory.setAvailableQty(inventory.getAvailableQty() + item.getQuantity());
                inventoryService.saveInventory(inventory);

                inventoryService.createSystemMovement(
                    inventory,
                    InventoryDocumentType.CANCELLATION_ROLLBACK,
                    rollbackRef,
                    item.getQuantity(),
                    principal.userId(),
                    order.getFulfillmentWarehouse().getId(),
                    "Confirmed order rollback"
                );
            }

            InternalTenderRecord refund = new InternalTenderRecord();
            refund.setId(UUID.randomUUID());
            refund.setOrder(order);
            refund.setType(TenderType.REFUND);
            refund.setAmount(order.getFinalAmount());
            refund.setStatus(TenderStatus.COMPLETED);
            refund.setIdempotencyKey("refund-" + order.getId() + "-" + System.currentTimeMillis());
            refund.setReconciliationRef("REF-" + order.getId().toString().substring(0, 8));
            refund.setCreatedAt(LocalDateTime.now());
            internalTenderRecordRepository.save(refund);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        auditLogService.log(
            "ORDER",
            order.getId(),
            "CANCEL",
            principal.userId(),
            Map.of("status", order.getStatus().name()),
            ipAddress
        );

        return toOrderResponse(order, items, Map.of());
    }

    @Transactional
    public OrderResponse fulfillOrder(UUID orderId, UUID actorId, String ipAddress) {
        Order order = orderRepository.lockById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found"));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATE", "Only CONFIRMED orders can be fulfilled");
        }

        order.setStatus(OrderStatus.FULFILLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        auditLogService.log(
            "ORDER",
            order.getId(),
            "FULFILL",
            actorId,
            Map.of("status", order.getStatus().name()),
            ipAddress
        );

        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> myOrders(UUID memberId, OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null
            ? orderRepository.findByMember_IdOrderByCreatedAtDesc(memberId, pageable)
            : orderRepository.findByMember_IdAndStatusOrderByCreatedAtDesc(memberId, status, pageable);

        return page.map(this::toOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, JwtUserPrincipal principal) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order was not found"));

        if (principal.role() != UserRole.ADMIN && !order.getMember().getId().equals(principal.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You cannot access this order");
        }

        return toOrderResponse(order);
    }

    private Map<UUID, Integer> collapseQuantities(List<CreateOrderItemRequest> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (CreateOrderItemRequest item : items) {
            quantities.merge(item.listingId(), item.quantity(), Integer::sum);
        }
        return quantities;
    }

    private Map<UUID, Listing> loadListings(Set<UUID> listingIds) {
        Map<UUID, Listing> listings = new HashMap<>();
        for (UUID listingId : listingIds) {
            Listing listing = listingRepository.findByIdAndStatusNot(listingId, ListingStatus.REMOVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "Listing was not found: " + listingId));
            listings.put(listingId, listing);
        }
        return listings;
    }

    private Warehouse chooseFulfillmentWarehouse(Map<UUID, Integer> quantitiesByListing, CreateOrderRequest request) {
        List<InventoryRecord> all = inventoryService.findInventoriesByListingIds(new ArrayList<>(quantitiesByListing.keySet()));
        Map<UUID, List<InventoryRecord>> byWarehouse = all.stream()
            .collect(Collectors.groupingBy(ir -> ir.getWarehouse().getId()));

        List<WarehouseCandidate> candidates = new ArrayList<>();
        for (List<InventoryRecord> warehouseRecords : byWarehouse.values()) {
            Map<UUID, InventoryRecord> byListing = warehouseRecords.stream()
                .collect(Collectors.toMap(ir -> ir.getListing().getId(), Function.identity(), (a, b) -> a));

            boolean canFulfill = true;
            int stockScore = 0;
            for (Map.Entry<UUID, Integer> entry : quantitiesByListing.entrySet()) {
                InventoryRecord record = byListing.get(entry.getKey());
                if (record == null || record.getAvailableQty() < entry.getValue()) {
                    canFulfill = false;
                    break;
                }
                stockScore += record.getAvailableQty();
            }

            if (canFulfill) {
                Warehouse warehouse = warehouseRecords.get(0).getWarehouse();
                double distance = calculateDistance(request, warehouse);
                candidates.add(new WarehouseCandidate(warehouse, distance, stockScore));
            }
        }

        if (candidates.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "STOCK_UNAVAILABLE", "No single warehouse can fulfill all requested items");
        }

        candidates.sort(
            Comparator.comparingDouble(WarehouseCandidate::distanceMiles)
                .thenComparing(Comparator.comparingInt(WarehouseCandidate::stockScore).reversed())
        );
        return candidates.get(0).warehouse();
    }

    private double calculateDistance(CreateOrderRequest request, Warehouse warehouse) {
        if (request.deliveryLat() == null || request.deliveryLng() == null) {
            return Double.MAX_VALUE;
        }
        if (warehouse.getLatitude() == null || warehouse.getLongitude() == null) {
            return Double.MAX_VALUE;
        }
        return geoDistanceUtil.haversineMiles(
            request.deliveryLat().doubleValue(),
            request.deliveryLng().doubleValue(),
            warehouse.getLatitude().doubleValue(),
            warehouse.getLongitude().doubleValue()
        );
    }

    private boolean isBenefitApplicable(BenefitPackage benefit, Listing listing) {
        LocalDate today = LocalDate.now();
        boolean categoryOk = benefit.getScopeCategory() == null || benefit.getScopeCategory().equalsIgnoreCase(listing.getCategory());
        boolean sellerOk = benefit.getScopeSeller() == null || benefit.getScopeSeller().getId().equals(listing.getSeller().getId());
        boolean startOk = benefit.getScopeDateStart() == null || !today.isBefore(benefit.getScopeDateStart());
        boolean endOk = benefit.getScopeDateEnd() == null || !today.isAfter(benefit.getScopeDateEnd());
        return categoryOk && sellerOk && startOk && endOk;
    }

    private BigDecimal calculateAppliedValue(OrderItem item) {
        BenefitPackage benefit = item.getAppliedBenefit();
        if (benefit == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        if (benefit.getType() == BenefitType.EXCLUSIVE_PRICE) {
            BigDecimal exclusive = benefit.getValue().multiply(BigDecimal.valueOf(item.getQuantity()));
            return base.subtract(exclusive).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        if (benefit.getType() == BenefitType.PERCENTAGE_DISCOUNT) {
            return base.multiply(benefit.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
        Map<UUID, BigDecimal> benefitTotals = new LinkedHashMap<>();
        for (OrderItem item : items) {
            if (item.getAppliedBenefit() != null) {
                benefitTotals.merge(item.getAppliedBenefit().getId(), calculateAppliedValue(item), BigDecimal::add);
            }
        }
        return toOrderResponse(order, items, benefitTotals);
    }

    private OrderResponse toOrderResponse(Order order, List<OrderItem> items, Map<UUID, BigDecimal> benefitTotals) {
        Map<UUID, BenefitPackage> benefitById = items.stream()
            .map(OrderItem::getAppliedBenefit)
            .filter(b -> b != null)
            .collect(Collectors.toMap(BenefitPackage::getId, Function.identity(), (a, b) -> a));

        List<OrderItemResponse> itemResponses = items.stream()
            .map(item -> new OrderItemResponse(
                item.getListing().getId(),
                item.getListing().getTitle(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                item.getAppliedBenefit() == null ? null : item.getAppliedBenefit().getId()
            ))
            .toList();

        List<OrderBenefitResponse> benefitResponses = benefitTotals.entrySet().stream()
            .map(entry -> {
                BenefitPackage benefit = benefitById.get(entry.getKey());
                if (benefit == null) {
                    return null;
                }
                return new OrderBenefitResponse(benefit.getId(), benefit.getName(), benefit.getType(), entry.getValue());
            })
            .filter(response -> response != null)
            .toList();

        return new OrderResponse(
            order.getId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getDiscountAmount(),
            order.getFinalAmount(),
            order.getFulfillmentWarehouse() == null ? null : order.getFulfillmentWarehouse().getId(),
            order.getReservationExpiresAt(),
            order.getIdempotencyKey(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            itemResponses,
            benefitResponses
        );
    }

    private record WarehouseCandidate(Warehouse warehouse, double distanceMiles, int stockScore) {
    }

    private record OrderItemDraft(Listing listing, int quantity, BenefitPackage appliedBenefit) {
    }
}
