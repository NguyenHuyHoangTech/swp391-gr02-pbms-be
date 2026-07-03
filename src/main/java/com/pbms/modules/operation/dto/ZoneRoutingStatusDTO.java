package com.pbms.modules.operation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZoneRoutingStatusDTO {
    private Long zoneId;
    private String zoneName;
    private Integer capacity;
    private Integer occupied;
    private Integer reserved;
    private Integer available;
    private Double occupancyRate;
    private Integer fillThresholdPct;
    private Boolean isSuggested;
}
