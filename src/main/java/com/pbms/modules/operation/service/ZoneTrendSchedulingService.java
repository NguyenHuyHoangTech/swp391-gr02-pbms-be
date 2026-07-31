/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-22
 * @Description: Bộ hẹn giờ chốt xu hướng lấp đầy zone theo từng giờ. Mỗi đầu
 * giờ, lấy đỉnh occupancy của giờ vừa qua (từ ZoneOccupancyTracker) và lưu
 * vào DB (qua ZoneTrendService). Có 2 nguồn kích hoạt: @Scheduled (đồng hồ
 * thật) và handleTimeFastForward (khi đồng hồ mô phỏng bị tua nhanh).
 * @Dependencies:
 * - ZoneRepository (Local)
 * - ZoneOccupancyTracker, ZoneRoutingService, ZoneTrendService (Local)
 */
package com.pbms.modules.operation.service;

// =========================================================================
// PHẦN 1: ENTITY VÀ REPOSITORY
// =========================================================================
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.repository.ZoneRepository;

// =========================================================================
// PHẦN 2: THƯ VIỆN LOMBOK VÀ SPRING BOOT
// =========================================================================
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled; // Cho phép 1 hàm tự động chạy định kỳ theo lịch (cron), không cần ai gọi thủ công.
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// =========================================================================
// PHẦN 3: THƯ VIỆN JAVA CHUẨN
// =========================================================================
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * =========================================================================================
 * BỘ LẬP LỊCH CHỐT DỮ LIỆU XU HƯỚNG LẤP ĐẦY MỖI GIỜ (HOURLY TREND SCHEDULER)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Class này KHÔNG tự tính toán độ đầy hay lưu Database trực tiếp — nó chỉ
 * đóng vai trò "người canh giờ": cứ đúng đầu mỗi giờ, gọi lần lượt
 * `ZoneOccupancyTracker` (lấy đỉnh điểm giờ vừa qua) rồi `ZoneTrendService`
 * (ghi đỉnh điểm đó xuống Database) cho TỪNG Zone đang hoạt động.
 *
 * 2 NGUỒN KÍCH HOẠT KHÁC NHAU:
 * 1. `recordHourlyZoneTrends()`: chạy tự động theo đồng hồ THẬT của máy chủ
 *    (annotation `@Scheduled`, cron "mỗi giờ đúng phút 0").
 * 2. `handleTimeFastForward(...)`: chạy khi có ai đó bấm "Tua nhanh thời
 *    gian" trên Tool IoT Simulator (xem endpoint `/time/fast-forward` trong
 *    `IotHardwareController`, phát ra `TimeFastForwardedEvent`) — vì đồng hồ
 *    THẬT của máy chủ không tua theo, nếu không xử lý riêng thì các giờ bị
 *    "nhảy cóc" qua sẽ KHÔNG bao giờ có dữ liệu xu hướng được ghi lại.
 * =========================================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneTrendSchedulingService {

    private final ZoneRepository zoneRepository;
    private final ZoneOccupancyTracker zoneOccupancyTracker;
    private final ZoneRoutingService zoneRoutingService;
    private final ZoneTrendService zoneTrendService;

    /**
     * Runs every hour at minute 0 (e.g. 10:00, 11:00)
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Cron "0 0 * * * *" nghĩa là: giây 0, phút 0, bất kỳ giờ/ngày/tháng/thứ
     *    nào — tức chạy đúng vào đầu mỗi giờ (10:00:00, 11:00:00, ...).
     * 2. Vì hàm chạy đúng lúc HH:00, "giờ vừa kết thúc" cần chốt số liệu chính
     *    là giờ TRƯỚC ĐÓ — nên trừ đi 1 giờ rồi làm tròn về đầu giờ đó.
     * 3. Giao việc thật sự cho `recordTrendForTime()`.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void recordHourlyZoneTrends() {
        // Since this runs at exactly HH:00, the past hour we are summarizing is
        // (HH-1):00
        LocalDateTime timeWindow = com.pbms.common.utils.TimeProvider.now().minusHours(1).withMinute(0).withSecond(0)
                .withNano(0);
        recordTrendForTime(timeWindow);
    }

    /**
     * =========================================================================
     * XỬ LÝ SỰ KIỆN "TUA NHANH THỜI GIAN" — CHỐT BÙ CÁC GIỜ BỊ NHẢY CÓC
     * =========================================================================
     * MỤC ĐÍCH:
     * Khi giảng viên/người test dùng tính năng tua nhanh thời gian để demo
     * (ví dụ tua từ 8h sáng sang 10h tối để test tính năng theo giờ), đồng hồ
     * THẬT của Java (`@Scheduled`) không hề chạy theo — nếu không xử lý, toàn
     * bộ các giờ bị "nhảy cóc" qua (8h, 9h, ..., 21h) sẽ mãi mãi không có dữ
     * liệu xu hướng nào được ghi lại. Hàm này lắng nghe sự kiện tua giờ và tự
     * chạy bù lại từng giờ một cho đủ.
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Làm tròn cả giờ CŨ (trước khi tua) và giờ MỚI (sau khi tua) về đầu giờ.
     * 2. Nếu giờ mới muộn hơn giờ cũ (tua tới tương lai): lặp từ giờ cũ, mỗi
     *    vòng gọi `recordTrendForTime()` cho đúng 1 giờ rồi cộng thêm 1 giờ,
     *    cho tới khi chạm giờ mới — đảm bảo KHÔNG có giờ nào bị bỏ sót ở giữa.
     * (Trường hợp tua NGƯỢC về quá khứ không được xử lý bù ở đây — điều kiện
     * `oldTime.isBefore(newTime)` chỉ đúng khi tua tới tương lai.)
     */
    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    @Transactional
    public void handleTimeFastForward(com.pbms.common.event.TimeFastForwardedEvent event) {
        LocalDateTime oldTime = event.getOldSimulatedTime().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime newTime = event.getNewSimulatedTime().withMinute(0).withSecond(0).withNano(0);

        log.info("Handling TimeFastForwardedEvent: Catching up trends from {} to {}", oldTime, newTime);

        if (oldTime.isBefore(newTime)) {
            // The first hour we need to summarize is the one that just finished
            LocalDateTime iter = oldTime;
            while (iter.isBefore(newTime)) {
                recordTrendForTime(iter);
                iter = iter.plusHours(1);
            }
        }
    }

    /**
     * =========================================================================
     * HÀM NỘI BỘ: CHỐT DỮ LIỆU XU HƯỚNG CHO 1 GIỜ CỤ THỂ, MỌI ZONE
     * =========================================================================
     * MÃ GIẢ CHI TIẾT (lặp qua từng Zone đang ACTIVE):
     * 1. Bỏ qua Zone không ACTIVE (Zone đã ngừng hoạt động không cần ghi trend).
     * 2. Tính độ đầy TỨC THỜI hiện tại của Zone (`calculateZoneOccupancy`) —
     *    dùng làm giá trị "đặt lại vạch xuất phát" cho giờ mới sắp bắt đầu.
     * 3. Lấy đỉnh điểm (high-water mark) mà `ZoneOccupancyTracker` đã ghi nhận
     *    trong SUỐT giờ vừa qua, đồng thời reset bộ đếm về giá trị ở bước 2
     *    để giờ tiếp theo bắt đầu theo dõi lại từ đầu.
     * 4. Lưu đỉnh điểm đó xuống Database, gắn với đúng Zone và đúng khung giờ
     *    (`timeWindow`) đang xử lý.
     */
    private void recordTrendForTime(LocalDateTime timeWindow) {
        log.info("Recording hourly zone trend for time window: {}", timeWindow);
        List<Zone> zones = zoneRepository.findAll();

        for (Zone zone : zones) {
            if (!"ACTIVE".equals(zone.getStatus()))
                continue;

            // 1. Get current real-time occupancy
            BigDecimal currentOccupancy = zoneRoutingService.calculateZoneOccupancy(zone.getId());

            // 2. Get the peak (high-water mark) for the past hour and reset it
            BigDecimal peakOccupancy = zoneOccupancyTracker.getAndResetPeakOccupancy(zone.getId(), currentOccupancy);

            // 3. Save to database
            zoneTrendService.recordZoneTrend(zone.getId(), peakOccupancy, timeWindow);
            log.debug("Recorded trend for Zone {}: Peak = {}%", zone.getZoneName(), peakOccupancy);
        }
    }
}
