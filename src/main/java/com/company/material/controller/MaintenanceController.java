package com.company.material.controller;

import com.company.material.entity.MaintenancePlan;
import com.company.material.entity.MaintenanceTask;
import com.company.material.entity.SparePartConsumption;
import com.company.material.repository.MaintenancePlanRepository;
import com.company.material.repository.MaintenanceTaskRepository;
import com.company.material.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenancePlanRepository planRepository;
    private final MaintenanceTaskRepository taskRepository;
    private final MaintenanceService maintenanceService;

    @PostMapping("/plans")
    public ResponseEntity<?> createPlan(@RequestBody MaintenancePlan plan) {
        if (plan.getEquipmentId() == null || plan.getCycleDays() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "设备、保养周期为必填"));
        }
        MaintenancePlan saved = maintenanceService.createPlan(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceService.enrichPlanInfo(saved));
    }

    @GetMapping("/plans")
    public ResponseEntity<?> listPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long equipmentId) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<MaintenancePlan> result;
        if (status != null && !status.isBlank()) {
            result = planRepository.findByStatus(status, pr);
        } else if (equipmentId != null) {
            result = planRepository.findByEquipmentId(equipmentId, pr);
        } else {
            result = planRepository.findAll(pr);
        }
        return ResponseEntity.ok(maintenanceService.enrichPlanPage(result));
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<?> getPlanById(@PathVariable Long id) {
        return planRepository.findById(id)
                .map(plan -> ResponseEntity.ok((Object) maintenanceService.enrichPlanInfo(plan)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<?> updatePlan(@PathVariable Long id, @RequestBody MaintenancePlan body) {
        return maintenanceService.updatePlan(id, body)
                .map(plan -> ResponseEntity.ok((Object) maintenanceService.enrichPlanInfo(plan)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<?> deletePlan(@PathVariable Long id) {
        if (!planRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        planRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @GetMapping("/plans/due")
    public ResponseEntity<?> getDuePlans() {
        return ResponseEntity.ok(planRepository.findDuePlans(java.time.LocalDate.now())
                .stream()
                .map(maintenanceService::enrichPlanInfo)
                .toList());
    }

    @PostMapping("/plans/generate-tasks")
    public ResponseEntity<?> generateTasks() {
        return ResponseEntity.ok(Map.of(
                "generated", maintenanceService.generateDueTasks().size(),
                "message", "已生成到期保养任务"
        ));
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(@RequestBody MaintenanceTask task) {
        if (task.getEquipmentId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "设备为必填"));
        }
        task.setId(null);
        task.setTaskNo(maintenanceService.generateTaskNo());
        MaintenanceTask saved = taskRepository.save(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceService.enrichTaskInfo(saved));
    }

    @GetMapping("/tasks")
    public ResponseEntity<?> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) String keyword) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<MaintenanceTask> result;
        if (keyword != null && !keyword.isBlank()) {
            result = taskRepository.search(keyword, pr);
        } else if (status != null && !status.isBlank()) {
            result = taskRepository.findByStatus(status, pr);
        } else if (operator != null && !operator.isBlank()) {
            result = taskRepository.findByOperator(operator, pr);
        } else if (equipmentId != null) {
            result = taskRepository.findByEquipmentId(equipmentId, pr);
        } else if (planId != null) {
            result = taskRepository.findByPlanId(planId, pr);
        } else {
            result = taskRepository.findAll(pr);
        }
        return ResponseEntity.ok(maintenanceService.enrichTaskPage(result));
    }

    @GetMapping("/tasks/due")
    public ResponseEntity<?> getDueTasks() {
        return ResponseEntity.ok(maintenanceService.getDueTasks());
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<?> getTaskById(@PathVariable Long id) {
        return taskRepository.findById(id)
                .map(task -> ResponseEntity.ok((Object) maintenanceService.enrichTaskInfo(task)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/tasks/{id}/start")
    public ResponseEntity<?> startTask(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String operator = body.get("operator");
        if (operator == null || operator.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "操作人为必填"));
        }
        try {
            return maintenanceService.startTask(id, operator)
                    .map(task -> ResponseEntity.ok((Object) maintenanceService.enrichTaskInfo(task)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tasks/{id}/consume-spare-part")
    public ResponseEntity<?> consumeSparePart(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Long materialId = Long.valueOf(body.get("materialId").toString());
            Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
            Integer quantity = Integer.valueOf(body.get("quantity").toString());
            BigDecimal unitPrice = new BigDecimal(body.get("unitPrice").toString());
            String remark = body.get("remark") != null ? body.get("remark").toString() : null;

            SparePartConsumption consumption = maintenanceService.consumeSparePartForTask(
                    id, materialId, warehouseId, quantity, unitPrice, remark);
            return ResponseEntity.status(HttpStatus.CREATED).body(consumption);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks/{id}/spare-parts")
    public ResponseEntity<?> getSpareParts(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceService.getTaskConsumptionsWithDetails(id));
    }

    @PutMapping("/tasks/{id}/complete")
    public ResponseEntity<?> completeTask(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String maintenanceResult = body.get("maintenanceResult") != null
                    ? body.get("maintenanceResult").toString() : null;
            BigDecimal maintenanceHours = body.get("maintenanceHours") != null
                    ? new BigDecimal(body.get("maintenanceHours").toString()) : null;
            String remark = body.get("remark") != null ? body.get("remark").toString() : null;

            return maintenanceService.completeTask(id, maintenanceResult, maintenanceHours, remark)
                    .map(task -> ResponseEntity.ok((Object) maintenanceService.enrichTaskInfo(task)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(maintenanceService.getMaintenanceStats());
    }
}
