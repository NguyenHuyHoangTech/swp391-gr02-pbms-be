// TODO(Member2): Full implementation pending - SPATIAL & ROUTING MASTER
package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class ZoneRoutingStatusDTO {

    /** ID of the zone being evaluated */
    private Long zoneId;

    /** Number of available slots in this zone. <= 0 means FULL */
    private Integer available;
}
