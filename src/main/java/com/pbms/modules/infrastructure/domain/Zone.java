package com.pbms.modules.infrastructure.domain;

import com.pbms.modules.operation.domain.VehicleType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName;

    @Column(name = "function_type", nullable = false, length = 50)
    private String functionType; // WALK_IN, IMPOUNDED, MONTHLY

    @Column(name = "layout_x")
    private Double layoutX;

    @Column(name = "layout_y")
    private Double layoutY;

    private Integer rotation;

    @Column(name = "overflow_threshold")
    private Integer overflowThreshold;

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, DELETED
}

