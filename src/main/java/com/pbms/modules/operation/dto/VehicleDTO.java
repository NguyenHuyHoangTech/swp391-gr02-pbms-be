package com.pbms.modules.operation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDTO {
    private Long id;
    private String plateNumber;
    private String color;
    private String brand;
    private String vehicleTypeName;
    private Long vehicleTypeId;
    private String ownerName;
    private Long ownerId;
    private String status;
    private Boolean isBlacklisted;
    private String blacklistReason;
    private String blacklistEvidenceUrl;
}

