package com.pbms.modules.finance.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.operation.domain.MonthlyTicket;
import com.pbms.modules.operation.domain.Reservation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monthly_ticket_id")
    private MonthlyTicket monthlyTicket;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "action_type", length = 50)
    private String actionType; // CREATE_RESERVATION, CREATE_MONTHLY_TICKET, RENEW_MONTHLY_TICKET, CHECKOUT

    @Column(columnDefinition = "VARCHAR(MAX)")
    private String payload; // JSON representation of the request


    @Column(name = "order_code", nullable = false, unique = true, length = 100)
    private String orderCode;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 50)
    private String status; // PENDING, PAID, CANCELLED, REFUNDED

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // PAYOS, VNPAY, CASH
}

