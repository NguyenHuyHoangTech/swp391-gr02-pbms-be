package com.pbms.modules.identity.domain;

import com.pbms.modules.infrastructure.domain.Gate;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "staff_work_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffWorkSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id", nullable = false)
    private Gate gate;

    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

    @Column(name = "logout_time")
    private LocalDateTime logoutTime;

    @Column(nullable = false, length = 50)
    private String status; // ACTIVE, COMPLETED

    @Column(name = "expected_revenue")
    private java.math.BigDecimal expectedRevenue;

    @Column(name = "actual_revenue")
    private java.math.BigDecimal actualRevenue;

    @Column(name = "revenue_variance")
    private java.math.BigDecimal revenueVariance;

    @Column(name = "variance_reason", length = 255)
    private String varianceReason;

    @Column(name = "discrepancy_status", length = 50)
    private String discrepancyStatus; // MATCH, SHORT, OVER
}

