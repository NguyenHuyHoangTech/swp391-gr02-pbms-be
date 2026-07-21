/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: DTO đại diện cho 1 điểm dữ liệu xu hướng lấp đầy của 1 zone
 * tại 1 khung giờ, dùng để dựng biểu đồ lịch sử "Water Leveling" trên
 * dashboard manager.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneTrendDTO {
    private String timeWindow;
    private Long zoneId;
    private String zoneName;
    private BigDecimal occupancyPct;
}
