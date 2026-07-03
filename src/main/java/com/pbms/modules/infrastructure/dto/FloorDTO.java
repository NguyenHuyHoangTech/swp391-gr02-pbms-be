/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Floor entity.
 * @Dependencies: lombok.Data
 */
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
