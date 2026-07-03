/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Zone entity.
 * @Dependencies: lombok.Builder, lombok.Data, java.util.List
 */
package com.pbms.modules.infrastructure.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ZoneDTO {
    private Long id;
    private Long floorId;
    private String floorName;
    private String name;
    private Integer capacity;
    private Integer availableSlots;
    private Integer pendingReservations;
    private Long vehicleTypeId;
    private String vehicleType;
    private Integer vehicleMatrixWidth;
    private Integer vehicleMatrixHeight;
    private String functionType;
    private Double layoutX;
    private Double layoutY;
    private Integer rotation;
    private List<SlotDTO> slots;
}
