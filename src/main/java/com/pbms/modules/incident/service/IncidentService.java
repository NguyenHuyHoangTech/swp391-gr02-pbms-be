package com.pbms.modules.incident.service;

import com.pbms.modules.incident.domain.IncidentTicket;
import com.pbms.modules.incident.dto.IncidentTicketRequest;
import com.pbms.modules.incident.repository.IncidentTicketRepository;
import com.pbms.modules.operation.domain.ParkingSession;
import com.pbms.modules.infrastructure.domain.RfidCard;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.infrastructure.repository.RfidCardRepository;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pbms.modules.incident.dto.IncidentTicketDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentTicketRepository incidentTicketRepository;
    private final ParkingSessionRepository sessionRepository;
    private final RfidCardRepository rfidCardRepository;
    private final ZoneRepository zoneRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.pbms.modules.finance.service.PricingCalculatorService pricingCalculatorService;
    private final com.pbms.modules.operation.repository.MonthlyTicketRepository monthlyTicketRepository;
    private final com.pbms.modules.identity.repository.UserRepository userRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;

    @Transactional
    public IncidentTicket createIncident(IncidentTicketRequest request, String email) {
        ParkingSession session = null;
        if (request.getSessionId() != null) {
            session = sessionRepository.findById(request.getSessionId())
                    .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        } else if (request.getPlate() != null && !request.getPlate().isBlank()) {
            session = sessionRepository.findByPlateAndStatus(request.getPlate().trim().toUpperCase(), "ACTIVE")
                    .orElse(null);
        }

        com.pbms.modules.identity.domain.User user = null;
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email).orElse(null);
        }

        IncidentTicket ticket = IncidentTicket.builder()
                .session(session)
                .user(user)
                .issueType(request.getIssueType())
                .priority(request.getPriority() != null ? request.getPriority() : "MEDIUM")
                .description(request.getDescription())
                .status("PENDING")
                .fineAmount(request.getFineAmount())
                .uploadedDocUrl(request.getUploadedDocUrl())
                .build();

        if (session != null) {
            if ("LPR_MISMATCH".equals(request.getIssueType()) && request.getCorrectPlateNumber() != null) {
                session.setPlate(request.getCorrectPlateNumber());
                if (session.getRfidCard() != null) {
                    session.getRfidCard().setAssignedPlate(request.getCorrectPlateNumber());
                }
                ticket.setStatus("RESOLVED");
                ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
                ticket.setResolutionNotes("Sign in");
            } 
            else if ("LOST_CARD".equals(request.getIssueType())) {
                if (session.getRfidCard() != null) {
                    session.getRfidCard().setStatus("LOST");
                    rfidCardRepository.save(session.getRfidCard());
                }
                if (request.getFineAmount() != null) {
                    session.setPenaltyFee(request.getFineAmount());
                }
            }
            else if ("DAMAGED_CARD".equals(request.getIssueType())) {
                if (session.getRfidCard() != null) {
                    session.getRfidCard().setStatus("DAMAGED");
                    rfidCardRepository.save(session.getRfidCard());
                }
            }
        }

        if ("ZONE_VIOLATION".equals(request.getIssueType())) {
            if (request.getFineAmount() == null) {
                boolean is2W = false;
                if (session != null && session.getVehicleType() != null && "TWO_WHEEL".equals(session.getVehicleType().getCategory())) {
                    is2W = true;
                }
                String configKey = is2W ? "PENALTY_ZONE_VIOLATION_2W" : "PENALTY_ZONE_VIOLATION_4W";
                BigDecimal defaultFine = is2W ? new BigDecimal("50000") : new BigDecimal("100000");
                try {
                    defaultFine = new BigDecimal(systemConfigService.getConfigByKey(configKey).getConfigValue());
                } catch (Exception e) {
                    log.warn("Could not find {} config, using default", configKey);
                }
                ticket.setFineAmount(defaultFine);
                if (session != null) {
                    BigDecimal currentPenalty = session.getPenaltyFee() != null ? session.getPenaltyFee() : BigDecimal.ZERO;
                    session.setPenaltyFee(currentPenalty.add(defaultFine));
                }
            }
            if (request.getExpectedZoneId() != null) {
                ticket.setExpectedZone(zoneRepository.findById(request.getExpectedZoneId()).orElse(null));
            }
            if (request.getActualZoneId() != null) {
                ticket.setActualZone(zoneRepository.findById(request.getActualZoneId()).orElse(null));
            }
            messagingTemplate.convertAndSend("/topic/alerts", "Warnings for destroying the area:" + request.getDescription());
        }

        if ("SLOT_OCCUPIED".equals(request.getIssueType()) && session != null) {
            boolean isMonthly = monthlyTicketRepository.findByPlateAndStatus(session.getPlate(), "ACTIVE").isPresent();
            if (isMonthly) {
                ticket.setPriority("HIGH");
                ticket.setDescription("[DEPRESENTATION AND CHARGE]" + ticket.getDescription());
            } else {
                ticket.setPriority("MEDIUM");
            }
        }

        return incidentTicketRepository.save(ticket);
    }

    // Cronjob running at 2:00 AM every day
    @Scheduled(cron = "0 0 2 * * ?")
    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    @Transactional
    public void handleOverstayVehicles() {
        log.info("Starting OVERSTAY checking cronjob...");
        int hoursLimit = 72;
        try {
            hoursLimit = Integer.parseInt(systemConfigService.getConfigByKey("OVERSTAY_HOURS_LIMIT").getConfigValue());
        } catch (Exception e) {
            log.warn("OVERSTAY_HOURS_LIMIT config not found, using default 72");
        }
        
        LocalDateTime cutoff = com.pbms.common.utils.TimeProvider.now().minusHours(hoursLimit);
        List<ParkingSession> overstaySessions = sessionRepository.findActiveSessionsOlderThan(cutoff);

        for (ParkingSession session : overstaySessions) {
            IncidentTicket ticket = IncidentTicket.builder()
                    .session(session)
                    .issueType("OVERSTAY")
                    .priority("HIGH")
                    .description(String.format("The vehicle has overstayed for more than %d hours (%s: %s)", 
                            hoursLimit, session.getPlate(), session.getTimeIn().toString()))
                    .status("PENDING")
                    .build();
            incidentTicketRepository.save(ticket);
            log.warn("Created OVERSTAY incident for plate: {}", session.getPlate());
            messagingTemplate.convertAndSend("/topic/alerts", "OVERSTAY incident generated for plate: " + session.getPlate());
        }
    }

    @Transactional(readOnly = true)
    public List<IncidentTicketDTO> getAllIncidents(String email) {
        List<IncidentTicket> tickets;
        if (email != null && !email.isBlank()) {
            tickets = incidentTicketRepository.findByUserEmailOrderByIdDesc(email);
        } else {
            tickets = incidentTicketRepository.findAllByOrderByIdDesc();
        }
        return tickets.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public IncidentTicketDTO moveToOverstay(Long id, String uploadedDocUrl) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        if (!"PENDING".equals(ticket.getStatus()) && !"OVERSTAY".equals(ticket.getIssueType())) {
            throw new IllegalStateException("Ticket must be PENDING and of type OVERSTAY");
        }

        ticket.setStatus("RESOLVED");
        ticket.setResolutionNotes("[OVERSTAY] Vehicle moved to overstay zone");
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        if (uploadedDocUrl != null && !uploadedDocUrl.isBlank()) {
            ticket.setUploadedDocUrl(uploadedDocUrl);
        }
        
        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    @Transactional
    public IncidentTicketDTO resolveIncident(Long id, String resolutionNotes, String uploadedDocUrl, String uploadedPicOutUrl, java.math.BigDecimal totalFee) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        if (!"PENDING".equals(ticket.getStatus()) && !"WAITING_CHECKOUT".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ticket is already resolved or in a different status");
        }

        ticket.setStatus("RESOLVED");
        ticket.setResolutionNotes(resolutionNotes != null ? resolutionNotes : "[GD2] Da thu phi va mo barrier.");
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        if (uploadedDocUrl != null && !uploadedDocUrl.isBlank()) {
            ticket.setUploadedDocUrl(uploadedDocUrl);
        }
        if (totalFee != null) {
            ticket.setFineAmount(totalFee); // fineAmount currently stores totalFee in this flow
        }

        // Update ParkingSession to COMPLETED with fee and picOut
        ParkingSession session = ticket.getSession();
        if (session != null && "LOCKED".equals(session.getStatus())) {
            session.setStatus("COMPLETED");
            if (session.getTimeOut() == null) {
                session.setTimeOut(com.pbms.common.utils.TimeProvider.now());
            }
            if (uploadedPicOutUrl != null && !uploadedPicOutUrl.isBlank()) {
                session.setPicOutPanorama(uploadedPicOutUrl);
            }
            if (totalFee != null) {
                // If the user hasn't paused fee before, we just accept totalFee
                // But normally totalFee = session.getTotalFee() + ticket.fineAmount
                // The frontend sends totalFee as parkingFee + penalty.
                session.setTotalFee(totalFee);
            }
            
            // Update the card status based on incident type
            if (session.getRfidCard() != null) {
                RfidCard card = session.getRfidCard();
                if ("LOST_CARD".equals(ticket.getIssueType())) {
                    card.setStatus("LOST");
                } else if ("DAMAGED_CARD".equals(ticket.getIssueType())) {
                    card.setStatus("DAMAGED");
                } else {
                    card.setStatus("AVAILABLE");
                }
                card.setAssignedPlate(null);
                rfidCardRepository.save(card);
            }
            
            sessionRepository.save(session);
        }
        
        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    @Transactional
    public IncidentTicketDTO cancelIncident(Long id, String reason) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + id + " does not exist"));

        ticket.setStatus("RESOLVED");
        ticket.setResolutionNotes("[HUY] " + (reason != null ? reason : "Khach da tim thay the, huy su co"));
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        ticket.setFineAmount(java.math.BigDecimal.ZERO); // Há»§y sá»± cá»‘ thÃ¬ khÃ´ng thu pháº¡t

        // Restore the session to ACTIVE so the vehicle can exit normally
        ParkingSession session = ticket.getSession();
        if (session != null && "LOCKED".equals(session.getStatus())) {
            session.setStatus("ACTIVE");
            // If we paused the fee earlier, reset it
            if (ticket.getFeePausedAt() != null) {
                session.setTimeOut(null);
                session.setTotalFee(null);
            }
            sessionRepository.save(session);
            log.info("ParkingSession #{} restored to ACTIVE (incident cancelled)", session.getId());
        }

        log.info("Incident #{} CANCELLED. Reason: {}", id, reason);
        return mapToDTO(incidentTicketRepository.save(ticket));
    }
    
    @Transactional
    public IncidentTicketDTO pauseFee(Long id) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + id + " does not exist"));

        if (!"WAITING_CHECKOUT".equals(ticket.getStatus())) {
            throw new IllegalStateException("Can only calculate fee in phase 2");
        }
        
        if (ticket.getFeePausedAt() != null) {
            throw new IllegalStateException("Parking fee has already been finalized");
        }

        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        ticket.setFeePausedAt(now);

        ParkingSession session = ticket.getSession();
        if (session != null) {
            session.setTimeOut(now);
            if (session.getVehicleType() != null) {
                BigDecimal parkingFee = pricingCalculatorService.calculateTotalFee(session.getVehicleType().getId(), session.getTimeIn(), now);
                session.setTotalFee(parkingFee);
            } else {
                session.setTotalFee(BigDecimal.ZERO);
            }
            sessionRepository.save(session);
        }

        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    /**
     * PUT /incidents/{id}/process-phase1
     * Staff duyet giai doan 1: Khoa phien do, chuyen sang cho check-out thu cong (GD2)
     */
    @Transactional
    public IncidentTicketDTO processPhase1(Long id) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + id + " does not exist"));

        if (!"PENDING".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ticket is already resolved or in an invalid state");
        }

        ticket.setStatus("WAITING_CHECKOUT");

        boolean isCardIncident = "LOST_CARD".equals(ticket.getIssueType()) || "DAMAGED_CARD".equals(ticket.getIssueType());

        if (isCardIncident) {
            ticket.setResolutionNotes("[GD1] Nhan vien da xac minh, dang cho thu tien va mo barrier.");
            // LOCK the parking session so gate knows to block this vehicle
            ParkingSession session = ticket.getSession();
            if (session != null && "ACTIVE".equals(session.getStatus())) {
                session.setStatus("LOCKED");
                sessionRepository.save(session);
                log.info("ParkingSession #{} LOCKED due to Incident #{}", session.getId(), id);
            }
            messagingTemplate.convertAndSend("/topic/alerts",
                    "[GD1 OK] Phien #" + id + " da khoa. Cho thu tien de mo barrier.");
        } else {
            ticket.setResolutionNotes("[GD1] Nhan vien da xac minh thong tin va dang xu ly.");
            messagingTemplate.convertAndSend("/topic/alerts",
                    "[GD1 OK] Ticket #" + id + " dang duoc nhan vien xu ly ho tro.");
        }

        log.info("Incident #{} moved to WAITING_CHECKOUT (Phase 1 approved)", id);
        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    @Transactional
    public IncidentTicketDTO resolveNonCardIncident(Long id, String resolutionNotes) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + id + " does not exist"));

        if (!"WAITING_CHECKOUT".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ticket khong o trang thai hop le de giai quyet");
        }

        ticket.setStatus("RESOLVED");
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());
        ticket.setResolutionNotes(resolutionNotes != null ? resolutionNotes : "Da giai quyet xong su co.");

        log.info("Incident #{} RESOLVED (Non-card flow)", id);
        messagingTemplate.convertAndSend("/topic/alerts", "[GD2 OK] Ticket #" + id + " da giai quyet xong.");

        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    /**
     * PUT /incidents/{id}/reject?reason=...
     * Tu choi xu ly ticket (ly do khong hop le, giay to sai)
     */
    @Transactional
    public IncidentTicketDTO rejectIncident(Long id, String reason) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket #" + id + " does not exist"));

        if ("RESOLVED".equals(ticket.getStatus()) || "REJECTED".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ticket da o trang thai cuoi, khong the tu choi");
        }

        ticket.setStatus("REJECTED");
        ticket.setResolutionNotes("[TU CHOI] " + reason);
        ticket.setResolvedAt(com.pbms.common.utils.TimeProvider.now());

        log.info("Incident #{} REJECTED. Reason: {}", id, reason);
        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    /**
     * POST /incidents/lost-card
     * Staff xu ly mat the: Danh dau the LOST + tinh phat
     */
    @Transactional
    public IncidentTicketDTO createLostCardIncident(String plate, BigDecimal fee, String description, String uploadedDocUrl, String email) {
        ParkingSession session = sessionRepository.findByPlateAndStatus(plate.trim().toUpperCase(), "ACTIVE")
                .orElseThrow(() -> new IllegalArgumentException(
                        "Khong tim thay phien do xe ACTIVE cho bien so: " + plate));

        // Danh dau the bi mat
        if (session.getRfidCard() != null) {
            RfidCard card = session.getRfidCard();
            card.setStatus("LOST");
            rfidCardRepository.save(card);
        }

        // Cong phi phat vao phien
        BigDecimal defaultFine = new BigDecimal("200000");
        try {
            defaultFine = new BigDecimal(systemConfigService.getConfigByKey("PENALTY_LOST_CARD").getConfigValue());
        } catch (Exception e) {
            log.warn("Could not find PENALTY_LOST_CARD config, using default 200000");
        }
        BigDecimal fineAmount = fee != null ? fee : defaultFine;
        session.setPenaltyFee(fineAmount);
        sessionRepository.save(session);

        com.pbms.modules.identity.domain.User user = null;
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email).orElse(null);
        }

        IncidentTicket ticket = new IncidentTicket();
        ticket.setSession(session);
        ticket.setUser(user);
        ticket.setIssueType("LOST_CARD");
        ticket.setPriority("HIGH");
        ticket.setDescription(description != null ? description : "Bao mat the, tien phat: " + fineAmount);
        ticket.setStatus("PENDING");
        ticket.setFineAmount(fineAmount);
        ticket.setUploadedDocUrl(uploadedDocUrl);

        log.info("LOST_CARD incident created for plate: {}, fine: {}", plate, fineAmount);
        messagingTemplate.convertAndSend("/topic/alerts",
                "[MAT THE] Bien so " + plate + " da bao mat the. Phi phat: " + fineAmount.toPlainString() + " VND");

        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    /**
     * POST /incidents/adjust-fee
     * Manager can thiep dieu chinh phi cho phien do xe
     */
    @Transactional
    public IncidentTicketDTO adjustFeeIncident(String plate, BigDecimal liveFee, String reason) {
        ParkingSession session = sessionRepository.findByPlateAndStatus(plate.trim().toUpperCase(), "ACTIVE")
                .orElseThrow(() -> new IllegalArgumentException(
                        "Khong tim thay phien do xe ACTIVE cho bien so: " + plate));

        BigDecimal oldFee = session.getTotalFee();
        session.setTotalFee(liveFee);
        sessionRepository.save(session);

        String desc = String.format(
                "Manager dieu chinh phi. Bien so: %s | Phi cu: %s | Phi moi: %s VND | Ly do: %s",
                plate,
                oldFee != null ? oldFee.toPlainString() : "chua tinh",
                liveFee.toPlainString(),
                reason != null ? reason : "Khong co");

        IncidentTicket ticket = IncidentTicket.builder()
                .session(session)
                .issueType("FEE_ADJUSTMENT")
                .priority("MEDIUM")
                .description(desc)
                .status("RESOLVED")
                .fineAmount(liveFee)
                .resolvedAt(com.pbms.common.utils.TimeProvider.now())
                .resolutionNotes("[TU DONG] Gianh quyen can thiep phi boi Manager")
                .build();

        log.info("FEE_ADJUSTMENT incident: plate={}, newFee={}", plate, liveFee);
        return mapToDTO(incidentTicketRepository.save(ticket));
    }

    private IncidentTicketDTO mapToDTO(IncidentTicket ticket) {
        int phase = 3;
        if ("PENDING".equals(ticket.getStatus())) phase = 1;
        else if ("WAITING_CHECKOUT".equals(ticket.getStatus())) phase = 2;

        ParkingSession session = ticket.getSession();
        String sessionTimeIn = null;
        String sessionPicIn = null;
        java.math.BigDecimal sessionParkingFee = null;
        String sessionVehicleType = null;
        Long sessionId = null;

        String sessionPicOut = null;
        String sessionSuggestedZone = null;

        if (session != null) {
            sessionId = session.getId();
            sessionTimeIn = session.getTimeIn() != null ? session.getTimeIn().toString() : null;
            sessionPicIn = session.getPicInPanorama();
            sessionPicOut = session.getPicOutPanorama();
            sessionParkingFee = session.getTotalFee();
            sessionVehicleType = session.getVehicleType() != null ? session.getVehicleType().getTypeName() : null;
            sessionSuggestedZone = session.getSuggestedZoneName();
        }

        return IncidentTicketDTO.builder()
                .id(ticket.getId())
                .plateNumber(session != null ? session.getPlate() : null)
                .issueType(ticket.getIssueType())
                .priority(ticket.getPriority())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .fineAmount(ticket.getFineAmount())
                .resolutionNotes(ticket.getResolutionNotes())
                .resolvedAt(ticket.getResolvedAt())
                .createdAt(ticket.getCreatedAt())
                .uploadedDocUrl(ticket.getUploadedDocUrl())
                .expectedZoneName(ticket.getExpectedZone() != null ? ticket.getExpectedZone().getZoneName() : null)
                .actualZoneName(ticket.getActualZone() != null ? ticket.getActualZone().getZoneName() : null)
                .type(ticket.getIssueType())
                .phase(phase)
                .plate(session != null ? session.getPlate() : null)
                .time(ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : "")
                .sessionId(sessionId)
                .sessionTimeIn(sessionTimeIn)
                .sessionPicInPanorama(sessionPicIn)
                .sessionPicOutPanorama(sessionPicOut)
                .sessionParkingFee(sessionParkingFee)
                .sessionVehicleType(sessionVehicleType)
                .sessionSuggestedZone(sessionSuggestedZone)
                .feePausedAt(ticket.getFeePausedAt())
                .baseFee(sessionParkingFee)
                .build();
    }
    @Transactional(readOnly = true)
    public boolean isPlateActive(String plate) {
        return sessionRepository.findByPlateAndStatus(plate, "ACTIVE").isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isPlateAndRfidActive(String plate, String rfid) {
        return sessionRepository.findByPlateAndStatus(plate.trim().toUpperCase(), "ACTIVE")
                .filter(session -> session.getRfidCard() != null && session.getRfidCard().getCardCode().equals(rfid.trim()))
                .isPresent();
    }

    @Transactional
    public void resolveFeeDispute(Long id, java.math.BigDecimal discountAmount, String resolutionNotes) {
        IncidentTicket ticket = incidentTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        
        if (!"WAITING_CHECKOUT".equals(ticket.getStatus())) {
            throw new IllegalStateException("Ticket is not in phase 2");
        }

        if (!"FEE_DISPUTE".equals(ticket.getIssueType())) {
            throw new IllegalStateException("This endpoint is only for FEE_DISPUTE");
        }

        ParkingSession session = ticket.getSession();
        if (session != null) {
            session.setDiscount(discountAmount);
            session.setDiscountValidUntil(com.pbms.common.utils.TimeProvider.now().plusMinutes(15));
            sessionRepository.save(session);
        }

        ticket.setStatus("RESOLVED");
        ticket.setResolutionNotes(resolutionNotes);
        incidentTicketRepository.save(ticket);
    }
}

