/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: Request DTO for creating a new reservation.
 *               Contains necessary information from customer to book a slot.
 * @Dependencies: None
 */
package com.pbms.modules.operation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateReservationRequest {

    private Long vehicleTypeId;
    private String plateNumber;
    private Long zoneId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedEntryTime;

    private Integer expectedDurationMinutes;
}