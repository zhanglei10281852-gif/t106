package com.company.material.service;

import com.company.material.entity.*;
import com.company.material.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenancePlanRepository planRepository;
    private final MaintenanceTaskRepository taskRepository;
    private final EquipmentRepository equipmentRepository;
    private final SparePartConsumptionRepository sparePartConsumptionRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public MaintenancePlan createPlan(MaintenancePlan plan) {
        plan.setId(null);
        plan.setStatus("启用");
        plan.calculateNextDate();
        return planRepository.save(plan);
    }

    @Transactional
    public Optional<MaintenancePlan> updatePlan(Long id, MaintenancePlan body) {
        return planRepository.findById(id).map(plan -> {
            if (body.getCycleDays() != null) plan.setCycleDays(body.getCycleDays());
            if (body.getMaintenanceItems() != null) plan.setMaintenanceItems(body.getMaintenanceItems());
            if (body.getDescription() != null) plan.setDescription(body.getDescription());
            if (body.getLastMaintenanceDate() != null) {
                plan.setLastMaintenanceDate(body.getLastMaintenanceDate());
                plan.calculateNextDate();
            }
            if (body.getStatus() != null) plan.setStatus(body.getStatus());
            return planRepository.save(plan);
        });
    }

    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public List<MaintenanceTask> generateDueTasks() {
        List<MaintenancePlan> duePlans = planRepository.findDuePlans(LocalDate.now());
        List<MaintenanceTask> generatedTasks = new ArrayList<>();

        for (MaintenancePlan plan : duePlans) {
            List<MaintenanceTask> existing = taskRepository.findByPlanIdAndStatusIn(
                    plan.getId(), List.of("待执行", "执行中"));
            if (!existing.isEmpty()) {
                continue;
            }

            MaintenanceTask task = new MaintenanceTask();
            task.setTaskNo(generateTaskNo());
            task.setPlanId(plan.getId());
            task.setEquipmentId(plan.getEquipmentId());
            task.setMaintenanceItems(plan.getMaintenanceItems());
            task.setStatus("待执行");
            task.setScheduledTime(plan.getNextMaintenanceDate().atStartOfDay());

            generatedTasks.add(taskRepository.save(task));

            equipmentRepository.findById(plan.getEquipmentId()).ifPresent(equipment -> {
                equipment.setStatus("保养中");
                equipmentRepository.save(equipment);
            });
        }

        return generatedTasks;
    }

    public String generateTaskNo() {
        String datePrefix = "BY" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int sequence = 1;
        for (int i = 1; i <= 9999; i++) {
            String candidate = datePrefix + String.format("%04d", i);
            if (!taskRepository.existsByTaskNo(candidate)) {
                sequence = i;
                break;
            }
        }
        return datePrefix + String.format("%04d", sequence);
    }

    @Transactional
    public Optional<MaintenanceTask> startTask(Long taskId, String operator) {
        return taskRepository.findById(taskId).map(task -> {
            if (!"待执行".equals(task.getStatus())) {
                throw new IllegalStateException("当前状态不允许开始");
            }
            task.setOperator(operator);
            task.setStatus("执行中");
            task.setStartTime(LocalDateTime.now());
            return taskRepository.save(task);
        });
    }

    @Transactional
    public SparePartConsumption consumeSparePartForTask(Long taskId, Long materialId, Long warehouseId,
                                                         Integer quantity, BigDecimal unitPrice, String remark) {
        MaintenanceTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("保养任务不存在"));

        if (!"执行中".equals(task.getStatus())) {
            throw new IllegalStateException("只有执行中状态才能领用备件");
        }

        InventoryStock stock = inventoryStockRepository.findByMaterialIdAndWarehouseId(materialId, warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("该仓库无此物料库存"));

        if (stock.getQuantity() < quantity) {
            throw new IllegalStateException("库存不足，当前库存：" + stock.getQuantity());
        }

        stock.setQuantity(stock.getQuantity() - quantity);
        inventoryStockRepository.save(stock);

        SparePartConsumption consumption = new SparePartConsumption();
        consumption.setRepairOrderId(-taskId);
        consumption.setMaterialId(materialId);
        consumption.setWarehouseId(warehouseId);
        consumption.setQuantity(quantity);
        consumption.setUnitPrice(unitPrice);
        consumption.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        consumption.setRemark(remark != null ? remark : "保养消耗");
        consumption.setConsumedAt(LocalDateTime.now());

        SparePartConsumption saved = sparePartConsumptionRepository.save(consumption);
        updateTaskCost(taskId);

        return saved;
    }

    @Transactional
    public void updateTaskCost(Long taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            List<SparePartConsumption> consumptions = sparePartConsumptionRepository.findByRepairOrderId(-taskId);
            BigDecimal sparePartCost = consumptions.stream()
                    .map(SparePartConsumption::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal hourRate = getHourlyRate();
            BigDecimal laborCost = task.getMaintenanceHours() != null
                    ? hourRate.multiply(task.getMaintenanceHours())
                    : BigDecimal.ZERO;

            task.setSparePartCost(sparePartCost);
            task.setLaborCost(laborCost);
            task.setTotalCost(sparePartCost.add(laborCost));
            taskRepository.save(task);
        });
    }

    private BigDecimal getHourlyRate() {
        return systemConfigRepository.findByConfigKey("maintenance.hourly.rate")
                .map(SystemConfig::getAsBigDecimal)
                .orElse(BigDecimal.valueOf(80));
    }

    @Transactional
    public Optional<MaintenanceTask> completeTask(Long taskId, String maintenanceResult,
                                                   BigDecimal maintenanceHours, String remark) {
        return taskRepository.findById(taskId).map(task -> {
            if (!"执行中".equals(task.getStatus())) {
                throw new IllegalStateException("当前状态不允许完成");
            }
            task.setMaintenanceResult(maintenanceResult);
            task.setMaintenanceHours(maintenanceHours);
            task.setRemark(remark);
            task.setStatus("已完成");
            task.setCompleteTime(LocalDateTime.now());

            updateTaskCost(taskId);

            if (task.getPlanId() != null) {
                planRepository.findById(task.getPlanId()).ifPresent(plan -> {
                    plan.setLastMaintenanceDate(LocalDate.now());
                    plan.calculateNextDate();
                    planRepository.save(plan);
                });
            }

            equipmentRepository.findById(task.getEquipmentId()).ifPresent(equipment -> {
                equipment.setStatus("正常运行");
                equipment.setLastMaintenanceDate(LocalDate.now());
                equipmentRepository.save(equipment);
            });

            return taskRepository.save(task);
        });
    }

    public List<MaintenanceTask> getDueTasks() {
        return taskRepository.findByStatus("待执行", Pageable.unpaged()).getContent()
                .stream()
                .map(this::enrichTaskInfo)
                .toList();
    }

    public MaintenancePlan enrichPlanInfo(MaintenancePlan plan) {
        equipmentRepository.findById(plan.getEquipmentId()).ifPresent(equipment -> {
            plan.setEquipmentName(equipment.getName());
            plan.setEquipmentCode(equipment.getEquipmentCode());
        });
        return plan;
    }

    public Page<MaintenancePlan> enrichPlanPage(Page<MaintenancePlan> page) {
        page.getContent().forEach(this::enrichPlanInfo);
        return page;
    }

    public MaintenanceTask enrichTaskInfo(MaintenanceTask task) {
        equipmentRepository.findById(task.getEquipmentId()).ifPresent(equipment -> {
            task.setEquipmentName(equipment.getName());
            task.setEquipmentCode(equipment.getEquipmentCode());
        });
        return task;
    }

    public Page<MaintenanceTask> enrichTaskPage(Page<MaintenanceTask> page) {
        page.getContent().forEach(this::enrichTaskInfo);
        return page;
    }

    public List<SparePartConsumption> getTaskConsumptionsWithDetails(Long taskId) {
        List<SparePartConsumption> consumptions = sparePartConsumptionRepository.findByRepairOrderId(-taskId);
        consumptions.forEach(c -> {
            materialRepository.findById(c.getMaterialId()).ifPresent(m -> {
                c.setMaterialCode(m.getMaterialCode());
                c.setMaterialName(m.getName());
            });
            warehouseRepository.findById(c.getWarehouseId()).ifPresent(w -> {
                c.setWarehouseName(w.getName());
            });
        });
        return consumptions;
    }

    public Map<String, Object> getMaintenanceStats() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1).atStartOfDay();

        long created = taskRepository.countCreatedByDateRange(startOfMonth, endOfMonth);
        long completed = taskRepository.countCompletedByDateRange(startOfMonth, endOfMonth);
        double executionRate = created > 0 ? (double) completed / created * 100 : 0;

        long activePlans = planRepository.countActivePlans();
        long overduePlans = planRepository.countOverduePlans(LocalDate.now());

        return Map.of(
                "activePlans", activePlans,
                "overduePlans", overduePlans,
                "monthlyCreated", created,
                "monthlyCompleted", completed,
                "executionRate", String.format("%.2f%%", executionRate)
        );
    }
}
