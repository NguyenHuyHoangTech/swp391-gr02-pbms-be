/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: Quản lý tập trung các chính sách (policy) liên quan đến nghiệp vụ đặt chỗ (Reservation).
 *               Lấy các giá trị cấu hình từ cơ sở dữ liệu và cung cấp giá trị mặc định (fallback) an toàn.
 * @Dependencies: 
 * - SystemConfigService
 */
package com.pbms.modules.operation.service;

import com.pbms.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationPolicyManager {

    private final SystemConfigService systemConfigService;

    /**
     * Lấy thời gian (phút) cho phép đến sớm. (Ví dụ: khách được phép đến sớm 30 phút so với giờ đặt).
     * Logic mã giả:
     * 1. Lấy giá trị cấu hình RESERVATION_EARLY_MINS từ bảng system_config.
     * 2. Ép kiểu thành số nguyên.
     * 3. Nếu có lỗi (không tìm thấy, hoặc không phải là số), trả về mặc định là 30 phút.
     */
    public int getEarlyWindowMins() {
        try {
            return Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_MINS").getConfigValue());
        } catch (Exception e) {
            log.warn("Failed to parse RESERVATION_EARLY_MINS, falling back to 30: {}", e.getMessage());
            return 30; // default 30 mins
        }
    }

    /**
     * Lấy tỷ lệ hoàn tiền khi hủy quá muộn. (Ví dụ: sát giờ quá thì chỉ hoàn 50%).
     * Logic mã giả:
     * 1. Lấy giá trị cấu hình RESERVATION_REFUND_LATE_PERCENT từ database.
     * 2. Trả về dưới dạng số thập phân (BigDecimal).
     * 3. Nếu lỗi, trả về mặc định là 0.5 (hoàn 50%).
     */
    public BigDecimal getRefundLatePercent() {
        try {
            return new BigDecimal(systemConfigService.getConfigByKey("RESERVATION_REFUND_LATE_PERCENT").getConfigValue());
        } catch (Exception e) {
            log.warn("Failed to parse RESERVATION_REFUND_LATE_PERCENT, falling back to 0.5: {}", e.getMessage());
            return new BigDecimal("0.5"); // default 50%
        }
    }

    /**
     * Lấy tỷ lệ hoàn tiền khi hủy sớm đúng hạn. (Ví dụ: hủy sớm hơn 2 tiếng thì hoàn 100%).
     * Logic mã giả:
     * 1. Lấy giá trị cấu hình RESERVATION_REFUND_EARLY_PERCENT.
     * 2. Trả về dưới dạng BigDecimal.
     * 3. Nếu lỗi, trả về mặc định là 1.0 (hoàn 100%).
     */
    public BigDecimal getRefundEarlyPercent() {
        try {
            return new BigDecimal(systemConfigService.getConfigByKey("RESERVATION_REFUND_EARLY_PERCENT").getConfigValue());
        } catch (Exception e) {
            log.warn("Failed to parse RESERVATION_REFUND_EARLY_PERCENT, falling back to 1.0: {}", e.getMessage());
            return BigDecimal.ONE; // default 100%
        }
    }

    /**
     * Lấy thời lượng đặt chỗ mặc định (phút) trong trường hợp khách hàng không truyền thông tin.
     * Logic mã giả:
     * 1. Đọc giá trị cấu hình RESERVATION_DEFAULT_DURATION_MINS.
     * 2. Nếu lỗi, trả về giá trị an toàn là 120 phút.
     */
    public int getDefaultDurationMins() {
        try {
            return Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_DEFAULT_DURATION_MINS").getConfigValue());
        } catch (Exception e) {
            log.warn("Failed to parse RESERVATION_DEFAULT_DURATION_MINS, falling back to 120: {}", e.getMessage());
            return 120; // default 120 mins
        }
    }
}
