package com.company.material.repository;

import com.company.material.entity.MaintenanceTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, Long> {
    Optional<MaintenanceTask> findByTaskNo(String taskNo);
    boolean existsByTaskNo(String taskNo);
    Page<MaintenanceTask> findByEquipmentId(Long equipmentId, Pageable pageable);
    Page<MaintenanceTask> findByStatus(String status, Pageable pageable);
    Page<MaintenanceTask> findByOperator(String operator, Pageable pageable);
    Page<MaintenanceTask> findByPlanId(Long planId, Pageable pageable);
    List<MaintenanceTask> findByPlanIdAndStatusIn(Long planId, List<String> statuses);

    @Query("SELECT COUNT(t) FROM MaintenanceTask t WHERE t.status = '已完成' AND t.completeTime BETWEEN :start AND :end")
    long countCompletedByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM MaintenanceTask t WHERE t.createdAt BETWEEN :start AND :end")
    long countCreatedByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT t FROM MaintenanceTask t WHERE t.taskNo LIKE %:kw% OR t.maintenanceItems LIKE %:kw%")
    Page<MaintenanceTask> search(@Param("kw") String kw, Pageable pageable);
}
