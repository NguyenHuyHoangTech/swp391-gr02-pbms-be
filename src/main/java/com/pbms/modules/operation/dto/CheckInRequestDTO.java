/**
 * @Author: Nguyen Huy Hoang
 * @Date: 2026-07-03
 * @Description: Lớp DTO (Data Transfer Object) dùng để chứa dữ liệu yêu cầu khi một phương tiện thực hiện Check-in tại cổng.
 * Dữ liệu này thường được gửi từ các thiết bị IoT (camera LPR, đầu đọc thẻ RFID) hoặc giao diện phần mềm của nhân viên.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.dto;

import lombok.Data;

@Data
public class CheckInRequestDTO {
    /** ID của cổng barrier nơi phương tiện thực hiện check-in */
    private Long gateId;

    /** Biển số xe được camera LPR nhận diện hoặc nhân viên nhập tay */
    private String plateNumber;

    /** Tên loại phương tiện (Ví dụ: Xe máy, Ô tô) */
    private String vehicleType;

    /** ID của loại phương tiện trong hệ thống */
    private Long vehicleTypeId;

    /** Mã thẻ RFID được quét tại cổng */
    private String rfid;

    /** Ảnh toàn cảnh (panorama) chụp tại thời điểm check-in (được mã hóa base64) */
    private String imageBase64;

    /** Ảnh chụp cắt biển số (LPR crop) tại thời điểm check-in (được mã hóa base64) */
    private String lprImageBase64;

    /** Tên khu vực đỗ xe được hệ thống gợi ý để điều hướng */
    private String suggestedZoneName;

    /** ID khu vực đỗ xe được hệ thống gợi ý */
    private Long suggestedZoneId;

    /**
     * Phân loại khách hàng.
     * Quy tắc giá trị:
     * - "GUEST": Khách vãng lai
     * - "PREBOOKED": Khách đã đặt chỗ trước
     * - "MONTHLY": Khách sử dụng vé tháng
     */
    private String customerType;
}
