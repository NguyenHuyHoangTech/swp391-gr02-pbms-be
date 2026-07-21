package com.pbms.modules.finance.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reference_type", nullable = false, length = 50)
    private String referenceType; // 'MONTHLY_PASS' or 'RESERVATION'

    @Column(name = "reference_id", nullable = false, length = 50)
    private String referenceId;

    @Column(name = "paid_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal paidAmount;

    @Column(name = "penalty_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal penaltyFee;

    @Column(name = "refund_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal refundAmount;

    @Column(name = "bank_name", columnDefinition = "NVARCHAR(100)")
    private String bankName;

    @Column(name = "account_number", length = 100)
    private String accountNumber;

    @Column(name = "account_name", columnDefinition = "NVARCHAR(100)")
    private String accountName;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING"; // PENDING, REFUNDED, REJECTED

    @Column(name = "cancel_time", nullable = false)
    private LocalDateTime cancelTime;

    @Column(name = "reject_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectReason;

    @Column(name = "proof_url", length = 500)
    private String proofUrl;
}

