package com.company.material.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "system_configs")
public class SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String configKey;

    @Column(nullable = false, length = 500)
    private String configValue;

    @Column(length = 100)
    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getAsBigDecimal() {
        try {
            return new BigDecimal(this.configValue);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public Integer getAsInteger() {
        try {
            return Integer.parseInt(this.configValue);
        } catch (Exception e) {
            return 0;
        }
    }
}
