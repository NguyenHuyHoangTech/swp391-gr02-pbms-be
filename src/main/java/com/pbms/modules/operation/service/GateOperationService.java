package com.pbms.modules.operation.service;

import com.pbms.modules.finance.domain.Transaction;
import com.pbms.modules.finance.repository.TransactionRepository;
import com.pbms.modules.finance.service.PricingCalculatorService;
import com.pbms.modules.infrastructure.domain.Gate;
import com.pbms.modules.infrastructure.domain.RfidCard;
import com.pbms.modules.infrastructure.repository.GateRepository;
import com.pbms.modules.infrastructure.repository.RfidCardRepository;
import com.pbms.modules.operation.domain.*;
import com.pbms.modules.operation.dto.CheckInRequestDTO;
import com.pbms.modules.operation.dto.CheckOutRequestDTO;
import com.pbms.modules.operation.dto.GateResponseDTO;
import com.pbms.modules.operation.dto.ScanEventDTO;
import com.pbms.modules.operation.repository.MonthlyTicketRepository;
import com.pbms.modules.operation.repository.ParkingSessionRepository;
import com.pbms.modules.operation.repository.ReservationRepository;
import com.pbms.modules.operation.repository.VehicleTypeRepository;
import com.pbms.modules.operation.repository.VehicleRepository;
import com.pbms.modules.infrastructure.domain.Zone;
import com.pbms.modules.infrastructure.repository.ZoneRepository;
import com.pbms.modules.infrastructure.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GateOperationService {

    private final ParkingSessionRepository sessionRepository;
    private final VehicleRepository vehicleRepository;
    private final ZoneRoutingService zoneRoutingService;
    private final RfidCardRepository rfidCardRepository;
    private final GateRepository gateRepository;
    private final MonthlyTicketRepository monthlyTicketRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final PricingCalculatorService pricingCalculatorService;
    private final ReservationRepository reservationRepository;
    private final ZoneRepository zoneRepository;
    private final SlotRepository slotRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionRepository transactionRepository;
    private final com.pbms.modules.system.service.SystemConfigService systemConfigService;
    private List<Reservation> getValidPendingReservations(String plate) {
        List<Reservation> pending = reservationRepository.findByVehicle_PlateNumberAndStatus(plate, "PENDING");
        List<Reservation> valid = new java.util.ArrayList<>();
        java.time.LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        int windowMinutes = 30;
        try {
            windowMinutes = Integer.parseInt(systemConfigService.getConfigByKey("RESERVATION_EARLY_ARRIVAL_WINDOW_MINUTES").getConfigValue());
        } catch (Exception e) {
            // ignore
        }
        for (Reservation res : pending) {
            java.time.LocalDateTime expireTime = res.getExpectedEntryTime().plusMinutes(res.getExpectedDurationMinutes());
            java.time.LocalDateTime earlyWindow = res.getExpectedEntryTime().minusMinutes(windowMinutes);
            if (now.isAfter(expireTime)) {
                res.setStatus("COMPLETED_UNUSED");
                reservationRepository.save(res);
            } else if (now.isBefore(earlyWindow)) {
                // Too early to use this reservation, ignored so they get treated as walk-in.
            } else {
                valid.add(res);
            }
        }
        return valid;
    }

    private String determineCustomerType(String plate, String rfid) {
        if (plate != null && !plate.trim().isEmpty()) {
            Optional<MonthlyTicket> mt = monthlyTicketRepository.findByPlateAndStatus(plate, "ACTIVE");
            if (mt.isPresent()) return "MONTHLY";

            List<Reservation> activeReservations = reservationRepository.findByVehicle_PlateNumberAndStatus(plate, "ACTIVE");
            if (!activeReservations.isEmpty()) return "PREBOOKED";

            List<Reservation> pendingReservations = getValidPendingReservations(plate);
            if (!pendingReservations.isEmpty()) return "PREBOOKED";
        }
        if (rfid != null && !rfid.trim().isEmpty()) {
             Optional<MonthlyTicket> mt = monthlyTicketRepository.findByRfidCard_CardCodeAndStatus(rfid, "ACTIVE");
             if (mt.isPresent()) return "MONTHLY";
        }
        return "WALK-IN";
    }

    public GateResponseDTO triggerScanCheckIn(CheckInRequestDTO request) {
        Gate gate = gateRepository.findById(request.getGateId())
                .orElseThrow(() -> new IllegalArgumentException("Gate not found"));

        VehicleType type = null;
        if (request.getVehicleTypeId() != null) {
            type = vehicleTypeRepository.findById(request.getVehicleTypeId()).orElse(null);
        }
        if (type == null && request.getVehicleType() != null) {
            type = vehicleTypeRepository.findByTypeName(request.getVehicleType()).orElse(null);
        }

        Zone suggestedZone = null;
        String customerType = determineCustomerType(request.getPlateNumber(), request.getRfid());
        if (type != null) {
            List<Reservation> reservations = getValidPendingReservations(request.getPlateNumber());
            if (!reservations.isEmpty()) {
                suggestedZone = reservations.get(0).getZone();
            } else {
                suggestedZone = zoneRoutingService.suggestZone(type, customerType);
            }
        }

        if (suggestedZone != null) {
            request.setSuggestedZoneName(suggestedZone.getZoneName());
        }

        ScanEventDTO event = ScanEventDTO.builder()
                .gateId(gate.getId())
                .actionType("IN")
                .plateNumber(request.getPlateNumber())
                .vehicleType(request.getVehicleType())
                .rfid(request.getRfid())
                .imageBase64(request.getImageBase64())
                .lprImageBase64(request.getLprImageBase64())
                .suggestedZoneId(suggestedZone != null ? suggestedZone.getId() : null)
                .suggestedZoneName(suggestedZone != null ? suggestedZone.getZoneName() : null)
                .customerType(customerType)
                .build();

        // Notify Staff UI immediately that a scan occurred
        messagingTemplate.convertAndSend("/topic/gates/" + gate.getId() + "/scans", event);

        return GateResponseDTO.builder()
                .status("SUCCESS")
                .message("Scan triggered. Waiting for staff approval.")
                .suggestedZoneId(suggestedZone != null ? suggestedZone.getId() : null)
                .suggestedZoneName(suggestedZone != null ? suggestedZone.getZoneName() : null)
                .build();
    }

    @Transactional
    public GateResponseDTO triggerScanCheckOut(CheckOutRequestDTO request) {
        Gate gate = gateRepository.findById(request.getGateId())
                .orElseThrow(() -> new IllegalArgumentException("Gate not found"));

        ScanEventDTO event = ScanEventDTO.builder()
                .gateId(gate.getId())
                .actionType("OUT")
                .plateNumber(request.getPlateNumber())
                .vehicleType(request.getVehicleType())
                .rfid(request.getRfid())
                .imageBase64(request.getImageBase64())
                .lprImageBase64(request.getLprImageBase64())
                .customerType(determineCustomerType(request.getPlateNumber(), request.getRfid()))
                .build();

        // Notify Staff UI immediately that a scan occurred (with ONLY the IoT data)
        messagingTemplate.convertAndSend("/topic/gates/" + gate.getId() + "/scans", event);

        return GateResponseDTO.builder()
                .status("SUCCESS")
                .message("Scan triggered. Waiting for staff approval.")
                .build();
    }

    @Transactional(readOnly = true)
    public com.pbms.modules.operation.dto.CheckOutSessionInfoDTO getCheckOutSessionInfo(String rfid, String plate) {
        ParkingSession session = null;
        if (rfid != null && !rfid.isEmpty()) {
            session = sessionRepository.findByRfidCard_CardCodeAndStatus(rfid, "ACTIVE").orElse(null);
            if (session == null) {
                session = sessionRepository.findByRfidCard_CardCodeAndStatus(rfid, "LOCKED").orElse(null);
            }
        } else if (plate != null && !plate.isEmpty()) {
            java.util.List<ParkingSession> list = sessionRepository.findByPlateOrderByTimeInDesc(plate);
            for (ParkingSession s : list) {
                if ("ACTIVE".equals(s.getStatus()) || "LOCKED".equals(s.getStatus())) {
                    session = s;
                    break;
                }
            }
        }

        if (session == null) {
            throw new IllegalArgumentException("No active parking session found for the provided vehicle");
        }

        com.pbms.modules.operation.dto.CheckOutSessionInfoDTO info = new com.pbms.modules.operation.dto.CheckOutSessionInfoDTO();
        info.setPlateNumberIn(session.getPlate());
        info.setRfid(session.getRfidCard() != null ? session.getRfidCard().getCardCode() : "N/A");
        info.setVehicleType(session.getVehicleType() != null ? session.getVehicleType().getTypeName() : "UNKNOWN");
        String rfidCode = session.getRfidCard() != null ? session.getRfidCard().getCardCode() : null;
        String customerType = determineCustomerType(session.getPlate(), rfidCode);
        info.setCustomerType(customerType);
        info.setPicInPanorama(session.getPicInPanorama());
        info.setPicInFace(session.getPicInFace());
        info.setTimeIn(session.getTimeIn());
        info.setStatus(session.getStatus());

        if (session.getSlot() != null && session.getSlot().getZone() != null) {
            info.setSuggestedZoneName(session.getSlot().getZone().getZoneName());
        } else if (session.getSuggestedZoneName() != null && !session.getSuggestedZoneName().isEmpty()) {
            info.setSuggestedZoneName(session.getSuggestedZoneName());
        } else if (session.getReservation() != null && session.getReservation().getZone() != null) {
            info.setSuggestedZoneName(session.getReservation().getZone().getZoneName());
        } else {
            info.setSuggestedZoneName("N/A");
        }

        java.time.LocalDateTime now = com.pbms.common.utils.TimeProvider.now();
        long duration = java.time.Duration.between(session.getTimeIn(), now).toMinutes();
        info.setDurationMinutes(duration);
        info.setTimeOut(now);

        boolean isExemptZone = false;
        if (session.getSlot() != null && session.getSlot().getZone() != null) {
            String fType = session.getSlot().getZone().getFunctionType();
            if ("IMPOUNDED".equalsIgnoreCase(fType) || "MONTHLY".equalsIgnoreCase(fType)) {
                isExemptZone = true;
            }
        }

        if ("MONTHLY".equals(customerType) || isExemptZone) {
            info.setExpectedFee(java.math.BigDecimal.ZERO);
            info.setOvertimeMinutes(0L);
        } else if (session.getReservation() != null) {
            java.time.LocalDateTime bookedIn = session.getReservation().getExpectedEntryTime();
            java.time.LocalDateTime bookedOut = bookedIn.plusMinutes(session.getReservation().getExpectedDurationMinutes());
            info.setBookedTimeIn(bookedIn);
            info.setBookedTimeOut(bookedOut);
            
            if (now.isAfter(bookedOut)) {
                long overtime = java.time.Duration.between(bookedOut, now).toMinutes();
                info.setOvertimeMinutes(overtime);
                if (session.getVehicleType() != null) {
                    try {
                        java.math.BigDecimal fee = pricingCalculatorService.calculateTotalFee(session.getVehicleType().getId(), bookedOut, now);
                        info.setExpectedFee(fee);
                    } catch (Exception e) {
                        info.setExpectedFee(java.math.BigDecimal.ZERO);
                    }
                } else {
                    info.setExpectedFee(java.math.BigDecimal.ZERO);
                }
            } else {
                info.setOvertimeMinutes(0L);
                info.setExpectedFee(java.math.BigDecimal.ZERO);
            }
        } else if (session.getVehicleType() != null) {
            try {
                java.math.BigDecimal fee = pricingCalculatorService.calculateTotalFee(session.getVehicleType().getId(), session.getTimeIn(), now);
                log.info("CALCULATED FEE: " + fee + " for duration: " + duration);
                info.setExpectedFee(fee);
            } catch (Exception e) {
                log.error("Error calculating fee for session " + session.getId(), e);
                info.setExpectedFee(java.math.BigDecimal.ZERO);
            }
        } else {
            info.setExpectedFee(java.math.BigDecimal.ZERO);
        }

        if (session.getDiscount() != null && session.getDiscountValidUntil() != null) {
            if (now.isBefore(session.getDiscountValidUntil())) {
                java.math.BigDecimal discountedFee = info.getExpectedFee().subtract(session.getDiscount());
                if (discountedFee.compareTo(java.math.BigDecimal.ZERO) < 0) {
                    discountedFee = java.math.BigDecimal.ZERO;
                }
                info.setDiscountFee(session.getDiscount());
                info.setExpectedFee(discountedFee);
            }
        }

        return info;
    }

    @Transactional
    public GateResponseDTO processCheckIn(CheckInRequestDTO request) {
        Gate gate = gateRepository.findById(request.getGateId())
                .orElseThrow(() -> new IllegalArgumentException("Gate not found"));

        VehicleType type = null;
        if (request.getVehicleTypeId() != null) {
            type = vehicleTypeRepository.findById(request.getVehicleTypeId()).orElse(null);
        }
        if (type == null && request.getVehicleType() != null) {
            type = vehicleTypeRepository.findByTypeName(request.getVehicleType()).orElse(null);
        }

        Zone suggestedZone = null;
        String customerType = determineCustomerType(request.getPlateNumber(), request.getRfid());
        List<Reservation> reservations = java.util.Collections.emptyList();

        if (type != null) {
            reservations = getValidPendingReservations(request.getPlateNumber());
            if (!reservations.isEmpty()) {
                suggestedZone = reservations.get(0).getZone();
            } else {
                suggestedZone = zoneRoutingService.suggestZone(type, customerType);
            }
        }

        if (suggestedZone != null) {
            request.setSuggestedZoneName(suggestedZone.getZoneName());
        }

        // Notify Staff UI immediately that a scan occurred
        messagingTemplate.convertAndSend("/topic/gates/" + gate.getId() + "/scans", request);

        if (!"IN".equals(gate.getGateType()) && !"ENTRY".equals(gate.getGateType()) && !"IN_OUT".equals(gate.getGateType())) {
            return GateResponseDTO.builder()
                    .status("ERROR")
                    .message("Invalid gate type for check-in")
                    .build();
        }

        if (type == null) {
            return GateResponseDTO.builder()
                    .status("ERROR")
                    .message("Invalid vehicle type")
                    .build();
        }

        if ("INACTIVE".equals(type.getStatus())) {
            return GateResponseDTO.builder()
                    .status("ERROR")
                    .message("Vehicle type is blocked/inactive")
                    .build();
        }

        RfidCard card = rfidCardRepository.findByCardCode(request.getRfid()).orElse(null);
        if (card == null) {
            return GateResponseDTO.builder()
                    .status("ERROR")
                    .message("Invalid or missing RFID card")
                    .build();
        }

        if (!"AVAILABLE".equals(card.getStatus())) {
            return GateResponseDTO.builder()
                    .status("ERROR")
                    .message("Card is not available (Status: " + card.getStatus() + ")")
                    .build();
        }

        if (type == null) {
            return GateResponseDTO.builder()
                    .status("ERROR")
                    .message("Invalid vehicle type")
                    .build();
        }

        String plate = request.getPlateNumber();
        if (plate == null || plate.isBlank()) {
            return GateResponseDTO.builder()
                    .status("ERROR")
                    .message("Plate number is required")
                    .build();
        }

        // Check Blacklist
        vehicleRepository.findByPlateNumber(request.getPlateNumber()).ifPresent(v -> {
            if (Boolean.TRUE.equals(v.getIsBlacklisted())) {
                throw new IllegalStateException("Vehicle is blacklisted: " + v.getBlacklistReason());
            }
        });

        Reservation activeRes = null;
        if (!reservations.isEmpty()) {
            activeRes = reservations.get(0);
        }

        // Create Session
        ParkingSession session = ParkingSession.builder()
                .gateIn(gate)
                .plate(request.getPlateNumber())
                .vehicleType(type)
                .rfidCard(card)
                .timeIn(com.pbms.common.utils.TimeProvider.now())
                .picInPanorama(request.getImageBase64())
                .picInFace(request.getLprImageBase64())
                .reservation(activeRes)
                .suggestedZoneName(suggestedZone != null ? suggestedZone.getZoneName() : null)
                .status("ACTIVE")
                .build();

        // Mark card as IN_USE
        card.setStatus("IN_USE");
        card.setAssignedPlate(request.getPlateNumber());
        rfidCardRepository.save(card);

        session = sessionRepository.save(session);

        // Use the already computed suggestedZone
        if (activeRes != null) {
            activeRes.setStatus("ACTIVE");
            reservationRepository.save(activeRes);
        }

        GateResponseDTO response = GateResponseDTO.builder()
                .sessionId(session.getId())
                .plateNumber(session.getPlate())
                .status("SUCCESS")
                .message("Check-in successful")
                .suggestedZoneId(suggestedZone != null ? suggestedZone.getId() : null)
                .suggestedZoneName(suggestedZone != null ? suggestedZone.getZoneName() : null)
                .build();

        return response;
    }

    @Transactional
    public GateResponseDTO processCheckOut(CheckOutRequestDTO request) {
        Gate gate = gateRepository.findById(request.getGateId())
                .orElseThrow(() -> new IllegalArgumentException("Gate not found"));

        // Notify Staff UI immediately that a scan occurred
        messagingTemplate.convertAndSend("/topic/gates/" + gate.getId() + "/scans", request);

        if (!"OUT".equals(gate.getGateType()) && !"EXIT".equals(gate.getGateType()) && !"IN_OUT".equals(gate.getGateType())) {
            return GateResponseDTO.builder()
                    .status("ERROR")
                    .message("Invalid gate type for check-out")
                    .build();
        }

        ParkingSession session = sessionRepository.findByRfidCard_CardCodeAndStatus(request.getRfid(), "ACTIVE")
                .orElseThrow(() -> new IllegalArgumentException("No active session found for this card"));

        if (!session.getPlate().equals(request.getPlateNumber())) {
            return GateResponseDTO.builder()
                    .sessionId(session.getId())
                    .plateNumber(request.getPlateNumber())
                    .status("WARNING")
                    .message("Plate number mismatch! Expected: " + session.getPlate() + ", Actual: "
                            + request.getPlateNumber())
                    .build();
        }

        session.setGateOut(gate);
        session.setTimeOut(com.pbms.common.utils.TimeProvider.now());
        session.setPlateOut(request.getPlateNumber());
        session.setPicOutPanorama(request.getImageBase64());
        session.setPicOutFace(request.getLprImageBase64());

        // Check Monthly Ticket
        boolean isMonthlyCovered = false;
        MonthlyTicket monthlyTicket = monthlyTicketRepository.findByPlateAndStatus(session.getPlate(), "ACTIVE")
                .orElse(null);
        if (monthlyTicket != null && monthlyTicket.getValidUntil().isAfter(com.pbms.common.utils.TimeProvider.now())) {
            if (monthlyTicket.getVehicleType().getId().equals(session.getVehicleType().getId())) {
                isMonthlyCovered = true;
            }
        }

        BigDecimal fee = BigDecimal.ZERO;
        if (!isMonthlyCovered) {
            if (request.getTotalFee() != null) {
                fee = request.getTotalFee();
            } else if (session.getReservation() != null) {
                java.time.LocalDateTime bookedIn = session.getReservation().getExpectedEntryTime();
                java.time.LocalDateTime bookedOut = bookedIn.plusMinutes(session.getReservation().getExpectedDurationMinutes());
                if (session.getTimeOut().isAfter(bookedOut)) {
                    fee = pricingCalculatorService.calculateTotalFee(session.getVehicleType().getId(), bookedOut, session.getTimeOut());
                }
            } else {
                fee = pricingCalculatorService.calculateTotalFee(session.getVehicleType().getId(), session.getTimeIn(),
                        session.getTimeOut());
            }
        }

        session.setTotalFee(fee);
        session.setStatus("COMPLETED");

        RfidCard card = session.getRfidCard();
        card.setStatus("AVAILABLE");
        card.setAssignedPlate(null);
        rfidCardRepository.save(card);

        sessionRepository.save(session);

        // Record Transaction for revenue tracking
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            String payMethod = request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase() : "CASH";
            Transaction transaction = Transaction.builder()
                    .parkingSession(session)
                    .amount(fee)
                    .paymentMethod(payMethod)
                    .status("SUCCESS")
                    .transactionReference("TXN-" + session.getId() + "-" + System.currentTimeMillis())
                    .build();
            transactionRepository.save(transaction);
            log.info("Transaction recorded: {} {} via {}", fee, "VND", payMethod);
        }

        // Complete Reservation if exists
        List<Reservation> activeRes = reservationRepository.findByVehicle_PlateNumberAndStatus(request.getPlateNumber(),
                "ACTIVE");
        if (!activeRes.isEmpty()) {
            Reservation res = activeRes.get(0);
            res.setStatus("COMPLETED");
            reservationRepository.save(res);
        }

        GateResponseDTO response = GateResponseDTO.builder()
                .sessionId(session.getId())
                .plateNumber(session.getPlateOut())
                .status("SUCCESS")
                .message("Check-out successful")
                .checkoutFee(fee)
                .build();

        return response;
    }
}

