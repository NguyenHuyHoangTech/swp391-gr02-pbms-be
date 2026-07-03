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
public class ScanEventDTO {
    private Long gateId;
    private String actionType; // "IN" or "OUT"
    private String plateNumber;
    private String vehicleType;
    private String rfid;
    
    // Captured images (From IoT)
    private String imageBase64;
    private String lprImageBase64;
    private String plateNumberIn;
    
    // DB Context (If OUT, fetch IN data)
    private String picInPanorama;
    private String picInFace;
    private LocalDateTime timeIn;
    
    // Suggested Zone (If IN)
    private Long suggestedZoneId;
    private String suggestedZoneName;
    
    // Pricing
    private Long durationMinutes;
    private java.math.BigDecimal expectedFee;
    
    // Customer Type
    private String customerType; // "GUEST", "PREBOOKED", "MONTHLY"
}

