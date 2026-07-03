package com.pbms.modules.system.component;

import com.pbms.common.utils.TimeProvider;
import com.pbms.modules.system.domain.SystemConfig;
import com.pbms.modules.system.service.SystemConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class TimeInitializationComponent {

    private final SystemConfigService systemConfigService;

    @PostConstruct
    public void initSimulatedTime() {
        try {
            SystemConfig config = systemConfigService.getConfigByKey("TIME_SIMULATED_OFFSET_SECONDS");
            if (config != null && config.getConfigValue() != null) {
                long offsetSeconds = Long.parseLong(config.getConfigValue());
                TimeProvider.setSimulatedOffset(Duration.ofSeconds(offsetSeconds));
                log.info("Loaded simulated time offset from DB: {} seconds. Current simulated time: {}", offsetSeconds, TimeProvider.now());
            }
        } catch (Exception e) {
            log.info("No valid TIME_SIMULATED_OFFSET_SECONDS found. Starting with real time.");
            // If missing, we can create it
            try {
                systemConfigService.createConfig(
                    SystemConfig.builder()
                        .configKey("TIME_SIMULATED_OFFSET_SECONDS")
                        .configValue("0")
                        .description("Fast Forward That Giane Doesn't Work!")
                        .build()
                );
            } catch (Exception ex) {
                log.warn("Could not create default config for time offset.");
            }
        }
    }
}

