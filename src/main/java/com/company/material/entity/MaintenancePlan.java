package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "maintenance_plans")
public class MaintenancePlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long equipmentId;

    @Transient
    private String equipmentName;

    @Transient
    private String equipmentCode;

    @Column(nullable = false)
    private Integer cycleDays;

    @Column(length = 1000)
    private String maintenanceItems;

    @Column(length = 200)
    private String description;

    private LocalDate lastMaintenanceDate;

    private LocalDate nextMaintenanceDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 50)
    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "启用";
        calculateNextDate();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void calculateNextDate() {
        if (this.lastMaintenanceDate != null && this.cycleDays != null) {
            this.nextMaintenanceDate = this.lastMaintenanceDate.plusDays(this.cycleDays);
        } else if (this.cycleDays != null) {
            this.nextMaintenanceDate = LocalDate.now().plusDays(this.cycleDays);
        }
    }

    public boolean isDue() {
        if (this.nextMaintenanceDate == null) return false;
        return !LocalDate.now().isBefore(this.nextMaintenanceDate);
    }

    public boolean isOverdue() {
        if (this.nextMaintenanceDate == null) return false;
        return LocalDate.now().isAfter(this.nextMaintenanceDate);
    }
}
