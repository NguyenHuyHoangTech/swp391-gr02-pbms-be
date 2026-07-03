package com.pbms.modules.infrastructure.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GateConfigDTO {
    private Long id;
    private Long floorId;
    private String floor; // floorName like 'B1', 'B2' for frontend mapping
    private String staffName;
    private String staffEmail;
    private String name;
    private Long vehicleTypeId;
    private String type; // IN, OUT
    private String status; // IDLE, OCCUPIED, MAINTENANCE
    private Double layoutX;
    private Double layoutY;
    private Integer rotation;
}

