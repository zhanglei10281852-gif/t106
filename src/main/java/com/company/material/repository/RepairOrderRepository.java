package com.company.material.repository;

import com.company.material.entity.RepairOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, Long> {
    Optional<RepairOrder> findByOrderNo(String orderNo);
    boolean existsByOrderNo(String orderNo);
    Page<RepairOrder> findByStatus(String status, Pageable pageable);
    Page<RepairOrder> findByEquipmentId(Long equipmentId, Pageable pageable);
    Page<RepairOrder> findByAssignedWorker(String assignedWorker, Pageable pageable);
    List<RepairOrder> findByEquipmentIdAndStatusIn(Long equipmentId, List<String> statuses);

    @Query("SELECT r FROM RepairOrder r WHERE r.orderNo LIKE %:kw% OR r.faultDescription LIKE %:kw%")
    Page<RepairOrder> search(@Param("kw") String kw, Pageable pageable);

    @Query("SELECT r FROM RepairOrder r WHERE r.urgencyLevel = '特急停产' AND r.status NOT IN ('已完成', '已关闭') " +
           "ORDER BY r.reportTime DESC")
    List<RepairOrder> findUrgentOrders();

    @Query("SELECT r FROM RepairOrder r ORDER BY " +
           "CASE r.urgencyLevel WHEN '特急停产' THEN 1 WHEN '紧急' THEN 2 ELSE 3 END, " +
           "r.reportTime DESC")
    Page<RepairOrder> findAllWithPriority(Pageable pageable);

    @Query("SELECT COUNT(r) FROM RepairOrder r WHERE r.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(r) FROM RepairOrder r WHERE r.reportTime BETWEEN :start AND :end")
    long countByReportTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT r.equipmentId, COUNT(r) as cnt FROM RepairOrder r " +
           "WHERE r.reportTime BETWEEN :start AND :end " +
           "GROUP BY r.equipmentId ORDER BY cnt DESC")
    List<Object[]> countByEquipmentIdAndDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(r.totalCost), 0) FROM RepairOrder r WHERE r.equipmentId = :equipmentId")
    java.math.BigDecimal sumTotalCostByEquipmentId(@Param("equipmentId") Long equipmentId);

    @Query("SELECT r FROM RepairOrder r WHERE r.equipmentId = :equipmentId AND r.repairResult IS NOT NULL " +
           "ORDER BY r.reportTime DESC")
    List<RepairOrder> findCompletedOrdersByEquipmentId(@Param("equipmentId") Long equipmentId);

    @Query("SELECT r.assignedWorker, COUNT(r), COALESCE(SUM(r.repairHours), 0) FROM RepairOrder r " +
           "WHERE r.assignedWorker IS NOT NULL AND r.reportTime BETWEEN :start AND :end " +
           "GROUP BY r.assignedWorker ORDER BY COUNT(r) DESC")
    List<Object[]> getWorkerWorkload(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(AVG(TIMESTAMPDIFF(HOUR, r.reportTime, r.repairCompleteTime)), 0) FROM RepairOrder r " +
           "WHERE r.repairCompleteTime IS NOT NULL AND r.reportTime BETWEEN :start AND :end")
    Double getAvgRepairDuration(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
