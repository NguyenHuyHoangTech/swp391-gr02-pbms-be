package com.pbms.modules.infrastructure.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTypeDTO {
    private Long id;
    private String typeName;
    private Integer matrixWidth;
    private Integer matrixHeight;
    private String category;
    private String status;
    private String iconUrl;
}

