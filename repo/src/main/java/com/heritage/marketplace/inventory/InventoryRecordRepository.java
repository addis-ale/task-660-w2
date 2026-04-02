package com.heritage.marketplace.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface InventoryRecordRepository extends JpaRepository<InventoryRecord, UUID> {

    List<InventoryRecord> findByListing_Id(UUID listingId);

    List<InventoryRecord> findByWarehouse_Id(UUID warehouseId);

    List<InventoryRecord> findByListing_IdAndWarehouse_Id(UUID listingId, UUID warehouseId);

    List<InventoryRecord> findByListing_IdIn(Collection<UUID> listingIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT ir
        FROM InventoryRecord ir
        WHERE ir.warehouse.id = :warehouseId
          AND ir.listing.id IN :listingIds
        """)
    List<InventoryRecord> lockByWarehouseAndListings(
        @Param("warehouseId") UUID warehouseId,
        @Param("listingIds") Collection<UUID> listingIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ir FROM InventoryRecord ir WHERE ir.id = :inventoryId")
    Optional<InventoryRecord> lockById(@Param("inventoryId") UUID inventoryId);

    @Query("""
        SELECT ir
        FROM InventoryRecord ir
        WHERE ir.availableQty < ir.lowStockThreshold
          AND (:warehouseId IS NULL OR ir.warehouse.id = :warehouseId)
        """)
    List<InventoryRecord> findLowStock(@Param("warehouseId") UUID warehouseId);
}
