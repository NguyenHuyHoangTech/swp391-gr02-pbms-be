/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: DTO cho màn hình "Card Warehouse" của manager - visualId và
 * location là giá trị suy luận, không lưu trong DB.
 * @Dependencies: Không có
 */
package com.pbms.modules.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RfidCardDTO {
    private String uid;
    private String visualId;
    private String status;
    private String location;
}
