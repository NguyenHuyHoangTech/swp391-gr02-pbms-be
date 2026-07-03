/**
 * @Author: Nguyen Huy Hoang
 * @Date: 2026-07-03
 * @Description: Lớp DTO đại diện cho xu hướng (trend) sức chứa của một khu vực đỗ xe (Zone) trong một khoảng thời gian nhất định.
 * Dùng để hiển thị biểu đồ phân tích trên Dashboard hoặc AI dự đoán.
 * @Dependencies: Không có
 */
package com.pbms.modules.incident.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneTrendDTO {
    /** Khung thời gian ghi nhận (Ví dụ: "08:00 - 09:00" hoặc định dạng ISO) */
    private String timeWindow;

    /** ID của khu vực đỗ xe (Zone) */
    private Long zoneId;

    /** Tên của khu vực đỗ xe (Zone) */
    private String zoneName;

    /** Tỷ lệ lấp đầy (Occupancy Percentage) tại khung thời gian đó. Đơn vị: % */
    private BigDecimal occupancyPct;
}
