package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class PreviewPriceRequest {
    private Long vehicleTypeId;
    private Integer expectedDurationMinutes;
}

