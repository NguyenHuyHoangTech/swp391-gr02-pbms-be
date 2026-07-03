package com.pbms.modules.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingRuleDTO {
    
    // Response payload for UI
    private String timeFrameId;
    private String name;
    private String startTime;
    private String endTime;
    private Boolean isDefault;
    private List<RuleItemDTO> rules;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleItemDTO {
        private Long id;
        private Long zoneId;
        private String zoneName;
        private Integer fillThresholdPct;
        private Long suggestedZoneId;
        private String suggestedZoneName;
    }

    // For batch update requests
    @Data
    public static class BatchUpdateRequest {
        private String vehicleTypeName;
        private Long floorId;
        private List<TimeFrameConfig> timeFrames;
    }

    @Data
    public static class TimeFrameConfig {
        private String startTime;
        private String endTime;
        private Boolean isDefault;
        private List<RuleItem> rules;
    }
    
    @Data
    public static class RuleItem {
        private Long zoneId;
        private Integer fillThresholdPct;
    }
}

