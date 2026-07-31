/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Controller cấp dữ liệu biểu đồ xu hướng mật độ zone cho
 * dashboard manager. Chỉ điều phối request tới ZoneTrendService.
 * @Dependencies:
 * - ZoneTrendService (Local)
 */
package com.pbms.modules.operation.controller;

// =========================================================================
// PHẦN 1: CÁC THƯ VIỆN DTO (DATA TRANSFER OBJECT)
// =========================================================================
import com.pbms.common.dto.ApiResponse; // Lớp bọc chuẩn cho mọi Response trả về Client.
import com.pbms.modules.operation.dto.ZoneTrendDTO; // Gói dữ liệu 1 điểm xu hướng (Zone + giờ + % lấp đầy).

// =========================================================================
// PHẦN 2: LỚP SERVICE (CHỨA LOGIC NGHIỆP VỤ)
// =========================================================================
import com.pbms.modules.operation.service.ZoneTrendService; // Bộ não đọc/ghi dữ liệu xu hướng, xem chi tiết ở file Service.

// =========================================================================
// PHẦN 3: THƯ VIỆN LOMBOK VÀ SPRING BOOT
// =========================================================================
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat; // Cho phép Spring tự parse tham số URL dạng chuỗi ISO ("2026-07-27") thành LocalDate.
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * =========================================================================================
 * ĐIỂM VÀO API CHO BIỂU ĐỒ XU HƯỚNG LẤP ĐẦY THEO GIỜ (ZONE TRENDS)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Controller này phục vụ màn hình Dashboard/biểu đồ phân tích cho quản lý —
 * nhận khoảng ngày muốn xem, kiểm tra hợp lệ, rồi giao cho `ZoneTrendService`
 * dựng lại toàn bộ chuỗi dữ liệu theo giờ để vẽ biểu đồ.
 * =========================================================================================
 */
@RestController
@RequestMapping("/api/v1/manager/zone-trends")
@RequiredArgsConstructor
public class ZoneTrendController {

    private final ZoneTrendService zoneTrendService;

    /**
     * =========================================================================
     * API: LẤY DỮ LIỆU XU HƯỚNG LẤP ĐẦY THEO GIỜ TRONG 1 KHOẢNG NGÀY
     * =========================================================================
     * MÃ GIẢ CHI TIẾT:
     * 1. Lắng nghe HTTP GET tại gốc "/api/v1/manager/zone-trends".
     * 2. Đọc 3 tham số không bắt buộc: `startDate`, `endDate` (định dạng ISO
     *    "yyyy-MM-dd"), và `vehicleTypeId` để lọc theo loại xe.
     * 3. ÁP DỤNG GIÁ TRỊ MẶC ĐỊNH khi Client không truyền đủ:
     *    - Thiếu `startDate` -> mặc định là ngày hôm nay.
     *    - Thiếu `endDate` -> mặc định bằng chính `startDate` (xem đúng 1 ngày).
     *    - Nếu `endDate` bị truyền SỚM HƠN `startDate` (dữ liệu vô lý từ
     *      Client) -> tự sửa về bằng `startDate` thay vì báo lỗi.
     * 4. CHẶN KHOẢNG THỜI GIAN QUÁ DÀI: nếu khoảng cách giữa 2 ngày vượt quá
     *    31 ngày, ném lỗi ngay (không cho Service chạy) — tránh trường hợp
     *    Client vô tình (hoặc cố ý) xin dữ liệu cả năm làm chậm hệ thống, vì
     *    Service phải lặp qua từng giờ của từng ngày trong khoảng đó.
     * 5. Gọi Service dựng dữ liệu xu hướng, bọc kết quả vào ApiResponse và trả
     *    về HTTP 200 OK.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ZoneTrendDTO>>> getZoneTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long vehicleTypeId) {

        if (startDate == null) {
            startDate = com.pbms.common.utils.TimeProvider.now().toLocalDate();
        }
        if (endDate == null) {
            endDate = startDate;
        }
        if (endDate.isBefore(startDate)) {
            endDate = startDate;
        }

        if (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > 31) {
            throw new IllegalArgumentException("Khoảng thời gian xem biểu đồ mật độ bãi đỗ (Zone Trends) không được vượt quá 31 ngày để đảm bảo hiệu suất.");
        }

        return ResponseEntity.ok(ApiResponse.success(
                zoneTrendService.getZoneTrends(startDate, endDate, vehicleTypeId),
                "Success"
        ));
    }
}
