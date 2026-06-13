package com.company.material.repository;

import com.company.material.entity.Equipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    Optional<Equipment> findByEquipmentCode(String equipmentCode);
    boolean existsByEquipmentCode(String equipmentCode);
    Page<Equipment> findByStatus(String status, Pageable pageable);
    Page<Equipment> findByEquipmentType(String equipmentType, Pageable pageable);
    List<Equipment> findByStatusIn(List<String> statuses);

    @Query("SELECT e FROM Equipment e WHERE e.name LIKE %:kw% OR e.equipmentCode LIKE %:kw% OR e.location LIKE %:kw%")
    Page<Equipment> search(@Param("kw") String kw, Pageable pageable);

    @Query("SELECT e FROM Equipment e WHERE e.status NOT IN ('正常运行', '停用') ORDER BY " +
           "CASE e.status WHEN '故障停机' THEN 1 WHEN '维修中' THEN 2 WHEN '保养中' THEN 3 ELSE 4 END, " +
           "e.createdAt DESC")
    List<Equipment> findAbnormalEquipments();

    @Query("SELECT e FROM Equipment e ORDER BY e.failureCount DESC, e.accumulatedRepairCost DESC")
    List<Equipment> findHighRiskEquipments(Pageable pageable);
}
