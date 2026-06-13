package com.company.material.controller;

import com.company.material.entity.Equipment;
import com.company.material.entity.Material;
import com.company.material.repository.*;
import com.company.material.service.RepairOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final RepairOrderRepository repairOrderRepository;
    private final SparePartConsumptionRepository sparePartConsumptionRepository;
    private final EquipmentRepository equipmentRepository;
    private final MaterialRepository materialRepository;
    private final MaintenancePlanRepository maintenancePlanRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final RepairOrderService repairOrderService;

    @GetMapping("/monthly-orders")
    public ResponseEntity<?> getMonthlyOrders(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start, end;
        if (year != null && month != null) {
            start = LocalDate.of(year, month, 1).atStartOfDay();
            end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay();
        } else {
            start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            end = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", start.toLocalDate() + " ~ " + end.toLocalDate().minusDays(1));
        result.put("totalOrders", repairOrderRepository.countByReportTimeBetween(start, end));

        Map<String, Long> statusCount = new LinkedHashMap<>();
        statusCount.put("待派工", repairOrderRepository.countByStatus("待派工"));
        statusCount.put("已派工", repairOrderRepository.countByStatus("已派工"));
        statusCount.put("维修中", repairOrderRepository.countByStatus("维修中"));
        statusCount.put("待验收", repairOrderRepository.countByStatus("待验收"));
        statusCount.put("已完成", repairOrderRepository.countByStatus("已完成"));
        statusCount.put("已关闭", repairOrderRepository.countByStatus("已关闭"));
        result.put("statusDistribution", statusCount);

        Double avgDuration = repairOrderRepository.getAvgRepairDuration(start, end);
        result.put("avgRepairDurationHours", String.format("%.2f", avgDuration));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/equipment-failure-ranking")
    public ResponseEntity<?> getEquipmentFailureRanking(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start, end;
        if (year != null && month != null) {
            start = LocalDate.of(year, month, 1).atStartOfDay();
            end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay();
        } else {
            start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            end = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();
        }

        List<Object[]> rawData = repairOrderRepository.countByEquipmentIdAndDateRange(start, end);
        List<Map<String, Object>> ranking = rawData.stream()
                .limit(limit)
                .map(row -> {
                    Long equipmentId = (Long) row[0];
                    Long count = (Long) row[1];
                    Map<String, Object> item = new LinkedHashMap<>();
                    equipmentRepository.findById(equipmentId).ifPresent(equipment -> {
                        item.put("equipmentId", equipment.getId());
                        item.put("equipmentCode", equipment.getEquipmentCode());
                        item.put("equipmentName", equipment.getName());
                        item.put("location", equipment.getLocation());
                        item.put("failureCount", count);
                        item.put("totalRepairCost", repairOrderRepository.sumTotalCostByEquipmentId(equipmentId));
                    });
                    return item;
                })
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "period", start.toLocalDate() + " ~ " + end.toLocalDate().minusDays(1),
                "ranking", ranking
        ));
    }

    @GetMapping("/repair-cost-ranking")
    public ResponseEntity<?> getRepairCostRanking(
            @RequestParam(defaultValue = "10") int limit) {
        List<Equipment> equipments = equipmentRepository.findHighRiskEquipments(PageRequest.of(0, limit));
        List<Map<String, Object>> ranking = equipments.stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("equipmentId", e.getId());
                    item.put("equipmentCode", e.getEquipmentCode());
                    item.put("equipmentName", e.getName());
                    item.put("location", e.getLocation());
                    item.put("failureCount", e.getFailureCount());
                    item.put("totalRepairCost", e.getAccumulatedRepairCost());
                    item.put("status", e.getStatus());
                    return item;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("ranking", ranking));
    }

    @GetMapping("/spare-part-consumption-ranking")
    public ResponseEntity<?> getSparePartConsumptionRanking(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start, end;
        if (year != null && month != null) {
            start = LocalDate.of(year, month, 1).atStartOfDay();
            end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay();
        } else {
            start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            end = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();
        }

        List<Object[]> rawData = sparePartConsumptionRepository.getSparePartRanking(start, end);
        List<Map<String, Object>> ranking = rawData.stream()
                .limit(limit)
                .map(row -> {
                    Long materialId = (Long) row[0];
                    Long qty = ((Number) row[1]).longValue();
                    BigDecimal amount = (BigDecimal) row[2];
                    Map<String, Object> item = new LinkedHashMap<>();
                    materialRepository.findById(materialId).ifPresent(material -> {
                        item.put("materialId", material.getId());
                        item.put("materialCode", material.getMaterialCode());
                        item.put("materialName", material.getName());
                        item.put("unit", material.getUnit());
                        item.put("quantity", qty);
                        item.put("amount", amount);
                    });
                    return item;
                })
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "period", start.toLocalDate() + " ~ " + end.toLocalDate().minusDays(1),
                "ranking", ranking
        ));
    }

    @GetMapping("/avg-processing-time")
    public ResponseEntity<?> getAvgProcessingTime(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start, end;
        if (year != null && month != null) {
            start = LocalDate.of(year, month, 1).atStartOfDay();
            end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay();
        } else {
            start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            end = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();
        }

        Double avgHours = repairOrderRepository.getAvgRepairDuration(start, end);

        return ResponseEntity.ok(Map.of(
                "period", start.toLocalDate() + " ~ " + end.toLocalDate().minusDays(1),
                "avgProcessingTimeHours", String.format("%.2f", avgHours),
                "avgProcessingTimeDays", String.format("%.2f", avgHours / 24)
        ));
    }

    @GetMapping("/worker-workload")
    public ResponseEntity<?> getWorkerWorkload(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start, end;
        if (year != null && month != null) {
            start = LocalDate.of(year, month, 1).atStartOfDay();
            end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay();
        } else {
            start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            end = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();
        }

        List<Object[]> rawData = repairOrderRepository.getWorkerWorkload(start, end);
        List<Map<String, Object>> workload = rawData.stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("worker", row[0]);
                    item.put("orderCount", row[1]);
                    item.put("totalHours", String.format("%.2f", row[2]));
                    return item;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "period", start.toLocalDate() + " ~ " + end.toLocalDate().minusDays(1),
                "workload", workload
        ));
    }

    @GetMapping("/maintenance-execution-rate")
    public ResponseEntity<?> getMaintenanceExecutionRate(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDateTime start, end;
        if (year != null && month != null) {
            start = LocalDate.of(year, month, 1).atStartOfDay();
            end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay();
        } else {
            start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            end = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();
        }

        long created = maintenanceTaskRepository.countCreatedByDateRange(start, end);
        long completed = maintenanceTaskRepository.countCompletedByDateRange(start, end);
        double executionRate = created > 0 ? (double) completed / created * 100 : 0;

        long activePlans = maintenancePlanRepository.countActivePlans();
        long overduePlans = maintenancePlanRepository.countOverduePlans(LocalDate.now());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", start.toLocalDate() + " ~ " + end.toLocalDate().minusDays(1));
        result.put("activePlans", activePlans);
        result.put("overduePlans", overduePlans);
        result.put("tasksCreated", created);
        result.put("tasksCompleted", completed);
        result.put("executionRate", String.format("%.2f%%", executionRate));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/equipment-health/{equipmentId}")
    public ResponseEntity<?> getEquipmentHealth(@PathVariable Long equipmentId) {
        try {
            return ResponseEntity.ok(repairOrderService.getEquipmentHealthAnalysis(equipmentId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all-equipments-health")
    public ResponseEntity<?> getAllEquipmentsHealth(@RequestParam(defaultValue = "false") boolean highRiskOnly) {
        List<Equipment> equipments = equipmentRepository.findAll();
        List<Map<String, Object>> healthList = equipments.stream()
                .map(e -> {
                    try {
                        return repairOrderService.getEquipmentHealthAnalysis(e.getId());
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(h -> !highRiskOnly || (Boolean) h.get("isHighRisk"))
                .sorted((a, b) -> {
                    BigDecimal costA = (BigDecimal) a.get("totalRepairCost");
                    BigDecimal costB = (BigDecimal) b.get("totalRepairCost");
                    return costB.compareTo(costA);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "total", healthList.size(),
                "highRiskCount", healthList.stream().filter(h -> (Boolean) h.get("isHighRisk")).count(),
                "equipments", healthList
        ));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();

        Map<String, Object> dashboard = new LinkedHashMap<>();

        Map<String, Long> orderStats = new LinkedHashMap<>();
        orderStats.put("total", repairOrderRepository.count());
        orderStats.put("monthlyNew", repairOrderRepository.countByReportTimeBetween(startOfMonth, endOfMonth));
        orderStats.put("pendingAssign", repairOrderRepository.countByStatus("待派工"));
        orderStats.put("repairing", repairOrderRepository.countByStatus("维修中"));
        orderStats.put("urgent", (long) repairOrderRepository.findUrgentOrders().size());
        dashboard.put("orderStats", orderStats);

        Map<String, Object> equipmentStats = new LinkedHashMap<>();
        equipmentStats.put("total", equipmentRepository.count());
        equipmentStats.put("abnormal", equipmentRepository.findAbnormalEquipments().size());
        dashboard.put("equipmentStats", equipmentStats);

        Map<String, Object> maintenanceStats = new LinkedHashMap<>();
        maintenanceStats.put("activePlans", maintenancePlanRepository.countActivePlans());
        maintenanceStats.put("overduePlans", maintenancePlanRepository.countOverduePlans(LocalDate.now()));
        dashboard.put("maintenanceStats", maintenanceStats);

        Double avgDuration = repairOrderRepository.getAvgRepairDuration(startOfMonth, endOfMonth);
        dashboard.put("avgRepairDurationHours", String.format("%.2f", avgDuration));

        return ResponseEntity.ok(dashboard);
    }
}
