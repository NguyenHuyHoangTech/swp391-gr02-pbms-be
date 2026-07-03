package com.pbms.modules.incident.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class IncidentTicketRequest {
    private Long sessionId;
    private String plate; // For finding session if sessionId is not provided
    private String issueType; // LPR_MISMATCH, LOST_CARD, DAMAGED_CARD, ZONE_VIOLATION
    private String priority; // HIGH, MEDIUM, LOW
    private String description;
    private String correctPlateNumber; // For LPR_MISMATCH
    private BigDecimal fineAmount; // For LOST_CARD
    private Long expectedZoneId; // For ZONE_VIOLATION
    private Long actualZoneId; // For ZONE_VIOLATION
    private String uploadedDocUrl; // For LOST_CARD
}

