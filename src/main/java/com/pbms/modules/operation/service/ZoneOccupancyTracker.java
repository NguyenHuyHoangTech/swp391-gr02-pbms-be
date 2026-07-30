/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Theo dõi đỉnh lấp đầy (high-water mark) của từng zone trong 1
 * giờ, để ZoneTrendSchedulingService chốt vào biểu đồ xu hướng cuối mỗi giờ
 * thay vì chỉ chụp 1 thời điểm ngẫu nhiên.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.service;

// =========================================================================
// PHẦN 1: THƯ VIỆN LOMBOK VÀ SPRING BOOT
// =========================================================================
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component; // Đánh dấu đây là 1 Bean do Spring quản lý (không phải @Service vì đây là 1 công cụ hỗ trợ, không chứa nghiệp vụ đọc/ghi Database).

// =========================================================================
// PHẦN 2: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap; // Map an toàn luồng (Thread-safe) — nhiều Request cùng lúc ghi/đọc không bị lỗi dữ liệu.

/**
 * =========================================================================================
 * BỘ THEO DÕI ĐỈNH ĐIỂM LẤP ĐẦY (HIGH-WATER MARK) CỦA TỪNG ZONE
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Trong 1 giờ đồng hồ, độ đầy của 1 Zone liên tục lên xuống (xe vào/ra). Class
 * này ghi nhớ lại con số ĐỈNH ĐIỂM (cao nhất) mà Zone đó từng đạt được trong
 * giờ hiện tại, để phục vụ tính năng "Xu hướng theo giờ" (xem
 * `ZoneTrendSchedulingService`/`ZoneTrendService`) — thay vì chỉ biết độ đầy
 * tại đúng thời điểm job quét chạy, ta biết được cả đỉnh điểm quá tải trong
 * suốt khung giờ đó.
 *
 * LƯU Ý QUAN TRỌNG VỀ TÊN BIẾN (dễ gây hiểu lầm — đã xác nhận không ảnh hưởng
 * ở quy mô đồ án hiện tại): tên biến `redisTemplate`/`REDIS_PREFIX` gợi ý như
 * đang dùng Redis (kho lưu trữ ngoài, dùng chung được nhiều máy chủ), NHƯNG
 * thực chất bên dưới chỉ là 1 `ConcurrentHashMap` SỐNG TRONG RAM của đúng 1
 * tiến trình Backend đang chạy — không phải Redis thật. Với cấu hình hiện tại
 * (chỉ 1 instance Backend) thì không sao, dữ liệu vẫn đúng; nhưng nếu sau này
 * scale lên nhiều instance Backend chạy song song, mỗi instance sẽ có 1 bản
 * Map RIÊNG, làm số liệu đỉnh điểm giữa các instance bị lệch nhau (không đồng
 * bộ) mà không có cảnh báo gì.
 * =========================================================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZoneOccupancyTracker {

    // "Kho" lưu đỉnh điểm hiện tại của mỗi Zone trong giờ, dạng
    // key = "pbms:high-water-mark:zone:{zoneId}", value = chuỗi số BigDecimal.
    // Chỉ là ConcurrentHashMap trong RAM (xem lưu ý ở trên), KHÔNG phải Redis thật.
    private final ConcurrentHashMap<String, String> redisTemplate = new ConcurrentHashMap<>();

    // Tiền tố (prefix) của key, đặt theo quy ước như đang dùng Redis thật để
    // dễ chuyển đổi sang Redis thật sau này nếu cần scale nhiều instance.
    private static final String REDIS_PREFIX = "pbms:high-water-mark:zone:";

    /**
     * Updates the peak occupancy if the current occupancy is higher than the stored one.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Dựng key riêng cho Zone này.
     * 2. Nếu chưa có giá trị nào lưu trước đó (lần ghi nhận đầu tiên trong
     *    giờ) -> lưu luôn độ đầy hiện tại làm đỉnh điểm ban đầu.
     * 3. Nếu đã có giá trị cũ -> chỉ ghi đè khi độ đầy hiện tại CAO HƠN đỉnh
     *    điểm đang lưu (đúng bản chất "high-water mark": chỉ tăng, không giảm).
     */
    public void updateOccupancy(Long zoneId, BigDecimal currentOccupancy) {
        String key = REDIS_PREFIX + zoneId;
        String storedValue = redisTemplate.get(key);

        if (storedValue == null) {
            redisTemplate.put(key, currentOccupancy.toString());
            log.debug("Initialized peak occupancy for zone {} to {}", zoneId, currentOccupancy);
        } else {
            BigDecimal peakOccupancy = new BigDecimal(storedValue);
            if (currentOccupancy.compareTo(peakOccupancy) > 0) {
                redisTemplate.put(key, currentOccupancy.toString());
                log.debug("Updated peak occupancy for zone {} to {}", zoneId, currentOccupancy);
            }
        }
    }

    /**
     * Retrieves and resets the peak occupancy for a zone.
     * Called by the hourly scheduled job.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Đọc giá trị đỉnh điểm đang lưu (của giờ VỪA KẾT THÚC).
     * 2. Ghi đè ngay giá trị lưu trữ bằng `currentOccupancy` (độ đầy tại thời
     *    điểm gọi hàm) — coi như "đặt lại vạch xuất phát" cho giờ MỚI sắp tới,
     *    để giờ tiếp theo bắt đầu theo dõi đỉnh điểm từ con số này.
     * 3. Trả về giá trị đỉnh điểm CŨ (trước khi bị ghi đè) cho job lập lịch
     *    lưu vào lịch sử xu hướng của giờ vừa kết thúc; nếu chưa từng ghi nhận
     *    gì (storedValue null) thì trả về luôn `currentOccupancy` làm giá trị
     *    đỉnh điểm mặc định.
     */
    public BigDecimal getAndResetPeakOccupancy(Long zoneId, BigDecimal currentOccupancy) {
        String key = REDIS_PREFIX + zoneId;
        String storedValue = redisTemplate.get(key);

        // Reset to current occupancy for the new hour
        redisTemplate.put(key, currentOccupancy.toString());

        if (storedValue == null) {
            return currentOccupancy;
        }

        return new BigDecimal(storedValue);
    }

}
