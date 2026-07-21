/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-15
 * @Description: Data Transfer Object for carrying the routing status information of a specific zone.
 * @Dependencies: lombok.Builder, lombok.Data
 */
package com.pbms.modules.operation.dto;

import lombok.Builder;
import lombok.Data;

// DTO trả về cho màn hình check-in/gate console: 1 dòng = 1 zone kèm số
// liệu realtime, do ZoneRoutingService.getRoutingStatus() dựng nên.
@Data
@Builder
public class ZoneRoutingStatusDTO {

    private Long zoneId;

    private String zoneName;

    // Sức chứa hiệu dụng = tổng slot - slot đang DISABLED (bảo trì).
    private Integer capacity;

    // Số slot đang có xe.
    private Integer occupied;

    // Số reservation PENDING đang rơi vào cửa sổ đến sớm.
    private Integer reserved;

    // Chỗ trống hiển thị = capacity - occupied - reserved (đã clamp >= 0).
    private Integer available;

    // % lấp đầy nội bộ dùng cho thuật toán routing - có thể vượt 100% vì
    // tính cả reservation như "nhu cầu" chưa tới nơi.
    private Double occupancyRate;

    // Ngưỡng đầy cấu hình của zone (để so sánh khi trượt ngưỡng); mặc định
    // 100 nếu zone chưa có rule routing nào.
    private Integer fillThresholdPct;

    // true nếu đây là zone hệ thống đang gợi ý cho xe vào.
    private Boolean isSuggested;
}
