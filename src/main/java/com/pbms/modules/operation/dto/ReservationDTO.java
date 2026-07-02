/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: Response DTO representing reservation details.
 *               Used to transfer reservation data back to the client.
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
     * PENDING    : Created, awaiting slot assignment or check-in
     * ACTIVE     : Customer has checked in
     * COMPLETED  : Customer has checked out
     * CANCELLED  : Cancelled by customer or auto-scheduler
     */
    private String status;
    private BigDecimal reservationFee;
    private String qrCode;

    private BigDecimal refundAmount;
    private String refundStatus;
    private String refundProofUrl;
    private String refundRejectReason;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}

