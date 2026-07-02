/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: JPA Entity mapping to the [reservations] table.
 *               Represents a customer's pre-booking request for a parking slot.
 *               Lifecycle: PENDING -> ACTIVE (on check-in) -> COMPLETED (on check-out)
 *                       or PENDING -> CANCELLED (by customer or scheduler)
 *                       or PENDING -> NO_SHOW (expired without check-in)
 * @Dependencies:
 *  - Vehicle   (com.pbms.modules.operation.domain)    - the vehicle being reserved
 *  - Zone      (com.pbms.modules.infrastructure.domain) - target parking zone
 *  - Slot      (com.pbms.modules.infrastructure.domain) - assigned slot (nullable until resolved)
 *  - User      (com.pbms.modules.identity.domain)     - manager who processed refund
 *  - BaseEntity (com.pbms.common.domain)              - provides id, createdAt, updatedAt
 */
package com.pbms.modules.operation.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.domain.Slot;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private Slot slot;

    @Column(name = "expected_entry_time", nullable = false)
    private LocalDateTime expectedEntryTime;

    @Column(name = "expected_duration_minutes", nullable = false)
    private Integer expectedDurationMinutes;

    /**
     * Reservation lifecycle status.
     * PENDING    : Created, awaiting slot assignment or check-in
     * ACTIVE     : Customer has checked in
     * COMPLETED  : Customer has checked out
     * CANCELLED  : Cancelled by customer or auto-scheduler
     * NO_SHOW    : Expired without check-in
     */
    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "reservation_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal reservationFee;

    @Column(name = "qr_code", unique = true)
    private String qrCode;

    @Column(name = "notified_early_arrival")
    private Boolean notifiedEarlyArrival;

    /**
     * Refund lifecycle status.
     * REQUESTED : Customer submitted refund request
     * APPROVED  : Manager approved, refund is pending transfer
     * REJECTED  : Manager rejected with reason
     */
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
