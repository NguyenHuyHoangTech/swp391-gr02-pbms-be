package com.pbms.modules.infrastructure.domain;

import jakarta.persistence.*;
import lombok.*;

import com.pbms.modules.operation.domain.VehicleType;

@Entity
@Table(name = "gates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id")
    private VehicleType vehicleType;

    @Column(name = "gate_name", nullable = false, length = 100)
    private String gateName;

    @Column(name = "gate_type", nullable = false, length = 50)
    private String gateType; // ENTRY, EXIT

    @Column(name = "live_override_mode", length = 50)
    @Builder.Default
    private String liveOverrideMode = "NORMAL"; // NORMAL, OPEN_ALL, CLOSE_ALL

    @Column(length = 50)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    @Column(name = "layout_x")
    private Double layoutX;

    @Column(name = "layout_y")
    private Double layoutY;

    private Integer rotation;
}

