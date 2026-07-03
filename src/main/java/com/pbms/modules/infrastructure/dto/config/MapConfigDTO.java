/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Map Configuration.
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
public class MapConfigDTO {
    private List<FloorConfigDTO> floors;
    private List<ZoneConfigDTO> zones;
    private List<GateConfigDTO> gates;
    private List<VehicleTypeDTO> vehicleTypes;
}
