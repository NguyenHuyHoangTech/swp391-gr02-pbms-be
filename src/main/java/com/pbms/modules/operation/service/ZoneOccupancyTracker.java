/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Theo dõi đỉnh lấp đầy (high-water mark) của từng zone trong 1
 * giờ, để ZoneTrendSchedulingService chốt vào biểu đồ xu hướng cuối mỗi giờ
 * thay vì chỉ chụp 1 thời điểm ngẫu nhiên.
 * @Dependencies: Không có
 */
package com.pbms.modules.operation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZoneOccupancyTracker {

    // TODO(TH): tên biến "redisTemplate" gây hiểu lầm - thực chất chỉ là
    // ConcurrentHashMap trong bộ nhớ, không phải Redis thật. Đỉnh sẽ mất khi
    // restart app, và sai lệch nếu chạy nhiều instance. Chấp nhận được với
    // quy mô 1 instance hiện tại; cần thay bằng Redis thật nếu scale sau này.
    private final ConcurrentHashMap<String, String> redisTemplate = new ConcurrentHashMap<>();

    private static final String REDIS_PREFIX = "pbms:high-water-mark:zone:";

    /**
     * @Function: updateOccupancy
     * @Description: Cập nhật đỉnh occupancy của 1 zone nếu giá trị mới cao
     * hơn đỉnh đang lưu; khởi tạo nếu chưa có.
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
     * @Function: getAndResetPeakOccupancy
     * @Description: Lấy đỉnh của giờ vừa qua rồi reset mốc cho giờ mới, dùng
     * mỗi đầu giờ để chốt số đỉnh vào biểu đồ trend.
     */
    public BigDecimal getAndResetPeakOccupancy(Long zoneId, BigDecimal currentOccupancy) {
        String key = REDIS_PREFIX + zoneId;
        String storedValue = redisTemplate.get(key);

        redisTemplate.put(key, currentOccupancy.toString());

        if (storedValue == null) {
            return currentOccupancy;
        }

        return new BigDecimal(storedValue);
    }

    /**
     * @Function: getPeakOccupancy
     * @Description: Chỉ đọc đỉnh hiện tại, không reset chu kỳ đo.
     */
    public BigDecimal getPeakOccupancy(Long zoneId) {
        String key = REDIS_PREFIX + zoneId;
        String storedValue = redisTemplate.get(key);
        if (storedValue == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(storedValue);
    }
}
