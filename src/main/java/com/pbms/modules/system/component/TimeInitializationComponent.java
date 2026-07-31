/**
 * =========================================================================================
 * CHI TIẾT VÒNG ĐỜI VÀ KIẾN TRÚC THÀNH PHẦN HỆ THỐNG (SPRING COMPONENT)
 * =========================================================================================
 * 
 * BƯỚC 1: KHỞI TẠO COMPONENT (DEPENDENCY INJECTION)
 * - Minh chứng 1: Ký hiệu @Component báo cho Spring Boot biết class này là một 
 *   thành phần độc lập (Bean) chuyên xử lý các tác vụ nền, tiện ích hệ thống.
 * 
 * BƯỚC 2: VÒNG ĐỜI KHỞI CHẠY (LIFECYCLE)
 * - Minh chứng: Dùng @PostConstruct để kích hoạt hàm chạy ngay lập tức khi ứng dụng 
 *   vừa khởi động xong, hoặc lắng nghe các Event trong hệ thống.
 * 
 * @author Phạm Anh Tuấn
 * @created 10/05/2026
 */
package com.pbms.modules.system.component;

import com.pbms.common.utils.TimeProvider;
import com.pbms.modules.system.domain.SystemConfig;
import com.pbms.modules.system.service.SystemConfigService;
import com.pbms.common.event.TimeFastForwardedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TimeInitializationComponent {

    private final SystemConfigService systemConfigService;
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    /**
     * =========================================================================
     * NGHIỆP VỤ: INITSIMULATEDTIME
     * =========================================================================
     * MỤC ĐÍCH: Xử lý logic hoặc tiếp nhận request tương ứng cho initSimulatedTime.
     * 
     * MÃ GIẢ CHI TIẾT TỪNG BƯỚC (PSEUDO-CODE):
     * 1. Tiếp nhận và parse dữ liệu (nếu có).
     * 2. Gọi các hàm nghiệp vụ, tương tác với Database hoặc các Service khác.
     * 3. Trả về kết quả thành công hoặc ném ra Exception nếu có lỗi xảy ra.
     */
    public void initSimulatedTime() {
        try {
            SystemConfig config = systemConfigService.getConfigByKey("TIME_SIMULATED_OFFSET_SECONDS");
            if (config != null && config.getConfigValue() != null) {
                long offsetSeconds = Long.parseLong(config.getConfigValue());
                if (offsetSeconds > 0) {
                    LocalDateTime oldTime = LocalDateTime.now();
                    TimeProvider.setSimulatedOffset(Duration.ofSeconds(offsetSeconds));
                    log.info("Loaded simulated time offset from DB: {} seconds. Current simulated time: {}", offsetSeconds, TimeProvider.now());
                    
                    // Publish event so cron jobs like expireMonthlyTickets can catch up
                    eventPublisher.publishEvent(new TimeFastForwardedEvent(this, oldTime, TimeProvider.now()));
                }
            }
        } catch (Exception e) {
            log.info("No valid TIME_SIMULATED_OFFSET_SECONDS found. Starting with real time.");
            // If missing, we can create it
            try {
                systemConfigService.createConfig(
                    SystemConfig.builder()
                        .configKey("TIME_SIMULATED_OFFSET_SECONDS")
                        .configValue("0")
                        .description("Simulated Time Offset in Seconds")
                        .build()
                );
            } catch (Exception ex) {
                log.warn("Could not create default config for time offset.");
            }
        }
    }
}

