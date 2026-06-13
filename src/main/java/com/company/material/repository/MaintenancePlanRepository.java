package com.company.material.repository;

import com.company.material.entity.MaintenancePlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlan, Long> {
    Page<MaintenancePlan> findByEquipmentId(Long equipmentId, Pageable pageable);
    Page<MaintenancePlan> findByStatus(String status, Pageable pageable);
    Optional<MaintenancePlan> findByEquipmentIdAndStatus(Long equipmentId, String status);

    @Query("SELECT m FROM MaintenancePlan m WHERE m.status = '启用' AND m.nextMaintenanceDate <= :date")
    List<MaintenancePlan> findDuePlans(@Param("date") LocalDate date);

    @Query("SELECT m FROM MaintenancePlan m WHERE m.status = '启用' AND m.nextMaintenanceDate BETWEEN :start AND :end")
    List<MaintenancePlan> findPlansDueBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(m) FROM MaintenancePlan m WHERE m.status = '启用'")
    long countActivePlans();

    @Query("SELECT COUNT(m) FROM MaintenancePlan m WHERE m.status = '启用' AND m.nextMaintenanceDate < :today")
    long countOverduePlans(@Param("today") LocalDate today);
}
