package com.company.material.service;

import com.company.material.entity.*;
import com.company.material.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepairOrderService {

    private final RepairOrderRepository repairOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final SparePartConsumptionRepository sparePartConsumptionRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public RepairOrder createOrder(RepairOrder order) {
        String orderNo = generateOrderNo();
        order.setOrderNo(orderNo);
        order.setStatus("待派工");
        order.setReportTime(LocalDateTime.now());

        RepairOrder saved = repairOrderRepository.save(order);

        if ("特急停产".equals(order.getUrgencyLevel())) {
            equipmentRepository.findById(order.getEquipmentId()).ifPresent(equipment -> {
                equipment.setStatus("故障停机");
                equipmentRepository.save(equipment);
            });
        }

        return saved;
    }

    private String generateOrderNo() {
        String datePrefix = "WX" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int sequence = 1;
        for (int i = 1; i <= 9999; i++) {
            String candidate = datePrefix + String.format("%04d", i);
            if (!repairOrderRepository.existsByOrderNo(candidate)) {
                sequence = i;
                break;
            }
        }
        return datePrefix + String.format("%04d", sequence);
    }

    @Transactional
    public Optional<RepairOrder> assignWorker(Long orderId, String worker) {
        return repairOrderRepository.findById(orderId).map(order -> {
            if (!"待派工".equals(order.getStatus())) {
                throw new IllegalStateException("当前状态不允许派工");
            }
            order.setAssignedWorker(worker);
            order.setStatus("已派工");
            order.setAssignTime(LocalDateTime.now());
            return repairOrderRepository.save(order);
        });
    }

    @Transactional
    public Optional<RepairOrder> startRepair(Long orderId) {
        return repairOrderRepository.findById(orderId).map(order -> {
            if (!"已派工".equals(order.getStatus()) && !"维修中".equals(order.getStatus())) {
                throw new IllegalStateException("当前状态不允许开始维修");
            }
            order.setStatus("维修中");
            order.setRepairStartTime(LocalDateTime.now());
            equipmentRepository.findById(order.getEquipmentId()).ifPresent(equipment -> {
                equipment.setStatus("维修中");
                equipmentRepository.save(equipment);
            });
            return repairOrderRepository.save(order);
        });
    }

    @Transactional
    public SparePartConsumption consumeSparePart(Long orderId, Long materialId, Long warehouseId,
                                                  Integer quantity, BigDecimal unitPrice, String remark) {
        RepairOrder order = repairOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在"));

        if (!"维修中".equals(order.getStatus())) {
            throw new IllegalStateException("只有维修中状态才能领用备件");
        }

        InventoryStock stock = inventoryStockRepository.findByMaterialIdAndWarehouseId(materialId, warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("该仓库无此物料库存"));

        if (stock.getQuantity() < quantity) {
            throw new IllegalStateException("库存不足，当前库存：" + stock.getQuantity());
        }

        stock.setQuantity(stock.getQuantity() - quantity);
        inventoryStockRepository.save(stock);

        SparePartConsumption consumption = new SparePartConsumption();
        consumption.setRepairOrderId(orderId);
        consumption.setMaterialId(materialId);
        consumption.setWarehouseId(warehouseId);
        consumption.setQuantity(quantity);
        consumption.setUnitPrice(unitPrice);
        consumption.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        consumption.setRemark(remark);
        consumption.setConsumedAt(LocalDateTime.now());

        SparePartConsumption saved = sparePartConsumptionRepository.save(consumption);

        updateOrderCost(orderId);

        return saved;
    }

    @Transactional
    public void updateOrderCost(Long orderId) {
        repairOrderRepository.findById(orderId).ifPresent(order -> {
            BigDecimal sparePartCost = sparePartConsumptionRepository.sumTotalPriceByRepairOrderId(orderId);
            BigDecimal hourRate = getHourlyRate();
            BigDecimal laborCost = order.getRepairHours() != null
                    ? hourRate.multiply(order.getRepairHours())
                    : BigDecimal.ZERO;

            order.setSparePartCost(sparePartCost);
            order.setLaborCost(laborCost);
            order.setTotalCost(sparePartCost.add(laborCost));
            repairOrderRepository.save(order);
        });
    }

    private BigDecimal getHourlyRate() {
        return systemConfigRepository.findByConfigKey("repair.hourly.rate")
                .map(SystemConfig::getAsBigDecimal)
                .orElse(BigDecimal.valueOf(100));
    }

    @Transactional
    public Optional<RepairOrder> completeRepair(Long orderId, String faultCause, String repairMeasures,
                                                 String replacedParts, BigDecimal repairHours, String repairResult) {
        return repairOrderRepository.findById(orderId).map(order -> {
            if (!"维修中".equals(order.getStatus())) {
                throw new IllegalStateException("当前状态不允许完成维修");
            }
            order.setFaultCause(faultCause);
            order.setRepairMeasures(repairMeasures);
            order.setReplacedParts(replacedParts);
            order.setRepairHours(repairHours);
            order.setRepairResult(repairResult);
            order.setRepairCompleteTime(LocalDateTime.now());
            order.setStatus("待验收");

            updateOrderCost(orderId);
            return repairOrderRepository.save(order);
        });
    }

    @Transactional
    public Optional<RepairOrder> acceptOrder(Long orderId, boolean passed, String comment) {
        return repairOrderRepository.findById(orderId).map(order -> {
            if (!"待验收".equals(order.getStatus())) {
                throw new IllegalStateException("当前状态不允许验收");
            }
            order.setAcceptancePassed(passed);
            order.setAcceptanceComment(comment);
            order.setAcceptanceTime(LocalDateTime.now());

            if (passed) {
                order.setStatus("已完成");
                equipmentRepository.findById(order.getEquipmentId()).ifPresent(equipment -> {
                    equipment.setStatus("正常运行");
                    equipment.setFailureCount(equipment.getFailureCount() + 1);
                    equipment.setAccumulatedRepairCost(
                            equipment.getAccumulatedRepairCost().add(order.getTotalCost()));
                    equipmentRepository.save(equipment);
                });
            } else {
                order.setStatus("维修中");
                equipmentRepository.findById(order.getEquipmentId()).ifPresent(equipment -> {
                    equipment.setStatus("维修中");
                    equipmentRepository.save(equipment);
                });
            }
            return repairOrderRepository.save(order);
        });
    }

    @Transactional
    public Optional<RepairOrder> closeOrder(Long orderId) {
        return repairOrderRepository.findById(orderId).map(order -> {
            if (!"已完成".equals(order.getStatus())) {
                throw new IllegalStateException("只有已完成状态才能关闭");
            }
            order.setStatus("已关闭");
            order.setCloseTime(LocalDateTime.now());
            return repairOrderRepository.save(order);
        });
    }

    public RepairOrder enrichOrderInfo(RepairOrder order) {
        equipmentRepository.findById(order.getEquipmentId()).ifPresent(equipment -> {
            order.setEquipmentName(equipment.getName());
            order.setEquipmentCode(equipment.getEquipmentCode());
        });
        return order;
    }

    public Page<RepairOrder> enrichOrderPage(Page<RepairOrder> page) {
        page.getContent().forEach(this::enrichOrderInfo);
        return page;
    }

    public List<SparePartConsumption> getOrderConsumptionsWithDetails(Long orderId) {
        List<SparePartConsumption> consumptions = sparePartConsumptionRepository.findByRepairOrderId(orderId);
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

    public Map<String, Object> getEquipmentHealthAnalysis(Long equipmentId) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));

        List<RepairOrder> completedOrders = repairOrderRepository.findCompletedOrdersByEquipmentId(equipmentId);

        int failureCount = completedOrders.size();
        BigDecimal totalRepairCost = repairOrderRepository.sumTotalCostByEquipmentId(equipmentId);

        long totalDowntimeHours = 0;
        long totalRepairHours = 0;
        int mttrCount = 0;

        for (RepairOrder order : completedOrders) {
            if (order.getRepairStartTime() != null && order.getRepairCompleteTime() != null) {
                totalRepairHours += java.time.Duration.between(
                        order.getRepairStartTime(), order.getRepairCompleteTime()).toHours();
                mttrCount++;
            }
            if (order.getReportTime() != null && order.getAcceptanceTime() != null) {
                totalDowntimeHours += java.time.Duration.between(
                        order.getReportTime(), order.getAcceptanceTime()).toHours();
            }
        }

        double mttr = mttrCount > 0 ? (double) totalRepairHours / mttrCount : 0;

        double mtbf = 0;
        if (failureCount >= 2 && equipment.getInstallDate() != null) {
            long daysSinceInstall = java.time.Duration.between(
                    equipment.getInstallDate().atStartOfDay(), LocalDateTime.now()).toDays();
            mtbf = (double) daysSinceInstall / (failureCount - 1);
        }

        long daysSinceInstall = equipment.getInstallDate() != null
                ? java.time.Duration.between(equipment.getInstallDate().atStartOfDay(), LocalDateTime.now()).toDays()
                : 0;
        double failureRate = daysSinceInstall > 0 ? (double) failureCount / daysSinceInstall * 30 : 0;

        return Map.of(
                "equipmentId", equipment.getId(),
                "equipmentCode", equipment.getEquipmentCode(),
                "equipmentName", equipment.getName(),
                "failureCount", failureCount,
                "totalRepairCost", totalRepairCost,
                "totalDowntimeHours", totalDowntimeHours,
                "mttrHours", mttr,
                "mtbfDays", mtbf,
                "failureRatePerMonth", failureRate,
                "isHighRisk", failureRate > 0.5 || totalRepairCost.compareTo(BigDecimal.valueOf(10000)) > 0
        );
    }
}
