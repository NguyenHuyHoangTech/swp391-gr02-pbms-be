/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-03
 * @Description: Request DTO for the reservation preview price endpoint.
 *               FE sends this to calculate estimated fee before confirming booking.
 * @Dependencies: None
 */
package com.pbms.modules.operation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PreviewPriceRequest {

    private Long vehicleTypeId;
    private Integer expectedDurationMinutes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedEntryTime;
}
