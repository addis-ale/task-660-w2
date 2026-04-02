package com.heritage.marketplace.inventory;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.inventory.dto.InventoryDocumentItemRequest;
import com.heritage.marketplace.inventory.dto.InventoryDocumentMovementResponse;
import com.heritage.marketplace.inventory.dto.InventoryDocumentRequest;
import com.heritage.marketplace.inventory.dto.InventoryMovementResponse;
import com.heritage.marketplace.inventory.dto.InventoryResponse;
import com.heritage.marketplace.inventory.dto.LowStockAlertResponse;
import com.heritage.marketplace.inventory.dto.StocktakeItemRequest;
import com.heritage.marketplace.inventory.dto.StocktakeItemResponse;
import com.heritage.marketplace.inventory.dto.StocktakeRequest;
import com.heritage.marketplace.inventory.dto.WarehouseRequest;
import com.heritage.marketplace.inventory.dto.WarehouseResponse;
import com.heritage.marketplace.listing.Listing;
import com.heritage.marketplace.listing.ListingRepository;
import com.heritage.marketplace.user.UserRole;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final WarehouseRepository warehouseRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryMovementService inventoryMovementService;
    private final ListingRepository listingRepository;
    private final DocumentRefGenerator documentRefGenerator;

    public InventoryService(
        WarehouseRepository warehouseRepository,
        InventoryRecordRepository inventoryRecordRepository,
        InventoryMovementRepository inventoryMovementRepository,
        InventoryMovementService inventoryMovementService,
        ListingRepository listingRepository,
        DocumentRefGenerator documentRefGenerator
    ) {
        this.warehouseRepository = warehouseRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.inventoryMovementService = inventoryMovementService;
        this.listingRepository = listingRepository;
        this.documentRefGenerator = documentRefGenerator;
    }

    public List<WarehouseResponse> listWarehouses() {
        return warehouseRepository.findAll().stream()
            .sorted(Comparator.comparing(Warehouse::getName, String.CASE_INSENSITIVE_ORDER))
            .map(this::toWarehouseResponse)
            .toList();
    }

    @Transactional
    public WarehouseResponse createWarehouse(WarehouseRequest request) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setName(request.name().trim());
        warehouse.setAddress(request.address().trim());
        warehouse.setLatitude(request.latitude());
        warehouse.setLongitude(request.longitude());
        warehouse.setStatus(request.status());
        warehouseRepository.save(warehouse);
        return toWarehouseResponse(warehouse);
    }

    @Transactional
    public WarehouseResponse updateWarehouse(UUID warehouseId, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse was not found"));

        warehouse.setName(request.name().trim());
        warehouse.setAddress(request.address().trim());
        warehouse.setLatitude(request.latitude());
        warehouse.setLongitude(request.longitude());
        warehouse.setStatus(request.status());
        warehouseRepository.save(warehouse);
        return toWarehouseResponse(warehouse);
    }

    public List<InventoryResponse> inventory(UUID listingId, UUID warehouseId, JwtUserPrincipal principal) {
        List<InventoryRecord> records;
        if (listingId != null && warehouseId != null) {
            records = inventoryRecordRepository.findByListing_IdAndWarehouse_Id(listingId, warehouseId);
        } else if (listingId != null) {
            records = inventoryRecordRepository.findByListing_Id(listingId);
        } else if (warehouseId != null) {
            records = inventoryRecordRepository.findByWarehouse_Id(warehouseId);
        } else {
            records = inventoryRecordRepository.findAll();
        }

        return records.stream()
            .filter(ir -> canSeeInventory(principal, ir))
            .map(this::toInventoryResponse)
            .toList();
    }

    @Transactional
    public InventoryResponse updateThreshold(UUID inventoryId, int lowStockThreshold, JwtUserPrincipal principal) {
        InventoryRecord record = inventoryRecordRepository.findById(inventoryId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND", "Inventory record not found"));

        ensureCanManageSellerInventory(principal, record);
        record.setLowStockThreshold(lowStockThreshold);
        inventoryRecordRepository.save(record);
        return toInventoryResponse(record);
    }

    @Transactional
    public List<InventoryDocumentMovementResponse> processDocument(InventoryDocumentRequest request, JwtUserPrincipal principal) {
        if (request.type() != InventoryDocumentType.INBOUND && request.type() != InventoryDocumentType.OUTBOUND) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_TYPE", "Only INBOUND and OUTBOUND are allowed for this endpoint");
        }

        warehouseRepository.findById(request.warehouseId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse was not found"));

        String documentRef = documentRefGenerator.next(request.type());
        List<InventoryDocumentMovementResponse> responses = new ArrayList<>();

        for (InventoryDocumentItemRequest item : request.items()) {
            InventoryRecord inventory = resolveInventoryForDocument(item, request.warehouseId(), request.type());
            ensureCanManageSellerInventory(principal, inventory);

            int delta = request.type() == InventoryDocumentType.INBOUND ? item.quantity() : -item.quantity();
            int nextAvailable = inventory.getAvailableQty() + delta;
            if (nextAvailable < 0) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Outbound quantity exceeds available stock");
            }

            inventory.setAvailableQty(nextAvailable);
            inventoryRecordRepository.save(inventory);

            InventoryMovement movement = inventoryMovementService.record(
                inventory,
                request.type(),
                documentRef,
                delta,
                principal.userId(),
                request.warehouseId(),
                item.notes()
            );

            responses.add(new InventoryDocumentMovementResponse(
                movement.getId(),
                inventory.getId(),
                inventory.getListing().getId(),
                inventory.getWarehouse().getId(),
                request.type(),
                documentRef,
                delta,
                inventory.getAvailableQty(),
                inventory.getAvailableQty() < inventory.getLowStockThreshold()
            ));
        }

        return responses;
    }

    @Transactional
    public List<StocktakeItemResponse> stocktake(StocktakeRequest request, JwtUserPrincipal principal) {
        warehouseRepository.findById(request.warehouseId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse was not found"));

        String documentRef = documentRefGenerator.next(InventoryDocumentType.STOCKTAKE);
        List<StocktakeItemResponse> responses = new ArrayList<>();

        for (StocktakeItemRequest item : request.items()) {
            InventoryRecord inventory = resolveInventoryForStocktake(item, request.warehouseId());
            ensureCanManageSellerInventory(principal, inventory);

            int previous = inventory.getAvailableQty();
            int adjustment = item.actualCount() - previous;
            inventory.setAvailableQty(item.actualCount());
            inventoryRecordRepository.save(inventory);

            inventoryMovementService.record(
                inventory,
                InventoryDocumentType.STOCKTAKE,
                documentRef,
                adjustment,
                principal.userId(),
                request.warehouseId(),
                item.notes()
            );

            responses.add(new StocktakeItemResponse(
                inventory.getId(),
                inventory.getListing().getId(),
                previous,
                item.actualCount(),
                adjustment,
                documentRef
            ));
        }

        return responses;
    }

    public Page<InventoryMovementResponse> movementHistory(UUID inventoryId, Pageable pageable, JwtUserPrincipal principal) {
        InventoryRecord inventory = inventoryRecordRepository.findById(inventoryId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND", "Inventory record not found"));
        if (!canSeeInventory(principal, inventory)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You cannot access this inventory history");
        }

        return inventoryMovementRepository.findByInventory_IdOrderByCreatedAtDesc(inventoryId, pageable)
            .map(movement -> new InventoryMovementResponse(
                movement.getId(),
                movement.getDocumentType(),
                movement.getDocumentRef(),
                movement.getQuantityChange(),
                movement.getOperator() == null ? null : movement.getOperator().getId(),
                movement.getWarehouse().getId(),
                movement.getNotes(),
                movement.getCreatedAt()
            ));
    }

    public List<LowStockAlertResponse> lowStockAlerts(UUID warehouseId, JwtUserPrincipal principal) {
        return inventoryRecordRepository.findLowStock(warehouseId)
            .stream()
            .filter(ir -> canSeeInventory(principal, ir))
            .map(this::toLowStockAlert)
            .toList();
    }

    private InventoryRecord resolveInventoryForDocument(
        InventoryDocumentItemRequest item,
        UUID warehouseId,
        InventoryDocumentType type
    ) {
        if (item.inventoryId() != null) {
            return inventoryRecordRepository.lockById(item.inventoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND", "Inventory record not found"));
        }

        if (item.listingId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ITEM", "Each item must include inventory_id or listing_id");
        }

        List<InventoryRecord> existing = inventoryRecordRepository.findByListing_IdAndWarehouse_Id(item.listingId(), warehouseId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        if (type == InventoryDocumentType.OUTBOUND) {
            throw new ApiException(HttpStatus.CONFLICT, "INVENTORY_NOT_FOUND", "Cannot apply outbound movement to missing inventory");
        }

        Listing listing = listingRepository.findById(item.listingId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "Listing was not found"));
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse was not found"));

        InventoryRecord record = new InventoryRecord();
        record.setId(UUID.randomUUID());
        record.setListing(listing);
        record.setWarehouse(warehouse);
        record.setAvailableQty(0);
        record.setReservedQty(0);
        record.setLowStockThreshold(5);
        return inventoryRecordRepository.save(record);
    }

    private InventoryRecord resolveInventoryForStocktake(StocktakeItemRequest item, UUID warehouseId) {
        if (item.inventoryId() != null) {
            return inventoryRecordRepository.lockById(item.inventoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND", "Inventory record not found"));
        }

        if (item.listingId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ITEM", "Each stocktake item must include inventory_id or listing_id");
        }

        List<InventoryRecord> existing = inventoryRecordRepository.findByListing_IdAndWarehouse_Id(item.listingId(), warehouseId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        Listing listing = listingRepository.findById(item.listingId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "Listing was not found"));
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse was not found"));

        InventoryRecord record = new InventoryRecord();
        record.setId(UUID.randomUUID());
        record.setListing(listing);
        record.setWarehouse(warehouse);
        record.setAvailableQty(0);
        record.setReservedQty(0);
        record.setLowStockThreshold(5);
        return inventoryRecordRepository.save(record);
    }

    private boolean canSeeInventory(JwtUserPrincipal principal, InventoryRecord record) {
        if (principal.role() == UserRole.ADMIN || principal.role() == UserRole.WAREHOUSE_STAFF) {
            return true;
        }
        if (principal.role() == UserRole.SELLER) {
            return record.getListing().getSeller().getId().equals(principal.userId());
        }
        return false;
    }

    private void ensureCanManageSellerInventory(JwtUserPrincipal principal, InventoryRecord record) {
        if (principal.role() == UserRole.ADMIN || principal.role() == UserRole.WAREHOUSE_STAFF) {
            return;
        }
        if (principal.role() == UserRole.SELLER && record.getListing().getSeller().getId().equals(principal.userId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You cannot manage this inventory record");
    }

    private WarehouseResponse toWarehouseResponse(Warehouse warehouse) {
        return new WarehouseResponse(
            warehouse.getId(),
            warehouse.getName(),
            warehouse.getAddress(),
            warehouse.getLatitude(),
            warehouse.getLongitude(),
            warehouse.getStatus()
        );
    }

    private InventoryResponse toInventoryResponse(InventoryRecord ir) {
        return new InventoryResponse(
            ir.getId(),
            ir.getListing().getId(),
            ir.getWarehouse().getId(),
            ir.getWarehouse().getName(),
            ir.getAvailableQty(),
            ir.getReservedQty(),
            ir.getLowStockThreshold(),
            ir.getAvailableQty() < ir.getLowStockThreshold()
        );
    }

    private LowStockAlertResponse toLowStockAlert(InventoryRecord ir) {
        String severity;
        if (ir.getAvailableQty() <= 0) {
            severity = "CRITICAL";
        } else if (ir.getAvailableQty() <= Math.max(1, ir.getLowStockThreshold() / 2)) {
            severity = "HIGH";
        } else {
            severity = "MEDIUM";
        }

        return new LowStockAlertResponse(
            ir.getId(),
            ir.getListing().getId(),
            ir.getWarehouse().getId(),
            ir.getAvailableQty(),
            ir.getLowStockThreshold(),
            severity,
            "RED"
        );
    }

    @Transactional
    public InventoryMovement createSystemMovement(
        InventoryRecord inventory,
        InventoryDocumentType documentType,
        String documentRef,
        int quantityChange,
        UUID operatorId,
        UUID warehouseId,
        String notes
    ) {
        return inventoryMovementService.record(inventory, documentType, documentRef, quantityChange, operatorId, warehouseId, notes);
    }

    @Transactional
    public InventoryRecord saveInventory(InventoryRecord inventoryRecord) {
        return inventoryRecordRepository.save(inventoryRecord);
    }

    public InventoryRecord lockInventory(UUID inventoryId) {
        return inventoryRecordRepository.lockById(inventoryId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND", "Inventory record not found"));
    }

    public List<InventoryRecord> lockInventoriesByWarehouseAndListings(UUID warehouseId, List<UUID> listingIds) {
        return inventoryRecordRepository.lockByWarehouseAndListings(warehouseId, listingIds);
    }

    public List<InventoryRecord> findInventoriesByListingIds(List<UUID> listingIds) {
        return inventoryRecordRepository.findByListing_IdIn(listingIds);
    }

    public Warehouse getWarehouse(UUID warehouseId) {
        return warehouseRepository.findById(warehouseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse was not found"));
    }
}
