/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: DTO đại diện cho 1 điểm dữ liệu xu hướng lấp đầy của 1 zone
 * tại 1 khung giờ, dùng để dựng biểu đồ lịch sử "Water Leveling" trên
 * dashboard manager.
 * @Dependencies: Không có
 */
/**
 * @Author: Nguyen Huy Hoang
 * @Date: 2026-07-03
 * @Description: Lớp DTO đại diện cho xu hướng (trend) sức chứa của một khu vực đỗ xe (Zone) trong một khoảng thời gian nhất định.
 * Dùng để hiển thị biểu đồ phân tích trên Dashboard hoặc AI dự đoán.
 * @Dependencies: Không có
 *
 * =========================================================================================
 * GIẢI THÍCH CHI TIẾT (bổ sung cho phần mô tả gốc ở trên):
 * =========================================================================================
 * MỤC ĐÍCH:
 * Mỗi Object của class này là 1 "điểm dữ liệu" trên biểu đồ xu hướng — trả
 * lời câu hỏi "Zone X, trong khung giờ Y, đã lấp đầy bao nhiêu %". Dữ liệu
 * này được `ZoneTrendSchedulingService` chốt lại mỗi giờ (dựa trên đỉnh điểm
 * do `ZoneOccupancyTracker` theo dõi) và `ZoneTrendService` đọc ra để vẽ biểu
 * đồ/đưa cho AI advisor phân tích xu hướng quá tải theo giờ trong ngày.
 */
package com.pbms.modules.operation.dto;

// =========================================================================
// PHẦN 1: THƯ VIỆN LOMBOK
// =========================================================================
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =========================================================================
// PHẦN 2: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.math.BigDecimal; // Kiểu số thập phân chính xác cao, dùng để lưu % lấp đầy tránh sai số float.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneTrendDTO {
    /** Khung thời gian ghi nhận (Ví dụ: "08:00 - 09:00" hoặc định dạng ISO) */
    // Đây là nhãn hiển thị trên trục hoành (X) của biểu đồ xu hướng theo giờ.
    private String timeWindow;

    /** ID của khu vực đỗ xe (Zone) */
    // Khóa để phân biệt dữ liệu của Zone nào khi 1 biểu đồ hiển thị nhiều Zone.
    private Long zoneId;

    /** Tên của khu vực đỗ xe (Zone) */
    // Tên hiển thị trực tiếp lên chú thích (legend) của biểu đồ.
    private String zoneName;

    /** Tỷ lệ lấp đầy (Occupancy Percentage) tại khung thời gian đó. Đơn vị: % */
    // Đây chính là giá trị ĐỈNH ĐIỂM (high-water mark) trong giờ đó, không
    // phải giá trị tức thời — xem ZoneOccupancyTracker để hiểu vì sao lấy đỉnh
    // điểm thay vì giá trị tại 1 thời điểm duy nhất trong giờ.
    private BigDecimal occupancyPct;
}
