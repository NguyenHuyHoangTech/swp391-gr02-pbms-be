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

