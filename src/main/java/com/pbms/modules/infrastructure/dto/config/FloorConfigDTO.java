package com.pbms.modules.infrastructure.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorConfigDTO {
    private Long id;
    private String name;
    private String type; // FOUR_WHEEL, TWO_WHEEL
    private Integer mapCols;
    private Integer mapRows;
}

