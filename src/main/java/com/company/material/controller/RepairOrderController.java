package com.company.material.controller;

import com.company.material.entity.RepairOrder;
import com.company.material.entity.SparePartConsumption;
import com.company.material.repository.RepairOrderRepository;
import com.company.material.service.RepairOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repair-orders")
@RequiredArgsConstructor
public class RepairOrderController {

    private final RepairOrderRepository repairOrderRepository;
    private final RepairOrderService repairOrderService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody RepairOrder order) {
        if (order.getEquipmentId() == null || order.getReporter() == null
                || order.getUrgencyLevel() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "设备、报修人、紧急程度为必填"));
        }
        try {
            RepairOrder saved = repairOrderService.createOrder(order);
            return ResponseEntity.status(HttpStatus.CREATED).body(repairOrderService.enrichOrderInfo(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedWorker,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "true") boolean priority) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("reportTime").descending());
        Page<RepairOrder> result;
        if (keyword != null && !keyword.isBlank()) {
            result = repairOrderRepository.search(keyword, pr);
        } else if (status != null && !status.isBlank()) {
            result = repairOrderRepository.findByStatus(status, pr);
        } else if (assignedWorker != null && !assignedWorker.isBlank()) {
            result = repairOrderRepository.findByAssignedWorker(assignedWorker, pr);
        } else if (equipmentId != null) {
            result = repairOrderRepository.findByEquipmentId(equipmentId, pr);
        } else if (priority) {
            result = repairOrderRepository.findAllWithPriority(pr);
        } else {
            result = repairOrderRepository.findAll(pr);
        }
        return ResponseEntity.ok(repairOrderService.enrichOrderPage(result));
    }

    @GetMapping("/urgent")
    public ResponseEntity<?> getUrgentOrders() {
        return ResponseEntity.ok(repairOrderRepository.findUrgentOrders()
                .stream()
                .map(repairOrderService::enrichOrderInfo)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return repairOrderRepository.findById(id)
                .map(order -> ResponseEntity.ok((Object) repairOrderService.enrichOrderInfo(order)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<?> assignWorker(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String worker = body.get("assignedWorker");
        if (worker == null || worker.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "维修工为必填"));
        }
        try {
            return repairOrderService.assignWorker(id, worker)
                    .map(order -> ResponseEntity.ok((Object) repairOrderService.enrichOrderInfo(order)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/start")
    public ResponseEntity<?> startRepair(@PathVariable Long id) {
        try {
            return repairOrderService.startRepair(id)
                    .map(order -> ResponseEntity.ok((Object) repairOrderService.enrichOrderInfo(order)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/consume-spare-part")
    public ResponseEntity<?> consumeSparePart(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Long materialId = Long.valueOf(body.get("materialId").toString());
            Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
            Integer quantity = Integer.valueOf(body.get("quantity").toString());
            BigDecimal unitPrice = new BigDecimal(body.get("unitPrice").toString());
            String remark = body.get("remark") != null ? body.get("remark").toString() : null;

            SparePartConsumption consumption = repairOrderService.consumeSparePart(
                    id, materialId, warehouseId, quantity, unitPrice, remark);
            return ResponseEntity.status(HttpStatus.CREATED).body(consumption);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/spare-parts")
    public ResponseEntity<?> getSpareParts(@PathVariable Long id) {
        return ResponseEntity.ok(repairOrderService.getOrderConsumptionsWithDetails(id));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<?> completeRepair(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String faultCause = body.get("faultCause") != null ? body.get("faultCause").toString() : null;
            String repairMeasures = body.get("repairMeasures") != null ? body.get("repairMeasures").toString() : null;
            String replacedParts = body.get("replacedParts") != null ? body.get("replacedParts").toString() : null;
            BigDecimal repairHours = body.get("repairHours") != null
                    ? new BigDecimal(body.get("repairHours").toString()) : null;
            String repairResult = body.get("repairResult") != null ? body.get("repairResult").toString() : null;

            return repairOrderService.completeRepair(id, faultCause, repairMeasures, replacedParts, repairHours, repairResult)
                    .map(order -> ResponseEntity.ok((Object) repairOrderService.enrichOrderInfo(order)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<?> acceptOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            boolean passed = Boolean.parseBoolean(body.get("passed").toString());
            String comment = body.get("comment") != null ? body.get("comment").toString() : null;

            return repairOrderService.acceptOrder(id, passed, comment)
                    .map(order -> ResponseEntity.ok((Object) repairOrderService.enrichOrderInfo(order)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<?> closeOrder(@PathVariable Long id) {
        try {
            return repairOrderService.closeOrder(id)
                    .map(order -> ResponseEntity.ok((Object) repairOrderService.enrichOrderInfo(order)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/health-analysis")
    public ResponseEntity<?> getEquipmentHealth(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(repairOrderService.getEquipmentHealthAnalysis(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats/summary")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", repairOrderRepository.count());
        stats.put("pendingAssign", repairOrderRepository.countByStatus("待派工"));
        stats.put("assigned", repairOrderRepository.countByStatus("已派工"));
        stats.put("repairing", repairOrderRepository.countByStatus("维修中"));
        stats.put("pendingAccept", repairOrderRepository.countByStatus("待验收"));
        stats.put("completed", repairOrderRepository.countByStatus("已完成"));
        stats.put("closed", repairOrderRepository.countByStatus("已关闭"));
        stats.put("urgentCount", repairOrderRepository.findUrgentOrders().size());
        return ResponseEntity.ok(stats);
    }
}
