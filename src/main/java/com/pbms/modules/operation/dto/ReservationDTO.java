/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: Data Transfer Object (DTO) for Reservation entity. Used to send reservation details to the client.
 * @Dependencies: None
 */
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
    
    /**
     * Reservation lifecycle status.
     * Indicates the current state of the reservation.
     */
    private String status;
    private BigDecimal reservationFee;
    
    private String actualIn;
    private String actualOut;
    private BigDecimal penaltyFee;
    private String userEmail;
    
    private BigDecimal refundAmount;
    private String refundStatus;
    private String refundProofUrl;
    private String refundRejectReason;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}

