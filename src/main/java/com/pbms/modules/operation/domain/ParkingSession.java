package com.pbms.modules.operation.domain;

import com.pbms.modules.infrastructure.domain.Gate;
import com.pbms.modules.infrastructure.domain.RfidCard;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parking_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plate", length = 50)
    private String plate;

    @Column(name = "plate_out", length = 50)
    private String plateOut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id")
    private VehicleType vehicleType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfid_card_id")
    private RfidCard rfidCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_in_id")
    private Gate gateIn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_out_id")
    private Gate gateOut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;



    @Column(name = "time_in", nullable = false)
    private LocalDateTime timeIn;

    @Column(name = "time_out")
    private LocalDateTime timeOut;

    @Column(name = "pic_in_panorama", length = 500)
    private String picInPanorama;

    @Column(name = "pic_in_face", length = 500)
    private String picInFace;

    @Column(name = "pic_out_panorama", length = 500)
    private String picOutPanorama;

    @Column(name = "pic_out_face", length = 500)
    private String picOutFace;

    @Column(name = "suggested_zone_id")
    private Long suggestedZoneId;


    @Column(name = "penalty_fee", precision = 18, scale = 2)
    private BigDecimal penaltyFee;

    @Column(precision = 18, scale = 2)
    private BigDecimal discount;


    @Column(name = "parking_fee", precision = 18, scale = 2)
    private BigDecimal parkingFee;

    @Column(name = "overtime_minutes")
    private Long overtimeMinutes;

    @Column(name = "overtime_fee", precision = 18, scale = 2)
    private BigDecimal overtimeFee;

    @Column(nullable = false, length = 50)
    private String status; // ACTIVE, COMPLETED, EXCEPTION
}

