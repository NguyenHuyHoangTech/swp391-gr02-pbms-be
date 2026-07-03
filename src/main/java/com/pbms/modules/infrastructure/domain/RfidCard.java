package com.pbms.modules.infrastructure.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rfid_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfidCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_code", unique = true, nullable = false)
    private String cardCode;

    @Column(name = "status")
    private String status; // AVAILABLE, IN_USE, LOST, DAMAGED

    @Column(name = "assigned_plate", length = 50)
    private String assignedPlate;
}

