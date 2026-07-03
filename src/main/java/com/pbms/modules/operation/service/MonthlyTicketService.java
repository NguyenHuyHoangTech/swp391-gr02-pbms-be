package com.pbms.modules.operation.service;

import com.pbms.modules.operation.domain.MonthlyTicket;
import com.pbms.modules.operation.dto.MonthlyTicketDTO;
import com.pbms.modules.operation.repository.MonthlyTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import com.pbms.modules.operation.repository.VehicleTypeRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyTicketService {

    private final MonthlyTicketRepository monthlyTicketRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final com.pbms.modules.operation.repository.ParkingSessionRepository parkingSessionRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final com.pbms.modules.infrastructure.repository.SlotRepository slotRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional(readOnly = true)
    public List<MonthlyTicketDTO> getAllTickets() {
        List<MonthlyTicket> tickets = monthlyTicketRepository.findAll();
        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();

        return tickets.stream().map(ticket -> {
            String derivedStatus = ticket.getStatus();
            
            if ("ACTIVE".equals(derivedStatus)) {
                if (ticket.getValidUntil().isBefore(now)) {
                    derivedStatus = "EXPIRED";
                } else if (ChronoUnit.DAYS.between(now, ticket.getValidUntil()) <= 7) {
                    derivedStatus = "EXPIRING_SOON";
                }
            }

            boolean hasBeenUsed = parkingSessionRepository.existsByPlateAndTimeInGreaterThanEqual(ticket.getPlate(), ticket.getValidFrom());

            return MonthlyTicketDTO.builder()
                    .id("MP-" + ticket.getId())
                    .user(ticket.getUser() != null ? ticket.getUser().getFullName() : "Guest")
                    .email(ticket.getUser() != null ? ticket.getUser().getEmail() : "")
                    .phone("") // phone doesn't exist on User
                    .plate(ticket.getPlate())
                    .type(ticket.getVehicleType() != null ? ticket.getVehicleType().getTypeName() : "N/A")
                    .vehicleTypeId(ticket.getVehicleType() != null ? ticket.getVehicleType().getId() : null)
                    .status(derivedStatus)
                    .startDate(ticket.getValidFrom().format(FORMATTER))
                    .endDate(ticket.getValidUntil().format(FORMATTER))
                    .hasBeenUsed(hasBeenUsed)
                    .build();
        }).collect(Collectors.toList());
    }

    @Scheduled(cron = "0 0 1 * * ?") // Runs at 1:00 AM every day
    @org.springframework.context.event.EventListener(com.pbms.common.event.TimeFastForwardedEvent.class)
    @Transactional
    public void expireMonthlyTickets() {
        log.info("Running expireMonthlyTickets cronjob...");
        LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        int expiredCount = monthlyTicketRepository.expirePastTickets(now);
        log.info("Expired {} monthly tickets.", expiredCount);
    }

    @Transactional
    public MonthlyTicketDTO createTicket(Map<String, Object> payload) {
        String plate = (String) payload.get("plateNumber");
        Long vehicleTypeId = payload.get("vehicleTypeId") != null ? Long.parseLong(payload.get("vehicleTypeId").toString()) : null;
        int months = payload.get("duration") != null ? Integer.parseInt(payload.get("duration").toString()) : 1;
        
        com.pbms.modules.operation.domain.VehicleType vt = null;
        if (vehicleTypeId != null) {
            vt = vehicleTypeRepository.findById(vehicleTypeId).orElse(null);
        }

        // Get Current User
        com.pbms.modules.identity.domain.User currentUser = null;
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.pbms.modules.identity.domain.User) {
                currentUser = (com.pbms.modules.identity.domain.User) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.warn("Could not get current user context", e);
        }
        
        MonthlyTicket ticket = MonthlyTicket.builder()
                .plate(plate)
                .validFrom(com.pbms.common.utils.TimeProvider.now())
                .validUntil(com.pbms.common.utils.TimeProvider.now().plusMonths(months))
                .status("ACTIVE")
                .autoRenew(false)
                .vehicleType(vt)
                .user(currentUser)
                .build();
                
        monthlyTicketRepository.save(ticket);
        
        try {
            checkMonthlyThreshold();
        } catch (Exception e) {
            log.error("Failed to check monthly threshold", e);
        }
        
        return MonthlyTicketDTO.builder()
                .id("MP-" + ticket.getId())
                .plate(ticket.getPlate())
                .status(ticket.getStatus())
                .startDate(ticket.getValidFrom().format(FORMATTER))
                .endDate(ticket.getValidUntil().format(FORMATTER))
                .build();
    }

    @Transactional
    public MonthlyTicketDTO renewTicket(Long id, int durationMonths) {
        MonthlyTicket ticket = monthlyTicketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Do not take advantage of this and often"));
                
        LocalDateTime newEndDate;
        if (ticket.getValidUntil().isAfter(com.pbms.common.utils.TimeProvider.now())) {
            newEndDate = ticket.getValidUntil().plusMonths(durationMonths);
        } else {
            newEndDate = com.pbms.common.utils.TimeProvider.now().plusMonths(durationMonths);
            ticket.setValidFrom(com.pbms.common.utils.TimeProvider.now());
            ticket.setStatus("ACTIVE");
        }
        ticket.setValidUntil(newEndDate);
        monthlyTicketRepository.save(ticket);
        
        return MonthlyTicketDTO.builder()
                .id("MP-" + ticket.getId())
                .plate(ticket.getPlate())
                .status(ticket.getStatus())
                .startDate(ticket.getValidFrom().format(FORMATTER))
                .endDate(ticket.getValidUntil().format(FORMATTER))
                .build();
    }

    @Transactional
    public MonthlyTicketDTO updateTicketPlate(Long id, String newPlate) {
        MonthlyTicket ticket = monthlyTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Monthly ticket not found"));

        if (newPlate == null || newPlate.isBlank()) {
            throw new IllegalArgumentException("New plate cannot be empty");
        }

        boolean hasBeenUsed = parkingSessionRepository.existsByPlateAndTimeInGreaterThanEqual(ticket.getPlate(), ticket.getValidFrom());
        if (hasBeenUsed) {
            throw new IllegalStateException("This pass has already been used and cannot be modified");
        }

        ticket.setPlate(newPlate);
        monthlyTicketRepository.save(ticket);

        return MonthlyTicketDTO.builder()
                .id("MP-" + ticket.getId())
                .plate(ticket.getPlate())
                .status(ticket.getStatus())
                .startDate(ticket.getValidFrom().format(FORMATTER))
                .endDate(ticket.getValidUntil().format(FORMATTER))
                .build();
    }

    @Transactional
    public MonthlyTicketDTO assignRfidCard(Long ticketId, String rfidCode, com.pbms.modules.infrastructure.repository.RfidCardRepository rfidCardRepository) {
        MonthlyTicket ticket = monthlyTicketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Do not take advantage of this and often"));

        com.pbms.modules.infrastructure.domain.RfidCard card = rfidCardRepository.findByCardCode(rfidCode)
                .orElseGet(() -> {
                    com.pbms.modules.infrastructure.domain.RfidCard newCard = com.pbms.modules.infrastructure.domain.RfidCard.builder()
                            .cardCode(rfidCode)
                            .status("IN_USE")
                            .build();
                    return rfidCardRepository.save(newCard);
                });

        card.setStatus("IN_USE");
        card.setAssignedPlate(ticket.getPlate());
        rfidCardRepository.save(card);

        ticket.setRfidCard(card);
        monthlyTicketRepository.save(ticket);

        return MonthlyTicketDTO.builder()
                .id("MP-" + ticket.getId())
                .plate(ticket.getPlate())
                .status(ticket.getStatus())
                .startDate(ticket.getValidFrom().format(FORMATTER))
                .endDate(ticket.getValidUntil().format(FORMATTER))
                .build();
    }

    private void checkMonthlyThreshold() {
        try {
            int threshold = 90; // Default
            String configVal = systemConfigService.getConfigByKey("MONTHLY_TICKET_ALERT_THRESHOLD").getConfigValue();
            if (configVal != null && !configVal.isBlank()) {
                threshold = Integer.parseInt(configVal);
            }

            long totalMonthlySlots = slotRepository.countByZone_FunctionType("MONTHLY");
            if (totalMonthlySlots == 0) return;

            long totalActiveTickets = monthlyTicketRepository.countByStatus("ACTIVE");

            double currentPercentage = ((double) totalActiveTickets / totalMonthlySlots) * 100;

            if (currentPercentage > threshold) {
                log.warn("Monthly ticket threshold exceeded! Currently at {}% (Active: {}, Slots: {})", 
                        String.format("%.1f", currentPercentage), totalActiveTickets, totalMonthlySlots);
                
                messagingTemplate.convertAndSend("/topic/manager-alerts", 
                        (Object) Map.of(
                            "type", "MONTHLY_ZONE_OVERLOAD",
                            "message", String.format("Warning: The number of registered monthly tickets (%.1f%%) has exceeded the %d%% threshold of total Monthly Zone slots. Please consider expanding the Monthly Zone.", currentPercentage, threshold),
                            "activeTickets", totalActiveTickets,
                            "totalSlots", totalMonthlySlots
                        ));
            }
        } catch (Exception e) {
            log.error("Error in checkMonthlyThreshold", e);
        }
    }
}

