package com.heritage.marketplace.inventory;

import com.heritage.marketplace.common.exception.ApiException;
import com.heritage.marketplace.user.User;
import com.heritage.marketplace.user.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;

    public InventoryMovementService(
        InventoryMovementRepository inventoryMovementRepository,
        UserRepository userRepository,
        WarehouseRepository warehouseRepository
    ) {
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
    }

    public InventoryMovement record(
        InventoryRecord inventory,
        InventoryDocumentType documentType,
        String documentRef,
        int quantityChange,
        UUID operatorId,
        UUID warehouseId,
        String notes
    ) {
        InventoryMovement movement = new InventoryMovement();
        movement.setId(UUID.randomUUID());
        movement.setInventory(inventory);
        movement.setDocumentType(documentType);
        movement.setDocumentRef(documentRef);
        movement.setQuantityChange(quantityChange);
        movement.setOperator(resolveOperator(operatorId));
        movement.setWarehouse(warehouseRepository.findById(warehouseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse was not found")));
        movement.setNotes(notes);
        movement.setCreatedAt(LocalDateTime.now());
        return inventoryMovementRepository.save(movement);
    }

    private User resolveOperator(UUID operatorId) {
        if (operatorId == null) {
            return null;
        }
        return userRepository.findById(operatorId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Operator user was not found"));
    }
}
