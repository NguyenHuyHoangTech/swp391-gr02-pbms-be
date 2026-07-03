package com.pbms.modules.operation.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private com.pbms.modules.infrastructure.domain.Zone zone;

    @Column(name = "expected_entry_time", nullable = false)
    private LocalDateTime expectedEntryTime;

    @Column(name = "expected_duration_minutes", nullable = false)
    private Integer expectedDurationMinutes;

    @Column(nullable = false, length = 50)
    private String status; // PENDING, ACTIVE, COMPLETED, CANCELLED, NO_SHOW

    @Column(name = "reservation_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal reservationFee;

    @Column(name = "qr_code", unique = true)
    private String qrCode;

    @Column(name = "notified_early_arrival")
    private Boolean notifiedEarlyArrival;

    @Column(name = "refund_status", length = 50)
    private String refundStatus;

    @Column(name = "refund_amount", precision = 18, scale = 2)
    private BigDecimal refundAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by")
    private User refundedBy;

    @Column(name = "refund_proof_url", length = 500)
    private String refundProofUrl;

    @Column(name = "refund_reject_reason", columnDefinition = "VARCHAR(MAX)")
    private String refundRejectReason;
}

