/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: DTO containing data for creating a new prebooking reservation from the client.
 * @Dependencies: None
 */
package com.pbms.modules.operation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateReservationRequest {
    @NotNull(message = "Vehicle type is required")
    private Long vehicleTypeId;
    
    @NotBlank(message = "Plate number is required")
    private String plateNumber;
    
    @NotNull(message = "Zone ID is required")
    private Long zoneId;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedEntryTime;
    
    private Integer expectedDurationMinutes;
}

