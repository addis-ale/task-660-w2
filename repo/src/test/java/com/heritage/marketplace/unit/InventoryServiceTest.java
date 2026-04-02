package com.heritage.marketplace.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.inventory.*;
import com.heritage.marketplace.inventory.dto.WarehouseRequest;
import com.heritage.marketplace.inventory.dto.WarehouseResponse;
import com.heritage.marketplace.inventory.dto.InventoryResponse;
import com.heritage.marketplace.listing.Listing;
import com.heritage.marketplace.listing.ListingRepository;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.heritage.marketplace.inventory.WarehouseStatus;
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
class InventoryServiceTest {

    @Mock private WarehouseRepository warehouseRepository;
    @Mock private InventoryRecordRepository inventoryRecordRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;
    @Mock private InventoryMovementService inventoryMovementService;
    @Mock private ListingRepository listingRepository;
    @Mock private DocumentRefGenerator documentRefGenerator;

    @InjectMocks private InventoryService inventoryService;

    private Warehouse warehouse;
    private User seller;
    private Listing listing;
    private InventoryRecord inventoryRecord;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setName("Central Warehouse");
        warehouse.setAddress("100 Main Street");
        warehouse.setLatitude(BigDecimal.valueOf(40.7128));
        warehouse.setLongitude(BigDecimal.valueOf(-74.0060));
        warehouse.setStatus(WarehouseStatus.ACTIVE);

        seller = new User();
        seller.setId(UUID.randomUUID());
        seller.setDisplayName("Test Seller");
        seller.setRole(UserRole.SELLER);

        listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setSeller(seller);
        listing.setTitle("Test Item");

        inventoryRecord = new InventoryRecord();
        inventoryRecord.setId(UUID.randomUUID());
        inventoryRecord.setListing(listing);
        inventoryRecord.setWarehouse(warehouse);
        inventoryRecord.setAvailableQty(100);
        inventoryRecord.setReservedQty(10);
        inventoryRecord.setLowStockThreshold(5);
    }

    @Nested
    @DisplayName("listWarehouses")
    class ListWarehousesTests {

        @Test
        @DisplayName("should return warehouses sorted by name")
        void returnWarehousesSortedByName() {
            Warehouse wh2 = new Warehouse();
            wh2.setId(UUID.randomUUID());
            wh2.setName("Alpha Warehouse");
            wh2.setAddress("200 Oak Ave");
            wh2.setStatus(WarehouseStatus.ACTIVE);

            when(warehouseRepository.findAll()).thenReturn(List.of(warehouse, wh2));

            List<WarehouseResponse> result = inventoryService.listWarehouses();

            assertEquals(2, result.size());
            assertEquals("Alpha Warehouse", result.get(0).name());
            assertEquals("Central Warehouse", result.get(1).name());
        }
    }

    @Nested
    @DisplayName("createWarehouse")
    class CreateWarehouseTests {

        @Test
        @DisplayName("should create warehouse with trimmed fields")
        void createWarehouseSuccessfully() {
            when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

            WarehouseRequest request = new WarehouseRequest(
                "  New Warehouse  ", "  123 Test St  ",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(-73.0), WarehouseStatus.ACTIVE
            );

            WarehouseResponse response = inventoryService.createWarehouse(request);

            assertEquals("New Warehouse", response.name());
            assertEquals("123 Test St", response.address());
            assertEquals(WarehouseStatus.ACTIVE, response.status());
        }
    }

    @Nested
    @DisplayName("updateWarehouse")
    class UpdateWarehouseTests {

        @Test
        @DisplayName("should throw NOT_FOUND for nonexistent warehouse")
        void throwNotFoundForNonexistentWarehouse() {
            UUID warehouseId = UUID.randomUUID();
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

            WarehouseRequest request = new WarehouseRequest("Name", "Address", null, null, WarehouseStatus.ACTIVE);

            ApiException ex = assertThrows(ApiException.class,
                () -> inventoryService.updateWarehouse(warehouseId, request));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
            assertEquals("WAREHOUSE_NOT_FOUND", ex.getCode());
        }
    }

    @Nested
    @DisplayName("updateThreshold")
    class UpdateThresholdTests {

        @Test
        @DisplayName("should update low stock threshold for seller's own inventory")
        void updateThresholdForOwnInventory() {
            JwtUserPrincipal principal = new JwtUserPrincipal(seller.getId(), UserRole.SELLER);
            when(inventoryRecordRepository.findById(inventoryRecord.getId())).thenReturn(Optional.of(inventoryRecord));
            when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            InventoryResponse response = inventoryService.updateThreshold(inventoryRecord.getId(), 10, principal);

            assertEquals(10, response.lowStockThreshold());
        }

        @Test
        @DisplayName("should throw FORBIDDEN for seller managing other seller's inventory")
        void throwForbiddenForOtherSellersInventory() {
            JwtUserPrincipal otherSeller = new JwtUserPrincipal(UUID.randomUUID(), UserRole.SELLER);
            when(inventoryRecordRepository.findById(inventoryRecord.getId())).thenReturn(Optional.of(inventoryRecord));

            ApiException ex = assertThrows(ApiException.class,
                () -> inventoryService.updateThreshold(inventoryRecord.getId(), 10, otherSeller));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }

        @Test
        @DisplayName("should allow ADMIN to update any threshold")
        void allowAdminToUpdateAnyThreshold() {
            JwtUserPrincipal admin = new JwtUserPrincipal(UUID.randomUUID(), UserRole.ADMIN);
            when(inventoryRecordRepository.findById(inventoryRecord.getId())).thenReturn(Optional.of(inventoryRecord));
            when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(inv -> inv.getArgument(0));

            InventoryResponse response = inventoryService.updateThreshold(inventoryRecord.getId(), 15, admin);

            assertEquals(15, response.lowStockThreshold());
        }
    }

    @Nested
    @DisplayName("inventory filtering")
    class InventoryFilterTests {

        @Test
        @DisplayName("should filter inventory by listingId and warehouseId")
        void filterByBoth() {
            JwtUserPrincipal admin = new JwtUserPrincipal(UUID.randomUUID(), UserRole.ADMIN);
            when(inventoryRecordRepository.findByListing_IdAndWarehouse_Id(listing.getId(), warehouse.getId()))
                .thenReturn(List.of(inventoryRecord));

            List<InventoryResponse> result = inventoryService.inventory(listing.getId(), warehouse.getId(), admin);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("should hide other sellers inventory from SELLER role")
        void hideOtherSellersInventory() {
            JwtUserPrincipal otherSeller = new JwtUserPrincipal(UUID.randomUUID(), UserRole.SELLER);
            when(inventoryRecordRepository.findAll()).thenReturn(List.of(inventoryRecord));

            List<InventoryResponse> result = inventoryService.inventory(null, null, otherSeller);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should show all inventory to WAREHOUSE_STAFF")
        void showAllToWarehouseStaff() {
            JwtUserPrincipal staff = new JwtUserPrincipal(UUID.randomUUID(), UserRole.WAREHOUSE_STAFF);
            when(inventoryRecordRepository.findAll()).thenReturn(List.of(inventoryRecord));

            List<InventoryResponse> result = inventoryService.inventory(null, null, staff);

            assertEquals(1, result.size());
        }
    }
}
