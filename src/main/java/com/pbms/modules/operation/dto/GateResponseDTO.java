package com.pbms.modules.operation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GateResponseDTO {
    private Long sessionId;
    private String plateNumber;
    private String status; // SUCCESS, WARNING, ERROR
    private String message;
    
    // For Check-in
    private Long suggestedZoneId;
    private String suggestedZoneName;
    
    // For Check-out
    private BigDecimal checkoutFee;
}

