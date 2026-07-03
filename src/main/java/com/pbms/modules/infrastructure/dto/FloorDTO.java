package com.pbms.modules.infrastructure.dto;

import lombok.Data;

@Data
public class FloorDTO {
    private Long id;
    private String name;
    private String type;
    private Integer mapCols;
    private Integer mapRows;
}

