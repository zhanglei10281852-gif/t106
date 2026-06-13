package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "spare_part_consumptions")
public class SparePartConsumption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repairOrderId;

    @Column(nullable = false)
    private Long materialId;

    @Transient
    private String materialCode;

    @Transient
    private String materialName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false)
    private Long warehouseId;

    @Transient
    private String warehouseName;

    @Column(length = 200)
    private String remark;

    private LocalDateTime consumedAt;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.consumedAt == null) this.consumedAt = LocalDateTime.now();
        if (this.totalPrice == null && this.unitPrice != null && this.quantity != null) {
            this.totalPrice = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
        }
    }
}
