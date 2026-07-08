package com.pbms.modules.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentExecutionResponse {
    private boolean success;
    private String status; // COMPLETED, REFUND_INITIATED
    private String message;
    private Object resultData; // The resulting object (e.g. ReservationDTO)
}
