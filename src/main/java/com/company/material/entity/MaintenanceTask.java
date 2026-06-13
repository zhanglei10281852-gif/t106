package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "maintenance_tasks")
public class MaintenanceTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String taskNo;

    private Long planId;

    @Column(nullable = false)
    private Long equipmentId;

    @Transient
    private String equipmentName;

    @Transient
    private String equipmentCode;

    @Column(length = 1000)
    private String maintenanceItems;

    @Column(length = 50)
    private String operator;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String remark;

    @Column(length = 500)
    private String maintenanceResult;

    private BigDecimal maintenanceHours;

    private BigDecimal sparePartCost;

    private BigDecimal laborCost;

    private BigDecimal totalCost;

    private LocalDateTime scheduledTime;

    private LocalDateTime startTime;

    private LocalDateTime completeTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "待执行";
        if (this.sparePartCost == null) this.sparePartCost = BigDecimal.ZERO;
        if (this.laborCost == null) this.laborCost = BigDecimal.ZERO;
        if (this.totalCost == null) this.totalCost = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
