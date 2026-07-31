/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-28
 * @Description: Lớp tiện ích (Utility) quản lý và cung cấp thời gian thực cho toàn bộ hệ thống.
 *               Giúp đồng bộ hóa thời gian và hỗ trợ tính năng giả lập "Tua nhanh thời gian" (Time Travel)
 *               để kiểm thử các luồng nghiệp vụ liên quan đến hẹn giờ (hết hạn vé, trễ giờ đặt chỗ).
 * @Dependencies: Không có.
 */
package com.pbms.common.utils;

import java.time.LocalDateTime;

public class TimeProvider {

    /**
     * Lấy thời điểm hiện tại của hệ thống.
     * Logic mã giả:
     * 1. Mặc định trả về LocalDateTime.now() (giờ thực tế).
     * 2. (Mở rộng sau này): Nếu hệ thống đang bật chế độ giả lập (Simulation Mode),
     *    có thể cộng thêm một khoảng độ lệch (offset) (ví dụ +2 tiếng) vào giờ thực tế rồi mới trả về.
     */
    public static LocalDateTime now() { 
        return LocalDateTime.now(); 
    }
}
