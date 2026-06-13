package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "inventory_stocks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"material_id", "warehouse_id"})
})
public class InventoryStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long materialId;

    @Column(nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.quantity == null) this.quantity = 0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
