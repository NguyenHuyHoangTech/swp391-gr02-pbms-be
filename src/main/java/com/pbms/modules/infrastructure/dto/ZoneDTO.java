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
    private Integer capacity; // Maps to total_slots calculated from count
    private Integer availableSlots; // Calculated from COUNT(slots where status = AVAILABLE)
    private Long vehicleTypeId;
    private String vehicleType; // CAR or MOTORBIKE
    private Integer vehicleMatrixWidth;
    private Integer vehicleMatrixHeight;
    private String functionType; // WALK_IN, IMPOUNDED, MONTHLY
    private Double layoutX;
    private Double layoutY;
    private Integer rotation;
    private List<SlotDTO> slots;
}

