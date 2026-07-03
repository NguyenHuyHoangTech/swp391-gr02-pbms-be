/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Zone Configuration.
 * @Dependencies: lombok.AllArgsConstructor, lombok.Builder, lombok.Data, lombok.NoArgsConstructor, java.util.List
 */
package com.pbms.modules.infrastructure.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneConfigDTO {
    private Long id;
    private Long floorId;
    private String name;
    private Integer capacity;
    private Long vehicleTypeId;
    private String vehicleTypeName;
    private String vehicleCategory;
    private String functionType;
    private Double layoutX;
    private Double layoutY;
    private Integer rotation;
    private Integer overflowThreshold;
    private Long activeReservationsCount;
    private List<String> suggestedVehicles;
    private List<SlotConfigDTO> slots;
}
