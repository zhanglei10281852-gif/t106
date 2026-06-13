package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "equipments")
public class Equipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String equipmentCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String equipmentType;

    @Column(nullable = false, length = 50)
    private String location;

    private LocalDate installDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 50)
    private String responsiblePerson;

    private LocalDate lastMaintenanceDate;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal accumulatedRepairCost;

    private Integer failureCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "正常运行";
        if (this.failureCount == null) this.failureCount = 0;
        if (this.accumulatedRepairCost == null) this.accumulatedRepairCost = java.math.BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
