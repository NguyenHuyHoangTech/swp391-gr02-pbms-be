/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Slot entity.
 * @Dependencies: lombok.Builder, lombok.Data
 */
package com.pbms.modules.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlotDTO {
    private String id;
    private String name;
    private String status;
}
