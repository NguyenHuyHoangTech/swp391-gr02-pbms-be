/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Zone Layout information.
 * @Dependencies: lombok.Data
 */
package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class ZoneLayoutDTO {

    /**
     * X coordinate of the zone layout.
     */
    private Double layoutX;

    /**
     * Y coordinate of the zone layout.
     */
    private Double layoutY;

    /**
     * Rotation angle of the zone layout.
     */
    private Double rotation;
}
