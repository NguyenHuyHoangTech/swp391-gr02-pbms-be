package com.pbms.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
@Data
public class PaymentActionRequest {
    private Long referenceId;     // ID của Reservation hoặc MonthlyTicket
    private String referenceType; // "RESERVATION" hoặc "MONTHLY_TICKET"
    private BigDecimal amount;
    private String gateway;       // "VNPAY", "PAYOS", "PAYPAL"
}
