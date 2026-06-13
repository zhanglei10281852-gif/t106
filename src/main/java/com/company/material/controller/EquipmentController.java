package com.company.material.controller;

import com.company.material.entity.Equipment;
import com.company.material.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/equipments")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentRepository equipmentRepository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Equipment equipment) {
        if (equipment.getEquipmentCode() == null || equipment.getName() == null
                || equipment.getEquipmentType() == null || equipment.getLocation() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "设备编号、名称、类型、位置为必填"));
        }
        if (equipmentRepository.existsByEquipmentCode(equipment.getEquipmentCode())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "设备编号已存在"));
        }
        equipment.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(equipmentRepository.save(equipment));
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) String keyword) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Equipment> result;
        if (keyword != null && !keyword.isBlank()) {
            result = equipmentRepository.search(keyword, pr);
        } else if (status != null && !status.isBlank()) {
            result = equipmentRepository.findByStatus(status, pr);
        } else if (equipmentType != null && !equipmentType.isBlank()) {
            result = equipmentRepository.findByEquipmentType(equipmentType, pr);
        } else {
            result = equipmentRepository.findAll(pr);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return equipmentRepository.findById(id)
                .map(e -> ResponseEntity.ok((Object) e))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Equipment body) {
        return equipmentRepository.findById(id).map(e -> {
            if (body.getName() != null) e.setName(body.getName());
            if (body.getEquipmentType() != null) e.setEquipmentType(body.getEquipmentType());
            if (body.getLocation() != null) e.setLocation(body.getLocation());
            if (body.getInstallDate() != null) e.setInstallDate(body.getInstallDate());
            if (body.getStatus() != null) e.setStatus(body.getStatus());
            if (body.getResponsiblePerson() != null) e.setResponsiblePerson(body.getResponsiblePerson());
            return ResponseEntity.ok((Object) equipmentRepository.save(e));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!equipmentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        equipmentRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @GetMapping("/stats/summary")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", equipmentRepository.count());
        stats.put("abnormal", equipmentRepository.findAbnormalEquipments().size());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/high-risk")
    public ResponseEntity<?> getHighRiskEquipments(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(equipmentRepository.findHighRiskEquipments(PageRequest.of(0, limit)));
    }
}
