package com.pbms.modules.operation.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id")
    private VehicleType vehicleType;

    @Column(name = "plate_number", nullable = false, unique = true, length = 50)
    private String plateNumber;

    @Column(length = 50)
    private String color;

    @Column(length = 100)
    private String brand;

    @Builder.Default
    @Column(length = 50)
    private String status = "ACTIVE";

    @Builder.Default
    @Column(name = "is_blacklisted", nullable = false)
    private Boolean isBlacklisted = false;

    @Column(name = "blacklist_reason", columnDefinition = "VARCHAR(MAX)")
    private String blacklistReason;

    @Column(name = "blacklist_evidence_url", columnDefinition = "VARCHAR(MAX)")
    private String blacklistEvidenceUrl;
}

