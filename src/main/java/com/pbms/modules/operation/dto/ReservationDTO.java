package com.pbms.modules.operation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReservationDTO {
    private Long id;
    private String plateNumber;
    private String vehicleType;
    private String zoneName;
    private String slotName;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedEntryTime;
    
    private Integer expectedDurationMinutes;
    private String status; // PENDING, ACTIVE, COMPLETED, CANCELLED
    private BigDecimal reservationFee;
    private String qrCode;
    
    private BigDecimal refundAmount;
    private String refundStatus;
    private String refundProofUrl;
    private String refundRejectReason;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}

