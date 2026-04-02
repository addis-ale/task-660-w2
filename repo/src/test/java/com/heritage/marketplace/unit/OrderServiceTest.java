package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import com.heritage.marketplace.order.*;
import com.heritage.marketplace.order.dto.CreateOrderItemRequest;
import com.heritage.marketplace.order.dto.CreateOrderRequest;
import com.heritage.marketplace.order.dto.OrderResponse;
import com.heritage.marketplace.tier.*;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import com.heritage.marketplace.user.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private InternalTenderRecordRepository internalTenderRecordRepository;
    @Mock private BenefitIssuanceRepository benefitIssuanceRepository;
    @Mock private UserRepository userRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private InventoryService inventoryService;
    @Mock private MembershipRepository membershipRepository;
    @Mock private TierConfigRepository tierConfigRepository;
    @Mock private BenefitPackageRepository benefitPackageRepository;
    @Mock private BenefitEvaluationService benefitEvaluationService;
    @Mock private DocumentRefGenerator documentRefGenerator;
    @Mock private AuditLogService auditLogService;
    @Mock private GeoDistanceUtil geoDistanceUtil;

    @InjectMocks private OrderService orderService;

    private UUID memberId;
    private User member;
    private Listing listing;
    private Warehouse warehouse;
    private InventoryRecord inventoryRecord;
    private TierConfig tierConfig;
    private Membership membership;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        member = new User();
        member.setId(memberId);
        member.setDisplayName("TestMember");
        member.setRole(UserRole.MEMBER);

        UUID sellerId = UUID.randomUUID();
        User seller = new User();
        seller.setId(sellerId);
        seller.setDisplayName("TestSeller");
        seller.setRole(UserRole.SELLER);

        listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setTitle("Test Listing");
        listing.setPrice(BigDecimal.valueOf(100));
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setSeller(seller);
        listing.setCategory("furniture");
        listing.setViewCount(0);
        listing.setOrderCount7d(0);

        warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setName("Main Warehouse");
        warehouse.setLatitude(BigDecimal.valueOf(40.0));
        warehouse.setLongitude(BigDecimal.valueOf(-74.0));

        inventoryRecord = new InventoryRecord();
        inventoryRecord.setId(UUID.randomUUID());
        inventoryRecord.setListing(listing);
        inventoryRecord.setWarehouse(warehouse);
        inventoryRecord.setAvailableQty(50);
        inventoryRecord.setReservedQty(0);
        inventoryRecord.setLowStockThreshold(5);

        tierConfig = new TierConfig();
        tierConfig.setId(UUID.randomUUID());
        tierConfig.setName("Bronze");
        tierConfig.setRank(1);

        membership = new Membership();
        membership.setId(UUID.randomUUID());
        membership.setUser(member);
        membership.setTier(tierConfig);
        membership.setTotalSpend(BigDecimal.ZERO);
        membership.setTierValidUntil(LocalDate.now().plusYears(1));
    }

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("should reject null idempotency key")
        void rejectNullIdempotencyKey() {
            CreateOrderRequest request = new CreateOrderRequest(
                null, null,
                List.of(new CreateOrderItemRequest(listing.getId(), 2))
            );

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.createOrder(memberId, request, null, "127.0.0.1"));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertEquals("IDEMPOTENCY_KEY_REQUIRED", ex.getCode());
        }

        @Test
        @DisplayName("should reject blank idempotency key")
        void rejectBlankIdempotencyKey() {
            CreateOrderRequest request = new CreateOrderRequest(
                null, null,
                List.of(new CreateOrderItemRequest(listing.getId(), 1))
            );

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.createOrder(memberId, request, "  ", "127.0.0.1"));

            assertEquals("IDEMPOTENCY_KEY_REQUIRED", ex.getCode());
        }

        @Test
        @DisplayName("should return existing order for duplicate idempotency key")
        void returnExistingForDuplicateIdempotencyKey() {
            String idempotencyKey = "idem-123";
            Order existingOrder = buildOrder(OrderStatus.RESERVED);
            existingOrder.setIdempotencyKey(idempotencyKey);

            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));
            when(orderItemRepository.findByOrder_Id(existingOrder.getId())).thenReturn(List.of());

            CreateOrderRequest request = new CreateOrderRequest(
                null, null,
                List.of(new CreateOrderItemRequest(listing.getId(), 1))
            );

            OrderResponse response = orderService.createOrder(memberId, request, idempotencyKey, "127.0.0.1");

            assertNotNull(response);
            assertEquals(existingOrder.getId(), response.id());
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("should throw when member not found")
        void throwWhenMemberNotFound() {
            when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(userRepository.findById(memberId)).thenReturn(Optional.empty());

            CreateOrderRequest request = new CreateOrderRequest(
                null, null,
                List.of(new CreateOrderItemRequest(listing.getId(), 1))
            );

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.createOrder(memberId, request, "key-1", "127.0.0.1"));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("USER_NOT_FOUND", ex.getCode());
        }

        @Test
        @DisplayName("should throw STOCK_UNAVAILABLE when insufficient stock")
        void throwWhenInsufficientStock() {
            inventoryRecord.setAvailableQty(1);

            when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(userRepository.findById(memberId)).thenReturn(Optional.of(member));
            when(listingRepository.findByIdAndStatusNot(listing.getId(), ListingStatus.REMOVED))
                .thenReturn(Optional.of(listing));
            when(inventoryService.findInventoriesByListingIds(anyList())).thenReturn(List.of(inventoryRecord));

            CreateOrderRequest request = new CreateOrderRequest(
                null, null,
                List.of(new CreateOrderItemRequest(listing.getId(), 10))
            );

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.createOrder(memberId, request, "key-2", "127.0.0.1"));

            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
            assertEquals("STOCK_UNAVAILABLE", ex.getCode());
        }

        @Test
        @DisplayName("should create RESERVED order with correct totals")
        void createReservedOrderSuccessfully() {
            when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(userRepository.findById(memberId)).thenReturn(Optional.of(member));
            when(listingRepository.findByIdAndStatusNot(listing.getId(), ListingStatus.REMOVED))
                .thenReturn(Optional.of(listing));
            when(inventoryService.findInventoriesByListingIds(anyList())).thenReturn(List.of(inventoryRecord));
            when(inventoryService.lockInventoriesByWarehouseAndListings(any(), anyList()))
                .thenReturn(List.of(inventoryRecord));
            when(membershipRepository.findByUser_Id(memberId)).thenReturn(Optional.of(membership));
            when(benefitPackageRepository.findByTier_Id(tierConfig.getId())).thenReturn(List.of());
            when(benefitEvaluationService.selectApplicableBenefits(anyList())).thenReturn(List.of());
            when(documentRefGenerator.next(InventoryDocumentType.RESERVATION)).thenReturn("RES-001");
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            CreateOrderRequest request = new CreateOrderRequest(
                BigDecimal.valueOf(40.0), BigDecimal.valueOf(-74.0),
                List.of(new CreateOrderItemRequest(listing.getId(), 3))
            );

            OrderResponse response = orderService.createOrder(memberId, request, "key-3", "127.0.0.1");

            assertNotNull(response);
            assertEquals(OrderStatus.RESERVED, response.status());
            assertEquals(0, BigDecimal.valueOf(300).compareTo(response.totalAmount()));
            assertEquals(0, BigDecimal.ZERO.compareTo(response.discountAmount()));
            verify(inventoryService).saveInventory(argThat(ir -> ir.getReservedQty() == 3));
            verify(auditLogService).log(eq("ORDER"), any(), eq("CREATE_RESERVED"), eq(memberId), anyMap(), anyString());
        }
    }

    @Nested
    @DisplayName("confirmOrder")
    class ConfirmOrderTests {

        @Test
        @DisplayName("should reject null idempotency key on confirm")
        void rejectNullIdempotencyKeyOnConfirm() {
            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.confirmOrder(UUID.randomUUID(), memberId, null, "127.0.0.1"));
            assertEquals("IDEMPOTENCY_KEY_REQUIRED", ex.getCode());
        }

        @Test
        @DisplayName("should throw when confirming non-RESERVED order")
        void throwWhenConfirmingNonReservedOrder() {
            Order order = buildOrder(OrderStatus.CONFIRMED);
            order.getMember().setId(memberId);

            when(internalTenderRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.confirmOrder(order.getId(), memberId, "key-4", "127.0.0.1"));

            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
            assertEquals("INVALID_ORDER_STATE", ex.getCode());
        }

        @Test
        @DisplayName("should throw when reservation has expired")
        void throwWhenReservationExpired() {
            Order order = buildOrder(OrderStatus.RESERVED);
            order.getMember().setId(memberId);
            order.setReservationExpiresAt(LocalDateTime.now().minusMinutes(1));

            when(internalTenderRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.confirmOrder(order.getId(), memberId, "key-5", "127.0.0.1"));

            assertEquals("RESERVATION_EXPIRED", ex.getCode());
        }

        @Test
        @DisplayName("should throw FORBIDDEN when confirming another member's order")
        void throwForbiddenForOtherMembersOrder() {
            Order order = buildOrder(OrderStatus.RESERVED);

            when(internalTenderRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.confirmOrder(order.getId(), UUID.randomUUID(), "key-6", "127.0.0.1"));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }
    }

    @Nested
    @DisplayName("fulfillOrder")
    class FulfillOrderTests {

        @Test
        @DisplayName("should fulfill a CONFIRMED order")
        void fulfillConfirmedOrder() {
            Order order = buildOrder(OrderStatus.CONFIRMED);
            when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderItemRepository.findByOrder_Id(order.getId())).thenReturn(List.of());

            OrderResponse response = orderService.fulfillOrder(order.getId(), UUID.randomUUID(), "127.0.0.1");

            assertEquals(OrderStatus.FULFILLED, response.status());
            verify(auditLogService).log(eq("ORDER"), any(), eq("FULFILL"), any(), anyMap(), anyString());
        }

        @Test
        @DisplayName("should reject fulfillment of non-CONFIRMED order")
        void rejectFulfillmentOfNonConfirmedOrder() {
            Order order = buildOrder(OrderStatus.RESERVED);
            when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.fulfillOrder(order.getId(), UUID.randomUUID(), "127.0.0.1"));

            assertEquals("INVALID_ORDER_STATE", ex.getCode());
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrderTests {

        @Test
        @DisplayName("should return existing cancelled order without re-cancelling")
        void returnAlreadyCancelledOrder() {
            Order order = buildOrder(OrderStatus.CANCELLED);
            JwtUserPrincipal principal = new JwtUserPrincipal(order.getMember().getId(), UserRole.MEMBER);

            when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrder_Id(order.getId())).thenReturn(List.of());

            OrderResponse response = orderService.cancelOrder(order.getId(), principal, "127.0.0.1");

            assertEquals(OrderStatus.CANCELLED, response.status());
            verify(auditLogService, never()).log(anyString(), any(), eq("CANCEL"), any(), anyMap(), anyString());
        }

        @Test
        @DisplayName("should throw FORBIDDEN when non-owner non-admin cancels")
        void throwForbiddenForNonOwnerNonAdmin() {
            Order order = buildOrder(OrderStatus.RESERVED);
            JwtUserPrincipal principal = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MEMBER);

            when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.cancelOrder(order.getId(), principal, "127.0.0.1"));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }

        @Test
        @DisplayName("should allow ADMIN to cancel any order")
        void allowAdminCancelAnyOrder() {
            Order order = buildOrder(OrderStatus.RESERVED);
            JwtUserPrincipal adminPrincipal = new JwtUserPrincipal(UUID.randomUUID(), UserRole.ADMIN);

            when(orderRepository.lockById(order.getId())).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrder_Id(order.getId())).thenReturn(List.of());
            when(inventoryService.lockInventoriesByWarehouseAndListings(any(), anyList())).thenReturn(List.of());
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderResponse response = orderService.cancelOrder(order.getId(), adminPrincipal, "127.0.0.1");

            assertEquals(OrderStatus.CANCELLED, response.status());
        }
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrderTests {

        @Test
        @DisplayName("should throw NOT_FOUND for nonexistent order")
        void throwNotFoundForNonexistentOrder() {
            UUID orderId = UUID.randomUUID();
            JwtUserPrincipal principal = new JwtUserPrincipal(memberId, UserRole.MEMBER);
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.getOrder(orderId, principal));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        @Test
        @DisplayName("should throw FORBIDDEN when non-owner non-admin accesses")
        void throwForbiddenForNonOwnerNonAdmin() {
            Order order = buildOrder(OrderStatus.CONFIRMED);
            JwtUserPrincipal principal = new JwtUserPrincipal(UUID.randomUUID(), UserRole.MEMBER);

            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            ApiException ex = assertThrows(ApiException.class,
                () -> orderService.getOrder(order.getId(), principal));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }
    }

    private Order buildOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setMember(member);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.valueOf(300).setScale(2));
        order.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order.setFinalAmount(BigDecimal.valueOf(300).setScale(2));
        order.setFulfillmentWarehouse(warehouse);
        order.setReservationExpiresAt(LocalDateTime.now().plusMinutes(30));
        order.setIdempotencyKey("order-key-" + UUID.randomUUID());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}
