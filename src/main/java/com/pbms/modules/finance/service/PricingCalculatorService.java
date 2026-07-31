// TODO(Member3): Full implementation pending - FINANCE & DATA ANALYTICS
package com.pbms.modules.finance.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public interface PricingCalculatorService {

    /**
     * Calculates the total parking fee for a given vehicle type and time range.
     * @param vehicleTypeId   - ID of the vehicle type (affects pricing tier)
     * @param entryTime       - expected entry datetime
     * @param exitTime        - expected exit datetime
     * @return total fee in VND as BigDecimal
     */
    BigDecimal calculateTotalFee(Long vehicleTypeId, LocalDateTime entryTime, LocalDateTime exitTime);
}
