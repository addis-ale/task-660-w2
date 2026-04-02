package com.heritage.marketplace.inventory;

import com.heritage.marketplace.auth.JwtUserPrincipal;
import com.heritage.marketplace.common.api.ApiMeta;
import com.heritage.marketplace.common.api.ApiResponse;
import com.heritage.marketplace.inventory.dto.InventoryDocumentMovementResponse;
import com.heritage.marketplace.inventory.dto.InventoryDocumentRequest;
import com.heritage.marketplace.inventory.dto.InventoryMovementResponse;
import com.heritage.marketplace.inventory.dto.InventoryResponse;
import com.heritage.marketplace.inventory.dto.LowStockAlertResponse;
import com.heritage.marketplace.inventory.dto.StocktakeItemResponse;
import com.heritage.marketplace.inventory.dto.StocktakeRequest;
import com.heritage.marketplace.inventory.dto.UpdateThresholdRequest;
import com.heritage.marketplace.inventory.dto.WarehouseRequest;
import com.heritage.marketplace.inventory.dto.WarehouseResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/warehouses")
    @PreAuthorize("hasAnyRole('SELLER','WAREHOUSE_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> warehouses() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.listWarehouses()));
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> createWarehouse(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(inventoryService.createWarehouse(request)));
    }

    @PutMapping("/warehouses/{warehouseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateWarehouse(
        @PathVariable UUID warehouseId,
        @Valid @RequestBody WarehouseRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.updateWarehouse(warehouseId, request)));
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('SELLER','WAREHOUSE_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> inventory(
        @RequestParam(required = false) UUID listingId,
        @RequestParam(required = false) UUID warehouseId,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.inventory(listingId, warehouseId, principal)));
    }

    @PatchMapping("/inventory/{inventoryId}/threshold")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<ApiResponse<InventoryResponse>> updateThreshold(
        @PathVariable UUID inventoryId,
        @Valid @RequestBody UpdateThresholdRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            inventoryService.updateThreshold(inventoryId, request.lowStockThreshold(), principal)
        ));
    }

    @PostMapping("/inventory/documents")
    @PreAuthorize("hasAnyRole('SELLER','WAREHOUSE_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<List<InventoryDocumentMovementResponse>>> createDocument(
        @Valid @RequestBody InventoryDocumentRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.processDocument(request, principal)));
    }

    @PostMapping("/inventory/stocktake")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<List<StocktakeItemResponse>>> stocktake(
        @Valid @RequestBody StocktakeRequest request,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.stocktake(request, principal)));
    }

    @GetMapping("/inventory/{inventoryId}/movements")
    @PreAuthorize("hasAnyRole('SELLER','WAREHOUSE_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<List<InventoryMovementResponse>>> movementHistory(
        @PathVariable UUID inventoryId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        int safeSize = Math.max(1, Math.min(100, pageSize));
        int safePage = Math.max(0, page);
        Page<InventoryMovementResponse> movementPage = inventoryService.movementHistory(
            inventoryId,
            PageRequest.of(safePage, safeSize),
            principal
        );

        ApiMeta meta = ApiMeta.of(safePage, safeSize, movementPage.getTotalElements(), movementPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(movementPage.getContent(), meta));
    }

    @GetMapping("/inventory/alerts/low-stock")
    @PreAuthorize("hasAnyRole('SELLER','WAREHOUSE_STAFF','ADMIN')")
    public ResponseEntity<ApiResponse<List<LowStockAlertResponse>>> lowStockAlerts(
        @RequestParam(required = false) UUID warehouseId,
        @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.lowStockAlerts(warehouseId, principal)));
    }
}
