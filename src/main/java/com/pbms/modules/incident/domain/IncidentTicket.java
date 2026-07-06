package com.pbms.modules.incident.domain;

import com.pbms.common.domain.BaseEntity;
import com.pbms.modules.identity.domain.User;
import com.pbms.modules.infrastructure.domain.Zone;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "incident_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentTicket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private User staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private com.pbms.modules.operation.domain.ParkingSession session;

    @Column(name = "issue_type", nullable = false, length = 50)
    private String issueType;

    @Column(nullable = false, length = 50)
    private String priority; // HIGH, MEDIUM, LOW

    @Column(nullable = false, columnDefinition = "VARCHAR(MAX)")
    private String description;

    @Column(nullable = false, length = 50)
    private String status; // PENDING, WAITING_CHECKOUT, RESOLVED, REJECTED

    @Column(name = "uploaded_doc_url", columnDefinition = "VARCHAR(MAX)")
    private String uploadedDocUrl;

    @Column(name = "uploaded_card_url", length = 255)
    private String uploadedCardUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expected_zone_id")
    private Zone expectedZone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actual_zone_id")
    private Zone actualZone;

    @Column(name = "resolution_notes", columnDefinition = "VARCHAR(MAX)")
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "fine_amount")
    private java.math.BigDecimal fineAmount;

    @Column(name = "fee_paused_at")
    private LocalDateTime feePausedAt;

    @Column(name = "reported_plate", length = 50)
    private String reportedPlate;
}

