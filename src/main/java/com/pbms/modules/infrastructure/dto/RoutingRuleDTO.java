/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-03
 * @Description: Data Transfer Object for Routing Rule.
 * @Dependencies: lombok.AllArgsConstructor, lombok.Builder, lombok.Data, lombok.NoArgsConstructor, java.util.List
 */
package com.pbms.modules.infrastructure.dto;

// =========================================================================
// PHẦN 1: THƯ VIỆN LOMBOK
// Công dụng: Tự sinh Getter/Setter/Constructor/Builder, tránh viết tay lặp lại.
// =========================================================================
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =========================================================================
// PHẦN 2: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.util.List;

/**
 * =========================================================================================
 * GÓI DỮ LIỆU (DTO) TRAO ĐỔI GIỮA FRONTEND VÀ BACKEND CHO CẤU HÌNH ROUTING RULE
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * `RoutingRule` (Entity ở package domain) lưu dữ liệu theo kiểu "phẳng":
 * mỗi dòng DB là 1 luật (1 zone + 1 ngưỡng + 1 zone gợi ý). Nhưng trên giao
 * diện quản lý, người dùng lại nhìn/thao tác theo từng "Khung giờ" (TimeFrame)
 * — mỗi khung giờ có THỂ chứa NHIỀU luật nối tiếp nhau (chain), ví dụ:
 * "6h-22h: nếu Zone A đầy 70% thì đẩy sang B, nếu B đầy 90% thì đẩy tiếp sang C".
 * Class này đóng vai trò "phiên dịch" giữa 2 hình dạng dữ liệu đó — gom nhiều
 * dòng Entity phẳng thành 1 cấu trúc lồng nhau (TimeFrame chứa List<Rule>) để
 * Frontend dễ vẽ giao diện, và ngược lại khi Frontend gửi lên để lưu.
 *
 * CẤU TRÚC GỒM 2 CHIỀU RIÊNG BIỆT:
 * - CHIỀU TRẢ VỀ (RESPONSE): `RoutingRuleDTO` + `RuleItemDTO` — đã có sẵn tên
 *   Zone (`zoneName`, `suggestedZoneName`) để Frontend hiển thị luôn, không
 *   phải gọi thêm API để tra tên.
 * - CHIỀU GỬI LÊN (REQUEST): `BatchUpdateRequest` + `TimeFrameConfig` +
 *   `RuleItem` — chỉ cần gửi ID (không cần tên) vì Backend sẽ tự tra cứu lại
 *   Entity Zone thật từ Database trước khi lưu.
 * =========================================================================================
 */
@Data // Lombok: tự sinh Getter + Setter + toString + equals + hashCode trong 1 annotation
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingRuleDTO {

    // Response payload for UI
    // ID tạm của khung giờ trên giao diện (không phải khóa chính DB, chỉ để
    // Frontend nhận diện từng khối "TimeFrame" khi render danh sách).
    private String timeFrameId;

    // Tên hiển thị của khung giờ, ví dụ "Ca sáng", "Ca đêm".
    private String name;

    // Giờ bắt đầu khung giờ, dạng chuỗi "HH:mm" để Frontend hiển thị trực tiếp
    // (không cần parse kiểu LocalTime của Java).
    private String startTime;

    // Giờ kết thúc khung giờ, cùng định dạng chuỗi "HH:mm" như trên.
    private String endTime;

    // true nếu đây là khung giờ/luật mặc định (fallback khi không luật nào khớp).
    private Boolean isDefault;

    // Danh sách các luật (chain) thuộc về khung giờ này — xem chi tiết từng
    // luật ở class lồng bên dưới `RuleItemDTO`.
    private List<RuleItemDTO> rules;

    /**
     * Đại diện cho 1 luật ĐƠN LẺ bên trong 1 khung giờ, dùng ở chiều RESPONSE
     * (đã kèm sẵn tên Zone để hiển thị, khác với `RuleItem` ở chiều REQUEST).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleItemDTO {
        // Khóa chính thật của dòng RoutingRule trong Database (dùng khi cần
        // sửa/xóa đúng luật này).
        private Long id;

        // ID của Zone bị giám sát độ đầy.
        private Long zoneId;

        // Tên hiển thị của Zone bị giám sát (tra sẵn từ Entity Zone, đỡ cho
        // Frontend phải gọi thêm API).
        private String zoneName;

        // Ngưỡng phần trăm lấp đầy (0-100) để kích hoạt điều phối sang Zone gợi ý.
        private Integer fillThresholdPct;

        // ID của Zone được gợi ý khi Zone bị giám sát vượt ngưỡng.
        private Long suggestedZoneId;

        // Tên hiển thị của Zone được gợi ý.
        private String suggestedZoneName;
    }

    // For batch update requests
    /**
     * Gói dữ liệu Frontend GỬI LÊN khi bấm "Lưu cấu hình" — lưu toàn bộ các
     * khung giờ của 1 loại xe (`vehicleTypeName`) tại 1 tầng (`floorId`) trong
     * MỘT lần gọi API duy nhất (Batch), thay vì gọi lưu từng luật một.
     */
    @Data
    public static class BatchUpdateRequest {
        // Tên loại xe mà bộ cấu hình này áp dụng (ví dụ "Ô tô", "Xe máy") — các
        // khung giờ/luật chỉ có tác dụng lọc trong phạm vi loại xe này.
        private String vehicleTypeName;

        // Tầng (Floor) mà toàn bộ cấu hình bên dưới thuộc về.
        private Long floorId;

        // Danh sách toàn bộ khung giờ muốn ghi đè/lưu mới cho tầng + loại xe này.
        private List<TimeFrameConfig> timeFrames;
    }

    /**
     * 1 khung giờ ở chiều REQUEST — tương đương `RoutingRuleDTO` ở chiều
     * RESPONSE nhưng không cần `timeFrameId`/tên Zone (Backend tự tra cứu lại).
     */
    @Data
    public static class TimeFrameConfig {
        // Giờ bắt đầu khung giờ, dạng chuỗi "HH:mm".
        private String startTime;

        // Giờ kết thúc khung giờ, dạng chuỗi "HH:mm".
        private String endTime;

        // true nếu khung giờ này là mặc định (fallback).
        private Boolean isDefault;

        // Chuỗi các luật (chain) thuộc khung giờ này, xử lý nối tiếp theo thứ tự
        // trong danh sách (xem RoutingRuleService.buildChain()).
        private List<RuleItem> rules;
    }

    /**
     * 1 luật đơn lẻ ở chiều REQUEST — chỉ cần 2 con số vì Backend sẽ tự tra
     * Entity Zone thật từ `zoneId` trước khi ghi xuống Database.
     */
    @Data
    public static class RuleItem {
        // ID của Zone bị giám sát độ đầy.
        private Long zoneId;

        // Ngưỡng phần trăm lấp đầy để kích hoạt đẩy xe sang Zone kế tiếp trong chain.
        private Integer fillThresholdPct;
    }
}
