package com.pbms.modules.incident.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.StaffWorkSession;
import com.pbms.modules.operation.domain.ParkingSession;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "fee_adjustments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_session_id")
    private ParkingSession parkingSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_staff_id")
    private StaffWorkSession approvedByStaff;

    @Column(name = "adjustment_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal adjustmentAmount;

    @Column(nullable = false, columnDefinition = "VARCHAR(MAX)")
    private String reason;
}

