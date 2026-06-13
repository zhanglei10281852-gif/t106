package com.company.material.controller;

import com.company.material.entity.SystemConfig;
import com.company.material.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigRepository systemConfigRepository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SystemConfig config) {
        if (config.getConfigKey() == null || config.getConfigValue() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "配置键和值为必填"));
        }
        if (systemConfigRepository.existsByConfigKey(config.getConfigKey())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "配置键已存在"));
        }
        config.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(systemConfigRepository.save(config));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(systemConfigRepository.findAll());
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> getByKey(@PathVariable String key) {
        return systemConfigRepository.findByConfigKey(key)
                .map(config -> ResponseEntity.ok((Object) config))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{key}")
    public ResponseEntity<?> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.get("configValue");
        String description = body.get("description");
        if (value == null || value.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "配置值为必填"));
        }
        return systemConfigRepository.findByConfigKey(key).map(config -> {
            config.setConfigValue(value);
            if (description != null) config.setDescription(description);
            return ResponseEntity.ok((Object) systemConfigRepository.save(config));
        }).orElseGet(() -> {
            SystemConfig newConfig = new SystemConfig();
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            newConfig.setDescription(description);
            return ResponseEntity.status(HttpStatus.CREATED).body((Object) systemConfigRepository.save(newConfig));
        });
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<?> delete(@PathVariable String key) {
        return systemConfigRepository.findByConfigKey(key).map(config -> {
            systemConfigRepository.delete(config);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/init-defaults")
    public ResponseEntity<?> initDefaults() {
        java.util.ArrayList<SystemConfig> created = new java.util.ArrayList<>();
        tryCreate("repair.hourly.rate", "100", "维修工时单价（元/小时）", created);
        tryCreate("maintenance.hourly.rate", "80", "保养工时单价（元/小时）", created);
        tryCreate("high.risk.failure.rate", "0.5", "高风险故障率阈值（次/月）", created);
        tryCreate("high.risk.cost.threshold", "10000", "高风险维修成本阈值（元）", created);
        return ResponseEntity.ok(Map.of(
                "message", "默认配置已初始化",
                "created", created.size(),
                "skipped", 4 - created.size()
        ));
    }

    private void tryCreate(String key, String value, String description, java.util.ArrayList<SystemConfig> created) {
        if (!systemConfigRepository.existsByConfigKey(key)) {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            created.add(systemConfigRepository.save(config));
        }
    }
}
