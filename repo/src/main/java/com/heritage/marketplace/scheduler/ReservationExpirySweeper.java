package com.heritage.marketplace.scheduler;

import com.heritage.marketplace.audit.AuditService;
import com.heritage.marketplace.inventory.DocumentRefGenerator;
import com.heritage.marketplace.inventory.InventoryDocumentType;
import com.heritage.marketplace.inventory.InventoryMovementService;
import com.heritage.marketplace.inventory.InventoryRecord;
import com.heritage.marketplace.inventory.InventoryRecordRepository;
import com.heritage.marketplace.order.Order;
import com.heritage.marketplace.order.OrderItem;
import com.heritage.marketplace.order.OrderItemRepository;
import com.heritage.marketplace.order.OrderRepository;
import com.heritage.marketplace.order.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationExpirySweeper {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final InventoryMovementService inventoryMovementService;
    private final DocumentRefGenerator documentRefGenerator;
    private final AuditService auditService;

    public ReservationExpirySweeper(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        InventoryRecordRepository inventoryRecordRepository,
        InventoryMovementService inventoryMovementService,
        DocumentRefGenerator documentRefGenerator,
        AuditService auditService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.inventoryMovementService = inventoryMovementService;
        this.documentRefGenerator = documentRefGenerator;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.reservation-expiry.fixed-delay-ms:60000}")
    @Transactional
    public void sweepExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> expired = orderRepository.findByStatusAndReservationExpiresAtBefore(OrderStatus.RESERVED, now);

        for (Order candidate : expired) {
            Order order = orderRepository.lockById(candidate.getId()).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.RESERVED) {
                continue;
            }
            if (order.getReservationExpiresAt() == null || !order.getReservationExpiresAt().isBefore(now)) {
                continue;
            }

            List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
            if (order.getFulfillmentWarehouse() != null && !items.isEmpty()) {
                List<UUID> listingIds = items.stream().map(item -> item.getListing().getId()).toList();
                List<InventoryRecord> locked = inventoryRecordRepository.lockByWarehouseAndListings(
                    order.getFulfillmentWarehouse().getId(),
                    listingIds
                );
                Map<UUID, InventoryRecord> byListing = locked.stream()
                    .collect(Collectors.toMap(ir -> ir.getListing().getId(), Function.identity(), (a, b) -> a));

                String documentRef = documentRefGenerator.next(InventoryDocumentType.RESERVATION_RELEASE);
                for (OrderItem item : items) {
                    InventoryRecord inventory = byListing.get(item.getListing().getId());
                    if (inventory == null) {
                        continue;
                    }

                    int releasableQty = Math.min(item.getQuantity(), inventory.getReservedQty());
                    if (releasableQty <= 0) {
                        continue;
                    }

                    inventory.setReservedQty(inventory.getReservedQty() - releasableQty);
                    inventory.setAvailableQty(inventory.getAvailableQty() + releasableQty);
                    inventoryRecordRepository.save(inventory);

                    inventoryMovementService.record(
                        inventory,
                        InventoryDocumentType.RESERVATION_RELEASE,
                        documentRef,
                        releasableQty,
                        null,
                        order.getFulfillmentWarehouse().getId(),
                        "Reservation expired and stock released"
                    );
                }
            }

            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(now);
            orderRepository.save(order);

            auditService.log(
                "ORDER",
                order.getId(),
                "AUTO_CANCEL_EXPIRED_RESERVATION",
                null,
                Map.of("status", OrderStatus.RESERVED.name(), "reservationExpiresAt", order.getReservationExpiresAt()),
                Map.of("status", OrderStatus.CANCELLED.name()),
                "system"
            );
        }
    }
}
