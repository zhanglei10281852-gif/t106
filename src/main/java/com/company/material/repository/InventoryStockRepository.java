package com.company.material.repository;

import com.company.material.entity.InventoryStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {
    Optional<InventoryStock> findByMaterialIdAndWarehouseId(Long materialId, Long warehouseId);

    boolean existsByMaterialIdAndWarehouseId(Long materialId, Long warehouseId);

    List<InventoryStock> findByMaterialId(Long materialId);

    Page<InventoryStock> findByMaterialId(Long materialId, Pageable pageable);

    List<InventoryStock> findByWarehouseId(Long warehouseId);

    Page<InventoryStock> findByWarehouseId(Long warehouseId, Pageable pageable);

    @Query("SELECT s FROM InventoryStock s WHERE s.materialId = :materialId AND s.warehouseId = :warehouseId")
    Optional<InventoryStock> findStock(@Param("materialId") Long materialId, @Param("warehouseId") Long warehouseId);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM InventoryStock s WHERE s.materialId = :materialId")
    Integer sumQuantityByMaterialId(@Param("materialId") Long materialId);

    @Query("SELECT s FROM InventoryStock s WHERE s.quantity > 0")
    Page<InventoryStock> findAvailableStocks(Pageable pageable);
}
