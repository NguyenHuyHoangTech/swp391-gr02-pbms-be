// TODO(Member2): Full implementation pending - SPATIAL & ROUTING MASTER
package com.pbms.modules.operation.service;

import com.pbms.modules.operation.dto.ZoneRoutingStatusDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public interface ZoneRoutingService {

    /**
     * Calculates the current occupancy percentage of a zone.
     * @param zoneId - target zone ID
     * @return occupancy percentage (0 to 100+). >= 100 means FULL.
     */
    BigDecimal calculateZoneOccupancy(Long zoneId);

    /**
     * Returns routing status of all zones eligible for a given vehicle type and floor.
     * @param vehicleTypeId - vehicle type to filter zones
     * @param mode          - routing mode: "BOOK" or "WALK_IN"
     * @param floorOrZoneId - floor ID or zone ID to scope the search
     */
    List<ZoneRoutingStatusDTO> getRoutingStatus(Long vehicleTypeId, String mode, Long floorOrZoneId);
}
