package com.pbms.modules.infrastructure.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "slot_name", nullable = false, length = 50)
    private String slotName;

    @Column(length = 50)
    @Builder.Default
    private String status = "AVAILABLE"; // AVAILABLE, OCCUPIED, DISABLED

    @Column(name = "current_plate", length = 50)
    private String currentPlate;

    @Version
    @Builder.Default
    private Integer version = 1;
}

