package com.pbms.modules.operation.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.infrastructure.domain.RfidCard;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyTicket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id")
    private VehicleType vehicleType;

    @Column(name = "plate", length = 50)
    private String plate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfid_card_id")
    private RfidCard rfidCard;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    @Column(nullable = false, length = 50)
    private String status; // ACTIVE, EXPIRED, CANCELLED

    @Column(name = "auto_renew")
    @Builder.Default
    private Boolean autoRenew = false;
}

