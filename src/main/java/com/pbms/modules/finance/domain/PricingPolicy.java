package com.pbms.modules.finance.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pricing_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private com.pbms.modules.operation.domain.VehicleType vehicleType;

    @Column(name = "global_base_mins", nullable = false)
    private Integer globalBaseMins;

    @Column(name = "global_base_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal globalBaseFee;


    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, ARCHIVED

    @Column(name = "monthly_rate", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyRate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PricingShift> shifts = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = com.pbms.common.utils.TimeProvider.now();
        updatedAt = com.pbms.common.utils.TimeProvider.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = com.pbms.common.utils.TimeProvider.now();
    }
}

