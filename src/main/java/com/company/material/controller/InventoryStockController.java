package com.company.material.controller;

import com.company.material.entity.InventoryStock;
import com.company.material.entity.Material;
import com.company.material.entity.Warehouse;
import com.company.material.repository.InventoryStockRepository;
import com.company.material.repository.MaterialRepository;
import com.company.material.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/inventory-stocks")
@RequiredArgsConstructor
public class InventoryStockController {

    private final InventoryStockRepository inventoryStockRepository;
    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody InventoryStock stock) {
        if (stock.getMaterialId() == null || stock.getWarehouseId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "物料和仓库为必填"));
        }
        if (!materialRepository.existsById(stock.getMaterialId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "物料不存在"));
        }
        if (!warehouseRepository.existsById(stock.getWarehouseId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "仓库不存在"));
        }
        if (inventoryStockRepository.existsByMaterialIdAndWarehouseId(stock.getMaterialId(), stock.getWarehouseId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "该物料在此仓库已有库存，请使用入库接口增加库存"));
        }
        stock.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryStockRepository.save(stock));
    }

    @PostMapping("/inbound")
    public ResponseEntity<?> inbound(@RequestBody Map<String, Object> body) {
        try {
            Long materialId = Long.valueOf(body.get("materialId").toString());
            Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
            Integer quantity = Integer.valueOf(body.get("quantity").toString());
            BigDecimal unitPrice = body.get("unitPrice") != null
                    ? new BigDecimal(body.get("unitPrice").toString()) : null;

            if (quantity <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "入库数量必须大于0"));
            }
            if (!materialRepository.existsById(materialId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "物料不存在"));
            }
            if (!warehouseRepository.existsById(warehouseId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "仓库不存在"));
            }

            Optional<InventoryStock> existingStock = inventoryStockRepository
                    .findByMaterialIdAndWarehouseId(materialId, warehouseId);

            InventoryStock stock;
            if (existingStock.isPresent()) {
                stock = existingStock.get();
                stock.setQuantity(stock.getQuantity() + quantity);
                if (unitPrice != null) {
                    stock.setUnitPrice(unitPrice);
                }
            } else {
                stock = new InventoryStock();
                stock.setMaterialId(materialId);
                stock.setWarehouseId(warehouseId);
                stock.setQuantity(quantity);
                if (unitPrice != null) {
                    stock.setUnitPrice(unitPrice);
                } else {
                    materialRepository.findById(materialId).ifPresent(m -> {
                        if (m.getReferencePrice() != null) {
                            stock.setUnitPrice(m.getReferencePrice());
                        } else {
                            stock.setUnitPrice(BigDecimal.ZERO);
                        }
                    });
                }
            }

            InventoryStock saved = inventoryStockRepository.save(stock);
            return ResponseEntity.ok(Map.of(
                    "message", "入库成功",
                    "stockId", saved.getId(),
                    "currentQuantity", saved.getQuantity()
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "参数格式错误：" + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "入库失败：" + e.getMessage()));
        }
    }

    @PostMapping("/batch-inbound")
    public ResponseEntity<?> batchInbound(@RequestBody List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "入库明细不能为空"));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (Map<String, Object> item : items) {
            try {
                Long materialId = Long.valueOf(item.get("materialId").toString());
                Long warehouseId = Long.valueOf(item.get("warehouseId").toString());
                Integer quantity = Integer.valueOf(item.get("quantity").toString());
                BigDecimal unitPrice = item.get("unitPrice") != null
                        ? new BigDecimal(item.get("unitPrice").toString()) : null;

                if (quantity <= 0) {
                    results.add(Map.of("materialId", materialId, "success", false, "error", "数量必须大于0"));
                    failCount++;
                    continue;
                }

                Optional<InventoryStock> existingStock = inventoryStockRepository
                        .findByMaterialIdAndWarehouseId(materialId, warehouseId);

                InventoryStock stock;
                if (existingStock.isPresent()) {
                    stock = existingStock.get();
                    stock.setQuantity(stock.getQuantity() + quantity);
                    if (unitPrice != null) {
                        stock.setUnitPrice(unitPrice);
                    }
                } else {
                    stock = new InventoryStock();
                    stock.setMaterialId(materialId);
                    stock.setWarehouseId(warehouseId);
                    stock.setQuantity(quantity);
                    if (unitPrice != null) {
                        stock.setUnitPrice(unitPrice);
                    } else {
                        stock.setUnitPrice(materialRepository.findById(materialId)
                                .map(Material::getReferencePrice)
                                .orElse(BigDecimal.ZERO));
                    }
                }
                inventoryStockRepository.save(stock);
                results.add(Map.of("materialId", materialId, "success", true));
                successCount++;
            } catch (Exception e) {
                results.add(Map.of("item", item, "success", false, "error", e.getMessage()));
                failCount++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "批量入库完成",
                "successCount", successCount,
                "failCount", failCount,
                "details", results
        ));
    }

    @PostMapping("/outbound")
    public ResponseEntity<?> outbound(@RequestBody Map<String, Object> body) {
        try {
            Long materialId = Long.valueOf(body.get("materialId").toString());
            Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
            Integer quantity = Integer.valueOf(body.get("quantity").toString());
            String remark = body.get("remark") != null ? body.get("remark").toString() : null;

            if (quantity <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "出库数量必须大于0"));
            }

            InventoryStock stock = inventoryStockRepository
                    .findByMaterialIdAndWarehouseId(materialId, warehouseId)
                    .orElseThrow(() -> new IllegalArgumentException("该仓库无此物料库存"));

            if (stock.getQuantity() < quantity) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "库存不足，当前库存：" + stock.getQuantity()));
            }

            stock.setQuantity(stock.getQuantity() - quantity);
            InventoryStock saved = inventoryStockRepository.save(stock);

            return ResponseEntity.ok(Map.of(
                    "message", "出库成功",
                    "stockId", saved.getId(),
                    "currentQuantity", saved.getQuantity(),
                    "remark", remark != null ? remark : ""
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "参数格式错误：" + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "false") boolean availableOnly) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        Page<InventoryStock> result;
        if (availableOnly) {
            result = inventoryStockRepository.findAvailableStocks(pr);
        } else if (materialId != null) {
            result = inventoryStockRepository.findByMaterialId(materialId, pr);
        } else if (warehouseId != null) {
            result = inventoryStockRepository.findByWarehouseId(warehouseId, pr);
        } else {
            result = inventoryStockRepository.findAll(pr);
        }

        Page<Map<String, Object>> enriched = result.map(stock -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", stock.getId());
            item.put("materialId", stock.getMaterialId());
            item.put("warehouseId", stock.getWarehouseId());
            item.put("quantity", stock.getQuantity());
            item.put("unitPrice", stock.getUnitPrice());
            item.put("totalValue", stock.getUnitPrice() != null
                    ? stock.getUnitPrice().multiply(BigDecimal.valueOf(stock.getQuantity()))
                    : BigDecimal.ZERO);
            item.put("createdAt", stock.getCreatedAt());
            item.put("updatedAt", stock.getUpdatedAt());

            materialRepository.findById(stock.getMaterialId()).ifPresent(m -> {
                item.put("materialCode", m.getMaterialCode());
                item.put("materialName", m.getName());
                item.put("materialUnit", m.getUnit());
                item.put("materialCategory", m.getCategory());
            });
            warehouseRepository.findById(stock.getWarehouseId()).ifPresent(w -> {
                item.put("warehouseCode", w.getWarehouseCode());
                item.put("warehouseName", w.getName());
            });
            return item;
        });

        return ResponseEntity.ok(enriched);
    }

    @GetMapping("/material/{materialId}")
    public ResponseEntity<?> getByMaterialId(@PathVariable Long materialId) {
        List<InventoryStock> stocks = inventoryStockRepository.findByMaterialId(materialId);
        int totalQty = stocks.stream().mapToInt(InventoryStock::getQuantity).sum();

        List<Map<String, Object>> details = stocks.stream().map(stock -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", stock.getId());
            item.put("warehouseId", stock.getWarehouseId());
            item.put("quantity", stock.getQuantity());
            item.put("unitPrice", stock.getUnitPrice());
            warehouseRepository.findById(stock.getWarehouseId())
                    .ifPresent(w -> item.put("warehouseName", w.getName()));
            return item;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "materialId", materialId,
                "totalQuantity", totalQty,
                "warehouses", details
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return inventoryStockRepository.findById(id)
                .map(stock -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", stock.getId());
                    item.put("materialId", stock.getMaterialId());
                    item.put("warehouseId", stock.getWarehouseId());
                    item.put("quantity", stock.getQuantity());
                    item.put("unitPrice", stock.getUnitPrice());
                    item.put("totalValue", stock.getUnitPrice() != null
                            ? stock.getUnitPrice().multiply(BigDecimal.valueOf(stock.getQuantity()))
                            : BigDecimal.ZERO);
                    item.put("createdAt", stock.getCreatedAt());
                    item.put("updatedAt", stock.getUpdatedAt());

                    materialRepository.findById(stock.getMaterialId()).ifPresent(m -> {
                        item.put("materialCode", m.getMaterialCode());
                        item.put("materialName", m.getName());
                    });
                    warehouseRepository.findById(stock.getWarehouseId()).ifPresent(w -> {
                        item.put("warehouseName", w.getName());
                    });
                    return ResponseEntity.ok((Object) item);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return inventoryStockRepository.findById(id).map(stock -> {
            if (body.get("quantity") != null) {
                stock.setQuantity(Integer.valueOf(body.get("quantity").toString()));
            }
            if (body.get("unitPrice") != null) {
                stock.setUnitPrice(new BigDecimal(body.get("unitPrice").toString()));
            }
            return ResponseEntity.ok((Object) inventoryStockRepository.save(stock));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!inventoryStockRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        inventoryStockRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @GetMapping("/stats/summary")
    public ResponseEntity<?> getStats() {
        List<InventoryStock> all = inventoryStockRepository.findAll();
        int totalItems = all.size();
        int totalQuantity = all.stream().mapToInt(InventoryStock::getQuantity).sum();
        BigDecimal totalValue = all.stream()
                .map(s -> s.getUnitPrice() != null
                        ? s.getUnitPrice().multiply(BigDecimal.valueOf(s.getQuantity()))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(Map.of(
                "totalItems", totalItems,
                "totalQuantity", totalQuantity,
                "totalValue", totalValue
        ));
    }
}
