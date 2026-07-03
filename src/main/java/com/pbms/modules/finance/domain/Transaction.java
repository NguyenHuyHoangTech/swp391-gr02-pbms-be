package com.pbms.modules.finance.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.operation.domain.MonthlyTicket;
import com.pbms.modules.operation.domain.ParkingSession;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_session_id")
    private ParkingSession parkingSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_ticket_id")
    private MonthlyTicket monthlyTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    private PaymentOrder paymentOrder;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod; // CASH, BANK_TRANSFER, GATEWAY

    @Column(nullable = false, length = 50)
    private String status; // SUCCESS, FAILED, REFUNDED

    @Column(name = "transaction_reference")
    private String transactionReference;
}

