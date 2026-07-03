package com.pbms.modules.operation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckOutSessionInfoDTO {
    private String plateNumberIn;
    private String rfid;
    private String vehicleType;
    private String customerType;
    
    // Captured images (From Check-in DB)
    private String picInPanorama;
    private String picInFace;
    
    // DB Context
    private LocalDateTime timeIn;
    private LocalDateTime timeOut;
    private String suggestedZoneName;
    
    // Pricing
    private Long durationMinutes;
    private java.math.BigDecimal expectedFee;
    private java.math.BigDecimal discountFee; // Optional fee dispute reduction
    
    // Pre-booked Logic
    private LocalDateTime bookedTimeIn;
    private LocalDateTime bookedTimeOut;
    private Long overtimeMinutes;
    private String status;
}

