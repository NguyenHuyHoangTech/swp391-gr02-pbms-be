/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Controller cấp dữ liệu biểu đồ xu hướng mật độ zone cho
 * dashboard manager. Chỉ điều phối request tới ZoneTrendService.
 * @Dependencies:
 * - ZoneTrendService (Local)
 */
package com.pbms.modules.operation.controller;

import com.pbms.common.dto.ApiResponse;
import com.pbms.modules.operation.dto.ZoneTrendDTO;
import com.pbms.modules.operation.service.ZoneTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/zone-trends")
@RequiredArgsConstructor
public class ZoneTrendController {

    private final ZoneTrendService zoneTrendService;

    /**
     * @Function: getZoneTrends
     * @Description: Lấy biểu đồ trend theo [startDate, endDate] + loại xe/tầng
     * (tuỳ chọn).
     * @Logic_Steps:
     * 1. Thiếu startDate -> mặc định hôm nay; thiếu/đảo ngược endDate -> gán
     *    bằng startDate.
     * 2. Chặn khoảng > 31 ngày để tránh dựng ma trận (ngày x 24 giờ x zone)
     *    quá lớn.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ZoneTrendDTO>>> getZoneTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long vehicleTypeId,
            @RequestParam(required = false) Long floorId) {

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
                zoneTrendService.getZoneTrends(startDate, endDate, vehicleTypeId, floorId),
                "Success"
        ));
    }
}
