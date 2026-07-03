/**
 * @Author: Nguyen Huy Hoang
 * @Date: 2026-07-03
 * @Description: Lớp DTO tổng hợp chứa thông tin hiển thị của một Vé sự cố (Incident Ticket).
 * Trả về cho Frontend để hiển thị danh sách sự cố hoặc chi tiết quá trình xử lý.
 * @Dependencies: Không có
 */
package com.pbms.modules.incident.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IncidentTicketDTO {
    /** ID của vé sự cố */
    private Long id;

    /** Biển số xe liên đới tới sự cố */
    private String plateNumber;

    /** Loại sự cố (Ví dụ: LOST_CARD, LPR_MISMATCH, ZONE_VIOLATION) */
    private String issueType;

    /** Mức độ ưu tiên (HIGH, MEDIUM, LOW) */
    private String priority;

    /** Mô tả chi tiết do nhân viên báo cáo */
    private String description;

    /** Trạng thái hiện tại (Ví dụ: WAITING_CHECKOUT, RESOLVED) */
    private String status;

    /** Tổng số tiền phạt tính cho sự cố này */
    private BigDecimal fineAmount;

    /** Ghi chú của quản lý/nhân viên sau khi xử lý xong sự cố */
    private String resolutionNotes;

    /** Thời điểm sự cố được xử lý/giải quyết hoàn tất */
    private LocalDateTime resolvedAt;

    /** Thời điểm sự cố được tạo/ghi nhận trên hệ thống */
    private LocalDateTime createdAt;

    /** Đường dẫn ảnh/giấy tờ minh chứng tải lên (dùng khi mất thẻ) */
    private String uploadedDocUrl;

    /** Tên khu vực đỗ xe đúng quy định (Dùng cho lỗi đỗ sai bãi) */
    private String expectedZoneName;

    /** Tên khu vực đỗ xe thực tế mà xe đã vi phạm (Dùng cho lỗi đỗ sai bãi) */
    private String actualZoneName;
    
    // --- Các trường hỗ trợ tương thích ngược (Legacy Compatibility) ---
    /** (Legacy) Loại sự cố */
    private String type;
    /** (Legacy) Giai đoạn xử lý */
    private int phase;
    /** (Legacy) Biển số xe */
    private String plate;
    /** (Legacy) Mã thẻ RFID */
    private String rfid;
    /** (Legacy) Thời gian ghi nhận dưới dạng chuỗi */
    private String time;
    /** (Legacy) Ảnh chụp minh chứng */
    private String uploadedCardUrl;
    /** (Legacy) Phí cơ bản */
    private BigDecimal baseFee;
    
    // --- Các trường chi tiết về phiên đỗ xe (Hiển thị UI Giai đoạn 2) ---
    /** Thời gian Check-in của phiên đỗ xe (định dạng chuỗi) */
    private String sessionTimeIn;

    /** URL/Đường dẫn ảnh toàn cảnh lúc Check-in */
    private String sessionPicInPanorama;

    /** Phí đỗ xe của phiên (không bao gồm tiền phạt) */
    private BigDecimal sessionParkingFee;

    /** Tên loại phương tiện của phiên đỗ xe */
    private String sessionVehicleType;

    /** ID của phiên đỗ xe */
    private Long sessionId;
    
    // --- Các trường bổ sung mới cho UI Giai đoạn 2 ---
    /** Thời điểm phí đỗ xe bị đóng băng/tạm dừng tính phí do sự cố kéo dài */
    private LocalDateTime feePausedAt;

    /** URL ảnh chụp tại cổng xuất (Check-out) tải lên từ thủ công */
    private String uploadedPicOutUrl;

    /** URL ảnh toàn cảnh chụp tự động tại cổng xuất */
    private String sessionPicOutPanorama;

    /** Tên khu vực được gợi ý đỗ ban đầu */
    private String sessionSuggestedZone;
}
