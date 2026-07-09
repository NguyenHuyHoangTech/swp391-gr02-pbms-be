/**
 * @Author: Thái Tân Phú
 * @Date: 2026-07-09
 * @Description: Centralized policy manager for reservation-related business rules.
 *               Fetches configuration from the database with safe fallbacks.
 * @Dependencies:
 *  - SystemConfigService (Local)
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
     * @Function: getEarlyWindowMins
     * @Description: Retrieves the allowed early arrival window in minutes.
     *               Defaults to 30 minutes if config is missing or invalid.
     * @returns int - early arrival window in minutes
     */
    public int getEarlyWindowMins() {
        try {
            return Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_MINS").getConfigValue());
        } catch (Exception e) {
            log.warn("Failed to parse RESERVATION_EARLY_MINS, falling back to 30: {}", e.getMessage());
            return 30;
        }
    }

    /**
     * @Function: getRefundLatePercent
     * @Description: Retrieves the refund percentage for late cancellations.
     *               Defaults to 50% (0.5) if config is missing or invalid.
     * @returns BigDecimal - late cancellation refund ratio
     */
    public BigDecimal getRefundLatePercent() {
        try {
            return new BigDecimal(systemConfigService.getConfigByKey("RESERVATION_REFUND_LATE_PERCENT").getConfigValue());
        } catch (Exception e) {
            log.warn("Failed to parse RESERVATION_REFUND_LATE_PERCENT, falling back to 0.5: {}", e.getMessage());
            return new BigDecimal("0.5");
        }
    }

    /**
     * @Function: getRefundEarlyPercent
     * @Description: Retrieves the refund percentage for early cancellations.
     *               Defaults to 100% (1.0) if config is missing or invalid.
     * @returns BigDecimal - early cancellation refund ratio
     */
    public BigDecimal getRefundEarlyPercent() {
        try {
            return new BigDecimal(systemConfigService.getConfigByKey("RESERVATION_REFUND_EARLY_PERCENT").getConfigValue());
        } catch (Exception e) {
            log.warn("Failed to parse RESERVATION_REFUND_EARLY_PERCENT, falling back to 1.0: {}", e.getMessage());
            return BigDecimal.ONE;
        }
    }

    /**
     * @Function: getDefaultDurationMins
     * @Description: Retrieves the default duration for a reservation if not specified.
     *               Defaults to 120 minutes if config is missing or invalid.
     * @returns int - default reservation duration in minutes
     */
    public int getDefaultDurationMins() {
        try {
            return Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_DEFAULT_DURATION_MINS").getConfigValue());
        } catch (Exception e) {
            log.warn("Failed to parse RESERVATION_DEFAULT_DURATION_MINS, falling back to 120: {}", e.getMessage());
            return 120;
        }
    }
}
