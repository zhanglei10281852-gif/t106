package com.company.material.repository;

import com.company.material.entity.SparePartConsumption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SparePartConsumptionRepository extends JpaRepository<SparePartConsumption, Long> {
    List<SparePartConsumption> findByRepairOrderId(Long repairOrderId);

    @Query("SELECT COALESCE(SUM(s.totalPrice), 0) FROM SparePartConsumption s WHERE s.repairOrderId = :repairOrderId")
    BigDecimal sumTotalPriceByRepairOrderId(@Param("repairOrderId") Long repairOrderId);

    @Query("SELECT s.materialId, SUM(s.quantity) as qty, SUM(s.totalPrice) as amount " +
           "FROM SparePartConsumption s WHERE s.consumedAt BETWEEN :start AND :end " +
           "GROUP BY s.materialId ORDER BY amount DESC")
    List<Object[]> getSparePartRanking(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Page<SparePartConsumption> findByMaterialId(Long materialId, Pageable pageable);
}
