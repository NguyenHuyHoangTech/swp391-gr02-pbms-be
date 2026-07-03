/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for carrying the routing status information of a specific zone.
 * @Dependencies: lombok.Builder, lombok.Data
 */
package com.pbms.modules.operation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZoneRoutingStatusDTO {

    /**
     * Unique identifier of the zone.
     */
    private Long zoneId;

    /**
     * Name of the zone.
     */
    private String zoneName;

    /**
     * Total capacity of the zone.
     */
    private Integer capacity;

    /**
     * Number of currently occupied slots in the zone.
     */
    private Integer occupied;

    /**
     * Number of reserved slots in the zone.
     */
    private Integer reserved;

    /**
     * Number of available slots in the zone.
     */
    private Integer available;

    /**
     * Current occupancy rate of the zone.
     */
    private Double occupancyRate;

    /**
     * Fill threshold percentage configured for the zone.
     */
    private Integer fillThresholdPct;

    /**
     * Flag indicating if the zone is suggested for routing.
     */
    private Boolean isSuggested;
}
