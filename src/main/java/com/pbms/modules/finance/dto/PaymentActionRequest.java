package com.pbms.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentActionRequest {
    private String actionType; // CREATE_RESERVATION, CREATE_MONTHLY_TICKET, RENEW_MONTHLY_TICKET, CHECKOUT
    private Double amount;
    private String gateway; // VNPAY, PAYOS, PAYPAL
    private Long sessionId; // optional
    private Map<String, Object> payload; // the actual JSON payload required for the action
}
