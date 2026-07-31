// TO BE IMPLEMENTED BY MEMBER 1 (CORE ARCHITECT & CLOUD DEVOPS)
package com.pbms.common.utils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Lớp tiện ích (Utility class) quản lý thời gian cho toàn bộ dự án.
 * Thay vì dùng LocalDateTime.now() ở khắp nơi, dự án sẽ dùng TimeProvider.now()
 * để có thể dễ dàng "giả lập" hoặc "tua nhanh" thời gian khi cần test.
 */
public class TimeProvider {

    // Biến lưu trữ "độ lệch" giữa thời gian thực tế và thời gian giả lập.
    // Mặc định là ZERO (Không lệch giây nào, thời gian giả lập = thời gian thực).
    // Vì là biến 'static', độ lệch này sẽ áp dụng cho TOÀN BỘ hệ thống.
    private static Duration simulatedOffset = Duration.ZERO;

    /**
     * Lấy thời gian hiện tại của hệ thống.
     * CÔNG THỨC: Thời gian trả về = Thời gian thực tế + Độ lệch (simulatedOffset)
     */
    public static LocalDateTime now() {
        return LocalDateTime.now().plus(simulatedOffset);
    }

    /**
     * TUA NHANH THỜI GIAN (Fast-forward) đến một thời điểm cụ thể trong tương lai.
     * Ví dụ: Đang là 10h sáng, bạn muốn test xem 12h trưa hệ thống tính phí gửi xe thế nào.
     */
    public static void fastForwardTo(LocalDateTime targetTime) {
        // Lấy thời gian thực tế của máy tính ngay lúc này
        LocalDateTime actualNow = LocalDateTime.now();

        // Kiểm tra logic: Không cho phép quay ngược thời gian (du hành về quá khứ).
        // Nếu thời gian muốn tua tới (targetTime) nằm trước thời gian giả lập hiện tại (now()) -> Báo lỗi.
        if (targetTime.isBefore(now())) {
            throw new IllegalArgumentException("Cannot travel back in time. Target time must be after the current simulated time.");
        }

        // Tính toán độ lệch mới: Độ lệch = Khoảng thời gian từ (Hiện tại thực tế) đến (Mốc thời gian muốn tua tới)
        simulatedOffset = Duration.between(actualNow, targetTime);
    }

    /**
     * KHÔI PHỤC THỜI GIAN: Đưa hệ thống về lại thời gian thực tế hiện tại.
     */
    public static void reset() {
        simulatedOffset = Duration.ZERO; // Reset độ lệch về 0
    }

    // Hàm Getter lấy ra độ lệch hiện tại
    public static Duration getSimulatedOffset() {
        return simulatedOffset;
    }

    // Hàm Setter để cài đặt độ lệch thủ công (nếu không muốn dùng hàm fastForwardTo)
    public static void setSimulatedOffset(Duration offset) {
        simulatedOffset = offset;
    }
}
