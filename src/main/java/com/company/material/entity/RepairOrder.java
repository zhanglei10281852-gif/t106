package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "repair_orders")
public class RepairOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String orderNo;

    @Column(nullable = false)
    private Long equipmentId;

    @Transient
    private String equipmentName;

    @Transient
    private String equipmentCode;

    @Column(nullable = false, length = 50)
    private String reporter;

    @Column(length = 500)
    private String faultDescription;

    @Column(nullable = false, length = 20)
    private String urgencyLevel;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 50)
    private String assignedWorker;

    @Column(length = 500)
    private String faultCause;

    @Column(length = 500)
    private String repairMeasures;

    @Column(length = 1000)
    private String replacedParts;

    private BigDecimal repairHours;

    @Column(length = 30)
    private String repairResult;

    private BigDecimal sparePartCost;

    private BigDecimal laborCost;

    private BigDecimal totalCost;

    @Column(length = 500)
    private String acceptanceComment;

    private Boolean acceptancePassed;

    private LocalDateTime reportTime;

    private LocalDateTime assignTime;

    private LocalDateTime repairStartTime;

    private LocalDateTime repairCompleteTime;

    private LocalDateTime acceptanceTime;

    private LocalDateTime closeTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "待派工";
        if (this.reportTime == null) this.reportTime = LocalDateTime.now();
        if (this.sparePartCost == null) this.sparePartCost = BigDecimal.ZERO;
        if (this.laborCost == null) this.laborCost = BigDecimal.ZERO;
        if (this.totalCost == null) this.totalCost = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
